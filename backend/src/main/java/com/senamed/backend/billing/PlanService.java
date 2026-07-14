package com.senamed.backend.billing;

import com.senamed.backend.billing.dto.PlanResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listActivePlans() {
        return planRepository.findByActiveTrueOrderByPriceAmountAsc().stream()
                .map(PlanResponse::from)
                .toList();
    }
}
