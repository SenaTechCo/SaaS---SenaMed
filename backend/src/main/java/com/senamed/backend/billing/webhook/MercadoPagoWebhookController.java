package com.senamed.backend.billing.webhook;

import com.senamed.backend.billing.PreapprovalService;
import com.senamed.backend.billing.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (unauthenticated) endpoint Mercado Pago calls directly - see
 * {@link com.senamed.backend.config.SecurityConfig} for the explicit permitAll rule, and
 * {@link SubscriptionService#processPaymentNotification} for why a JWT isn't needed here: every
 * notification is re-confirmed against Mercado Pago's own API before anything is activated (RN-014).
 * Also routes Preapproval (KAN-76) notification types to {@link PreapprovalService}: {@code
 * subscription_preapproval} for status-only changes, {@code subscription_authorized_payment} for
 * each recurring charge. Field shape ({@code {type, data:{id}}}) is assumed identical for these
 * types - re-verify against MP's real webhook docs at first sandbox integration.
 */
@RestController
@RequestMapping("/api/webhooks/mercado-pago")
public class MercadoPagoWebhookController {

    private final SubscriptionService subscriptionService;
    private final PreapprovalService preapprovalService;

    public MercadoPagoWebhookController(SubscriptionService subscriptionService, PreapprovalService preapprovalService) {
        this.subscriptionService = subscriptionService;
        this.preapprovalService = preapprovalService;
    }

    @PostMapping
    public ResponseEntity<Void> receiveNotification(@RequestBody MercadoPagoWebhookPayload payload) {
        if (payload.data() == null || payload.data().id() == null) {
            return ResponseEntity.ok().build();
        }

        switch (payload.type()) {
            case "payment" -> subscriptionService.processPaymentNotification(payload.data().id());
            case "subscription_preapproval" -> preapprovalService.processPreapprovalStatusNotification(payload.data().id());
            case "subscription_authorized_payment" -> preapprovalService.processPreapprovalChargeNotification(payload.data().id());
            default -> { /* no-op - unknown/unsupported notification type */ }
        }
        return ResponseEntity.ok().build();
    }
}
