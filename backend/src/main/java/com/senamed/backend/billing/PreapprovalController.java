package com.senamed.backend.billing;

import com.senamed.backend.billing.dto.CreatePreapprovalRequest;
import com.senamed.backend.billing.dto.PreapprovalCheckoutResponse;
import com.senamed.backend.billing.dto.PreapprovalResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nested under {@code /api/subscriptions/**} deliberately - {@code WebMvcConfig}'s
 * {@code ClinicStatusInterceptor} exclusion for {@code /api/subscriptions/**} already covers this
 * path, so a blocked clinic can still manage/cancel its recurring subscription.
 */
@RestController
@RequestMapping("/api/subscriptions/preapproval")
public class PreapprovalController {

    private final PreapprovalService preapprovalService;

    public PreapprovalController(PreapprovalService preapprovalService) {
        this.preapprovalService = preapprovalService;
    }

    @PostMapping
    public ResponseEntity<PreapprovalCheckoutResponse> create(@Valid @RequestBody CreatePreapprovalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(preapprovalService.createPreapproval(request));
    }

    @GetMapping("/me")
    public ResponseEntity<PreapprovalResponse> getCurrent() {
        return preapprovalService.getCurrentPreapproval()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/me/cancel")
    public ResponseEntity<PreapprovalResponse> cancel() {
        return preapprovalService.cancelPreapproval()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
