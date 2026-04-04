package com.insurance.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VehicleDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("registrationNumber") String registrationNumber,
        @JsonProperty("make") String make,
        @JsonProperty("model") String model,
        @JsonProperty("year") int year,
        @JsonProperty("engineNumber") String engineNumber,
        @JsonProperty("chassisNumber") String chassisNumber,
        @JsonProperty("fuelType") String fuelType,
        @JsonProperty("vehicleClass") String vehicleClass
) {}
