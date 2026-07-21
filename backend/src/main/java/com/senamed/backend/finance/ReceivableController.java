package com.senamed.backend.finance;

import com.senamed.backend.finance.dto.FinanceSummaryResponse;
import com.senamed.backend.finance.dto.ReceivableResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReceivableController {

    private final ReceivableService receivableService;

    public ReceivableController(ReceivableService receivableService) {
        this.receivableService = receivableService;
    }

    @GetMapping("/api/finance/receivables")
    public List<ReceivableResponse> listAll(@RequestParam(required = false) ReceivableStatus status) {
        return receivableService.listAll(status);
    }

    @PatchMapping("/api/finance/receivables/{id}/pagar")
    public ReceivableResponse markPaid(@PathVariable Long id) {
        return receivableService.markPaid(id);
    }

    @GetMapping("/api/finance/summary")
    public FinanceSummaryResponse summary() {
        return receivableService.summary();
    }
}
