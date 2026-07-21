package com.senamed.backend.finance.dto;

import java.math.BigDecimal;

public record CommissionConfigResponse(Long doctorId, BigDecimal percentage, boolean active) {
}
