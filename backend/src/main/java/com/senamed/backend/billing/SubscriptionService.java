package com.senamed.backend.billing;

import com.senamed.backend.billing.dto.CheckoutRequest;
import com.senamed.backend.billing.dto.CheckoutResponse;
import com.senamed.backend.billing.dto.SubscriptionResponse;
import com.senamed.backend.billing.mercadopago.CreatePreferenceCommand;
import com.senamed.backend.billing.mercadopago.MercadoPagoClient;
import com.senamed.backend.billing.mercadopago.PaymentResult;
import com.senamed.backend.billing.mercadopago.PreferenceResult;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

/**
 * Backs the "checkout avulso" flow (RF-020): a single, non-recurring Mercado Pago Checkout Pro
 * payment for a chosen period. Real activation only ever happens via
 * {@code MercadoPagoWebhookController}, never here - this service only creates the PENDING
 * subscription row and the hosted checkout URL to redirect the clinic admin to.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private static final Set<Integer> ALLOWED_PERIOD_MONTHS = Set.of(1, 3, 12);

    private final ClinicRepository clinicRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MercadoPagoClient mercadoPagoClient;
    private final String backendBaseUrl;
    private final String frontendBaseUrl;

    public SubscriptionService(
            ClinicRepository clinicRepository,
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            MercadoPagoClient mercadoPagoClient,
            @Value("${senamed.backend.base-url}") String backendBaseUrl,
            @Value("${senamed.frontend.base-url}") String frontendBaseUrl) {
        this.clinicRepository = clinicRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.mercadoPagoClient = mercadoPagoClient;
        this.backendBaseUrl = backendBaseUrl;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public CheckoutResponse createCheckout(CheckoutRequest request) {
        if (!ALLOWED_PERIOD_MONTHS.contains(request.periodMonths())) {
            throw new InvalidRequestException("periodMonths deve ser 1, 3 ou 12");
        }

        Plan plan = planRepository.findById(request.planId())
                .filter(Plan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado"));

        Clinic clinic = clinicRepository.findById(TenantContext.currentClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Clínica não encontrada"));

        Subscription subscription = new Subscription(clinic, plan, request.periodMonths());
        subscription = subscriptionRepository.save(subscription);

        CreatePreferenceCommand command = new CreatePreferenceCommand(
                plan.getName() + " - " + request.periodMonths() + " meses",
                plan.getPriceAmount().multiply(BigDecimal.valueOf(request.periodMonths())),
                subscription.getId().toString(),
                backendBaseUrl + "/api/webhooks/mercado-pago",
                frontendBaseUrl + "/dashboard/plano?status=success",
                frontendBaseUrl + "/dashboard/plano?status=failure",
                frontendBaseUrl + "/dashboard/plano?status=pending");

        PreferenceResult result = mercadoPagoClient.createPreference(command);
        subscription.setMpPreferenceId(result.preferenceId());

        return new CheckoutResponse(subscription.getId(), result.checkoutUrl());
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> getCurrentSubscription() {
        return subscriptionRepository
                .findFirstByClinicIdOrderByCreatedAtDesc(TenantContext.currentClinicId())
                .map(SubscriptionResponse::from);
    }

    /**
     * Handles a Mercado Pago payment webhook notification (RF-021). Per RN-014, the payment's
     * status is always re-confirmed directly against Mercado Pago's own API - the webhook payload
     * itself is never trusted. Idempotent: replaying the same already-{@code APPROVED} payment id
     * is a no-op (still re-confirms via the API, but never re-applies the activation side effects).
     * Any outcome other than "MP itself couldn't be reached" (which propagates as
     * {@code MercadoPagoIntegrationException} -> 502, so MP's retry mechanism helps) is a 200 no-op:
     * an unknown/malformed external reference, an unknown subscription, or a non-terminal payment
     * status will never resolve differently by retrying.
     */
    @Transactional
    public void processPaymentNotification(String paymentId) {
        PaymentResult payment = mercadoPagoClient.getPayment(paymentId);

        Long subscriptionId;
        try {
            subscriptionId = Long.parseLong(payment.externalReference());
        } catch (NumberFormatException | NullPointerException ex) {
            log.warn("Mercado Pago webhook: external_reference inválido para pagamento {}", paymentId);
            return;
        }

        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            log.warn("Mercado Pago webhook: assinatura {} não encontrada (pagamento {})", subscriptionId, paymentId);
            return;
        }

        if (paymentId.equals(subscription.getMpPaymentId()) && subscription.getStatus() == SubscriptionStatus.APPROVED) {
            return; // already processed - re-confirmed above, but never re-apply side effects
        }

        String status = payment.status();
        if ("approved".equals(status)) {
            Instant periodStart = Instant.now();
            Instant periodEnd = periodStart.atZone(ZoneOffset.UTC).plusMonths(subscription.getPeriodMonths()).toInstant();
            subscription.markApproved(paymentId, periodStart, periodEnd);

            Clinic clinic = subscription.getClinic();
            clinic.setStatus(ClinicStatus.ACTIVE);
            clinic.setMaxDoctors(subscription.getPlan().getMaxDoctors());
        } else if ("rejected".equals(status) || "cancelled".equals(status)) {
            subscription.markRejected(paymentId);
        }
        // any other status (pending, in_process, ...) is a no-op - MP will notify again on the next transition
    }
}
