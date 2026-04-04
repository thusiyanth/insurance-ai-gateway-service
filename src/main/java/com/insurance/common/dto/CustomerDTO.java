package com.insurance.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record CustomerDTO(
        @JsonProperty("id") Long id,
        @JsonProperty("nic") String nic,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("lastName") String lastName,
        @JsonProperty("email") String email,
        @JsonProperty("phone") String phone,
        @JsonProperty("address") String address,
        @JsonProperty("dateOfBirth") LocalDate dateOfBirth
) {}
