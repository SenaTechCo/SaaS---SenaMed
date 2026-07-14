package com.senamed.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PreapprovalChargeRepository extends JpaRepository<PreapprovalCharge, Long> {

    boolean existsByMpPaymentId(String mpPaymentId);
}
