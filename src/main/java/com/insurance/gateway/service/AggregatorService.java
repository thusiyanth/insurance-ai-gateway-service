package com.insurance.gateway.service;

import com.insurance.common.dto.*;
import com.insurance.gateway.client.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregator Service — calls multiple domain microservices and assembles
 * a complete view of a policy with its associated customer, vehicle, and covers.
 */
@Service
@RequiredArgsConstructor
public class AggregatorService {

    private static final Logger logger = LoggerFactory.getLogger(AggregatorService.class);

    private final PolicyServiceClient policyClient;
    private final CustomerServiceClient customerClient;
    private final VehicleServiceClient vehicleClient;
    private final CoverServiceClient coverClient;

    /**
     * Full aggregation by policy number: fetches policy → customer → vehicle → covers
     */
    public Optional<AggregatedPolicyResponse> aggregateByPolicyNumber(String policyNumber) {
        logger.info("Aggregating full data for policy: {}", policyNumber);

        Optional<PolicyDTO> policyOpt = policyClient.getByPolicyNumber(policyNumber);
        if (policyOpt.isEmpty()) {
            logger.warn("Policy not found: {}", policyNumber);
            return Optional.empty();
        }

        PolicyDTO policy = policyOpt.get();
        return Optional.of(aggregateFromPolicy(policy));
    }

    /**
     * Aggregate by NIC: find customer → find their policies → aggregate first active policy
     */
    public Optional<AggregatedPolicyResponse> aggregateByNic(String nic) {
        logger.info("Aggregating data by NIC: {}", nic);
        List<AggregatedPolicyResponse> all = aggregateAllByNic(nic);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    public List<AggregatedPolicyResponse> aggregateAllByNic(String nic) {
        logger.info("Aggregating ALL policies by NIC: {}", nic);
        Optional<CustomerDTO> customerOpt = customerClient.getByNic(nic);
        if (customerOpt.isEmpty()) return Collections.emptyList();

        CustomerDTO customer = customerOpt.get();
        List<PolicyDTO> policies = policyClient.getByCustomerId(customer.id());
        if (policies.isEmpty()) return Collections.emptyList();

        List<AggregatedPolicyResponse> results = new ArrayList<>();
        for (PolicyDTO policy : policies) {
            AggregatedPolicyResponse response = aggregateFromPolicy(policy);
            results.add(new AggregatedPolicyResponse(response.policy(), customer, response.vehicle(), response.covers()));
        }
        return results;
    }

    /**
     * Aggregate by phone: find customer → find their policies → aggregate
     */
    public Optional<AggregatedPolicyResponse> aggregateByPhone(String phone) {
        logger.info("Aggregating data by phone: {}", phone);
        List<AggregatedPolicyResponse> all = aggregateAllByPhone(phone);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    public List<AggregatedPolicyResponse> aggregateAllByPhone(String phone) {
        logger.info("Aggregating ALL policies by phone: {}", phone);
        Optional<CustomerDTO> customerOpt = customerClient.getByPhone(phone);
        if (customerOpt.isEmpty()) return Collections.emptyList();

        CustomerDTO customer = customerOpt.get();
        List<PolicyDTO> policies = policyClient.getByCustomerId(customer.id());
        if (policies.isEmpty()) return Collections.emptyList();

        List<AggregatedPolicyResponse> results = new ArrayList<>();
        for (PolicyDTO policy : policies) {
            AggregatedPolicyResponse response = aggregateFromPolicy(policy);
            results.add(new AggregatedPolicyResponse(response.policy(), customer, response.vehicle(), response.covers()));
        }
        return results;
    }

    /**
     * Internal: given a PolicyDTO, fetch its associated customer, vehicle, and covers
     */
    private AggregatedPolicyResponse aggregateFromPolicy(PolicyDTO policy) {
        // Fetch customer
        CustomerDTO customer = customerClient.getById(policy.customerId()).orElse(null);

        // Fetch vehicle
        VehicleDTO vehicle = vehicleClient.getById(policy.vehicleId()).orElse(null);

        // Fetch policy covers (the join table data)
        List<Map<String, Object>> policyCoverMappings = policyClient.getPolicyCovers(policy.policyNumber());

        // Enrich covers with sum_insured and deductible from the policy_covers mapping
        List<CoverDTO> enrichedCovers = new ArrayList<>();
        if (!policyCoverMappings.isEmpty()) {
            List<Long> coverIds = policyCoverMappings.stream()
                    .map(m -> ((Number) m.get("coverId")).longValue())
                    .collect(Collectors.toList());

            List<CoverDTO> basCovers = coverClient.getByIds(coverIds);
            Map<Long, CoverDTO> coverMap = basCovers.stream()
                    .collect(Collectors.toMap(CoverDTO::id, c -> c));

            for (Map<String, Object> mapping : policyCoverMappings) {
                Long coverId = ((Number) mapping.get("coverId")).longValue();
                CoverDTO baseCover = coverMap.get(coverId);
                if (baseCover != null) {
                    BigDecimal sumInsured = toBigDecimal(mapping.get("sumInsured"));
                    BigDecimal deductible = toBigDecimal(mapping.get("deductible"));
                    enrichedCovers.add(new CoverDTO(
                            baseCover.id(), baseCover.coverCode(), baseCover.coverName(),
                            baseCover.description(), baseCover.coverType(), baseCover.maxLimit(),
                            sumInsured, deductible
                    ));
                }
            }
        }

        return new AggregatedPolicyResponse(policy, customer, vehicle, enrichedCovers);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return new BigDecimal(value.toString());
    }
}
