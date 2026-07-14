package com.senamed.backend.billing;

import com.senamed.backend.billing.dto.CreatePreapprovalRequest;
import com.senamed.backend.billing.dto.PreapprovalCheckoutResponse;
import com.senamed.backend.billing.dto.PreapprovalResponse;
import com.senamed.backend.billing.mercadopago.CreatePreapprovalCommand;
import com.senamed.backend.billing.mercadopago.MercadoPagoClient;
import com.senamed.backend.billing.mercadopago.PaymentResult;
import com.senamed.backend.billing.mercadopago.PreapprovalResult;
import com.senamed.backend.billing.mercadopago.PreapprovalStatusResult;
import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.clinic.ClinicStatus;
import com.senamed.backend.common.InvalidRequestException;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

/**
 * Backs the recurring "assinatura recorrente" flow (KAN-76): a Mercado Pago Preapproval that
 * auto-charges the clinic every {@code periodMonths} without a manual checkout each cycle. Coexists
 * with {@link SubscriptionService}'s one-time "checkout avulso" flow - a clinic uses one or the
 * other. Real activation only ever happens via {@code MercadoPagoWebhookController}, never here -
 * this service only creates the PENDING preapproval row and the hosted authorization URL to
 * redirect the clinic admin to.
 */
@Service
public class PreapprovalService {

    private static final Logger log = LoggerFactory.getLogger(PreapprovalService.class);

    private static final Set<Integer> ALLOWED_PERIOD_MONTHS = Set.of(1, 3, 12);

    private final ClinicRepository clinicRepository;
    private final PlanRepository planRepository;
    private final PreapprovalRepository preapprovalRepository;
    private final PreapprovalChargeRepository preapprovalChargeRepository;
    private final MercadoPagoClient mercadoPagoClient;
    private final String backendBaseUrl;
    private final String frontendBaseUrl;

    public PreapprovalService(
            ClinicRepository clinicRepository,
            PlanRepository planRepository,
            PreapprovalRepository preapprovalRepository,
            PreapprovalChargeRepository preapprovalChargeRepository,
            MercadoPagoClient mercadoPagoClient,
            @Value("${senamed.backend.base-url}") String backendBaseUrl,
            @Value("${senamed.frontend.base-url}") String frontendBaseUrl) {
        this.clinicRepository = clinicRepository;
        this.planRepository = planRepository;
        this.preapprovalRepository = preapprovalRepository;
        this.preapprovalChargeRepository = preapprovalChargeRepository;
        this.mercadoPagoClient = mercadoPagoClient;
        this.backendBaseUrl = backendBaseUrl;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public PreapprovalCheckoutResponse createPreapproval(CreatePreapprovalRequest request) {
        if (!ALLOWED_PERIOD_MONTHS.contains(request.periodMonths())) {
            throw new InvalidRequestException("periodMonths deve ser 1, 3 ou 12");
        }

        Plan plan = planRepository.findById(request.planId())
                .filter(Plan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado"));

        Clinic clinic = clinicRepository.findById(TenantContext.currentClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Clínica não encontrada"));

        if (!StringUtils.hasText(clinic.getEmail())) {
            throw new InvalidRequestException(
                    "Cadastre um e-mail de contato da clínica antes de criar uma assinatura recorrente");
        }

        Preapproval preapproval = new Preapproval(clinic, plan, request.periodMonths());
        preapproval = preapprovalRepository.save(preapproval);

        CreatePreapprovalCommand command = new CreatePreapprovalCommand(
                plan.getName() + " - assinatura recorrente (" + request.periodMonths() + " meses)",
                plan.getPriceAmount().multiply(BigDecimal.valueOf(request.periodMonths())),
                request.periodMonths(),
                clinic.getEmail(),
                preapproval.getId().toString(),
                backendBaseUrl + "/api/webhooks/mercado-pago",
                frontendBaseUrl + "/dashboard/plano?status=success");

        PreapprovalResult result = mercadoPagoClient.createPreapproval(command);
        preapproval.setMpPreapprovalId(result.preapprovalId());

        return new PreapprovalCheckoutResponse(preapproval.getId(), result.checkoutUrl());
    }

    @Transactional(readOnly = true)
    public Optional<PreapprovalResponse> getCurrentPreapproval() {
        return preapprovalRepository
                .findFirstByClinicIdOrderByCreatedAtDesc(TenantContext.currentClinicId())
                .map(PreapprovalResponse::from);
    }

    /**
     * Cancels the clinic's current preapproval directly at Mercado Pago before applying the
     * cancellation locally. Deliberately does not touch {@code clinic.status}/{@code maxDoctors} -
     * access lapses naturally via {@link ClinicSubscriptionScheduler} once {@code current_period_end}
     * passes, matching {@link SubscriptionService}'s "no immediate downgrade" posture.
     */
    @Transactional
    public Optional<PreapprovalResponse> cancelPreapproval() {
        Preapproval preapproval = preapprovalRepository
                .findFirstByClinicIdOrderByCreatedAtDesc(TenantContext.currentClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Assinatura recorrente não encontrada"));

        if (preapproval.getStatus() == PreapprovalStatus.CANCELLED) {
            return Optional.of(PreapprovalResponse.from(preapproval));
        }

        if (preapproval.getMpPreapprovalId() != null) {
            mercadoPagoClient.cancelPreapproval(preapproval.getMpPreapprovalId());
        }

        preapproval.markCancelled();
        return Optional.of(PreapprovalResponse.from(preapproval));
    }

    /**
     * Handles a Mercado Pago {@code subscription_preapproval} webhook notification: a status-only
     * change (e.g. the clinic admin authorized or paused the recurring charge). Per RN-014, the
     * status is always re-confirmed directly against Mercado Pago's own API - the webhook payload
     * itself is never trusted.
     */
    @Transactional
    public void processPreapprovalStatusNotification(String preapprovalId) {
        PreapprovalStatusResult result = mercadoPagoClient.getPreapproval(preapprovalId);

        Long id;
        try {
            id = Long.parseLong(result.externalReference());
        } catch (NumberFormatException | NullPointerException ex) {
            log.warn("Mercado Pago webhook: external_reference inválido para preapproval {}", preapprovalId);
            return;
        }

        Preapproval preapproval = preapprovalRepository.findById(id).orElse(null);
        if (preapproval == null) {
            log.warn("Mercado Pago webhook: preapproval {} não encontrada (mp id {})", id, preapprovalId);
            return;
        }

        PreapprovalStatus mapped = mapStatus(result.status());
        if (mapped == null) {
            log.warn("Mercado Pago webhook: status de preapproval desconhecido '{}' para {}", result.status(), preapprovalId);
            return;
        }
        if (mapped == preapproval.getStatus()) {
            return; // already up to date - re-confirmed above, but nothing changed
        }

        if (preapproval.getMpPreapprovalId() == null) {
            preapproval.setMpPreapprovalId(preapprovalId);
        }
        preapproval.applyStatus(mapped);
    }

    /**
     * Handles a Mercado Pago {@code subscription_authorized_payment} webhook notification: a single
     * recurring charge in an already-authorized preapproval's lifecycle. Per RN-014, the payment's
     * status is always re-confirmed directly against Mercado Pago's own API. Idempotent per
     * {@code mp_payment_id}, backstopped by {@code preapproval_charges}'s DB unique constraint - each
     * cycle's charge is applied at most once.
     */
    @Transactional
    public void processPreapprovalChargeNotification(String paymentId) {
        PaymentResult payment = mercadoPagoClient.getPayment(paymentId);

        Long preapprovalId;
        try {
            preapprovalId = Long.parseLong(payment.externalReference());
        } catch (NumberFormatException | NullPointerException ex) {
            log.warn("Mercado Pago webhook: external_reference inválido para cobrança recorrente {}", paymentId);
            return;
        }

        Preapproval preapproval = preapprovalRepository.findById(preapprovalId).orElse(null);
        if (preapproval == null) {
            log.warn("Mercado Pago webhook: preapproval {} não encontrada (cobrança {})", preapprovalId, paymentId);
            return;
        }

        if (preapprovalChargeRepository.existsByMpPaymentId(paymentId)) {
            return; // already processed - re-confirmed above, but never re-apply side effects
        }

        if (!"approved".equals(payment.status())) {
            return; // a failed/rejected charge is handled by the status webhook and/or scheduler lapse, not here
        }

        Instant periodStart = Instant.now();
        Instant periodEnd = periodStart.atZone(ZoneOffset.UTC).plusMonths(preapproval.getPeriodMonths()).toInstant();

        preapprovalChargeRepository.save(new PreapprovalCharge(preapproval, paymentId, payment.status(), periodStart));
        preapproval.registerCharge(periodStart, periodEnd);

        Clinic clinic = preapproval.getClinic();
        clinic.setStatus(ClinicStatus.ACTIVE);
        clinic.setMaxDoctors(preapproval.getPlan().getMaxDoctors());
    }

    private static PreapprovalStatus mapStatus(String mpStatus) {
        if (mpStatus == null) {
            return null;
        }
        return switch (mpStatus) {
            case "pending" -> PreapprovalStatus.PENDING;
            case "authorized" -> PreapprovalStatus.AUTHORIZED;
            case "paused" -> PreapprovalStatus.PAUSED;
            case "cancelled" -> PreapprovalStatus.CANCELLED;
            default -> null;
        };
    }
}
