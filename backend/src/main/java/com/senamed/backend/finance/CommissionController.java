package com.senamed.backend.finance;

import com.senamed.backend.finance.dto.CommissionCalculationResponse;
import com.senamed.backend.finance.dto.CommissionConfigRequest;
import com.senamed.backend.finance.dto.CommissionConfigResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/commissions")
public class CommissionController {

    private final CommissionService commissionService;

    public CommissionController(CommissionService commissionService) {
        this.commissionService = commissionService;
    }

    @GetMapping("/{doctorId}")
    public CommissionCalculationResponse calculate(
            @PathVariable Long doctorId, @RequestParam int year, @RequestParam int month) {
        return commissionService.calculate(doctorId, year, month);
    }

    @PutMapping("/{doctorId}")
    public CommissionConfigResponse upsertConfig(
            @PathVariable Long doctorId, @Valid @RequestBody CommissionConfigRequest request) {
        return commissionService.upsertConfig(doctorId, request);
    }

    @GetMapping("/{doctorId}/config")
    public CommissionConfigResponse getConfig(@PathVariable Long doctorId) {
        return commissionService.getConfig(doctorId);
    }
}
