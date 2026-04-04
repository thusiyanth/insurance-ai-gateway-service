package com.insurance.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatResponse(
        @JsonProperty("response") String response,
        @JsonProperty("intent") String intent,
        @JsonProperty("dataSource") String dataSource,
        @JsonProperty("timestamp") String timestamp
) {}
