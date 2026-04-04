package com.insurance.common.dto;

import java.util.List;

public record PolicyCreationRequest(
        Long customerId,
        Long vehicleId,
        List<Long> coverIds
) {}
