package com.insurance.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AggregatedPolicyResponse(
        @JsonProperty("policy") PolicyDTO policy,
        @JsonProperty("customer") CustomerDTO customer,
        @JsonProperty("vehicle") VehicleDTO vehicle,
        @JsonProperty("covers") List<CoverDTO> covers
) {}
