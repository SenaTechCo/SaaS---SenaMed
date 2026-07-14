package com.senamed.backend.billing.webhook;

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
 */
@RestController
@RequestMapping("/api/webhooks/mercado-pago")
public class MercadoPagoWebhookController {

    private final SubscriptionService subscriptionService;

    public MercadoPagoWebhookController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<Void> receiveNotification(@RequestBody MercadoPagoWebhookPayload payload) {
        if (!"payment".equals(payload.type()) || payload.data() == null || payload.data().id() == null) {
            return ResponseEntity.ok().build();
        }

        subscriptionService.processPaymentNotification(payload.data().id());
        return ResponseEntity.ok().build();
    }
}
