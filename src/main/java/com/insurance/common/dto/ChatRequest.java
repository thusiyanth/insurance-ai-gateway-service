package com.insurance.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChatRequest(
        @JsonProperty("message") String message,
        @JsonProperty("sessionId") String sessionId
) {}
