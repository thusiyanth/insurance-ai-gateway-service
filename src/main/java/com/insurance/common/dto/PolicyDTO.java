package com.insurance.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PolicyDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("policyNumber") String policyNumber,
        @JsonProperty("customerId") Long customerId,
        @JsonProperty("vehicleId") Long vehicleId,
        @JsonProperty("policyType") String policyType,
        @JsonProperty("status") String status,
        @JsonProperty("startDate") LocalDate startDate,
        @JsonProperty("endDate") LocalDate endDate,
        @JsonProperty("premiumAmount") BigDecimal premiumAmount
) {}
