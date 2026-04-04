package com.insurance.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record CoverDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("coverCode") String coverCode,
        @JsonProperty("coverName") String coverName,
        @JsonProperty("description") String description,
        @JsonProperty("coverType") String coverType,
        @JsonProperty("maxLimit") BigDecimal maxLimit,
        @JsonProperty("sumInsured") BigDecimal sumInsured,
        @JsonProperty("deductible") BigDecimal deductible
) {}
