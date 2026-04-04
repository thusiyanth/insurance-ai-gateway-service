package com.insurance.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.insurance.common.enums.IntentCategory;

public record RouterResult(
        @JsonProperty("intent") IntentCategory intent,
        @JsonProperty("policyNumber") String policyNumber,
        @JsonProperty("nic") String nic,
        @JsonProperty("phoneNumber") String phoneNumber,
        @JsonProperty("confidence") double confidence,
        @JsonProperty("customerName") String customerName,
        @JsonProperty("vehicleRegistration") String vehicleRegistration,
        @JsonProperty("vehicleMake") String vehicleMake,
        @JsonProperty("requestedCovers") java.util.List<String> requestedCovers
) {}
