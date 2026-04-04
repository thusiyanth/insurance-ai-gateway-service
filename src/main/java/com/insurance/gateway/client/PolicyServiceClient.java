package com.insurance.gateway.client;

import com.insurance.common.dto.PolicyDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PolicyServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(PolicyServiceClient.class);
    private final WebClient webClient;

    public PolicyServiceClient(@Qualifier("policyWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Optional<PolicyDTO> getByPolicyNumber(String policyNumber) {
        try {
            logger.info("Calling policy-service for policy: {}", policyNumber);
            PolicyDTO policy = webClient.get()
                    .uri("/api/policy/{policyNumber}", policyNumber)
                    .retrieve()
                    .bodyToMono(PolicyDTO.class)
                    .block();
            return Optional.ofNullable(policy);
        } catch (WebClientResponseException.NotFound e) {
            logger.warn("Policy not found: {}", policyNumber);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error calling policy-service", e);
            return Optional.empty();
        }
    }

    public List<PolicyDTO> getByCustomerId(Long customerId) {
        try {
            logger.info("Calling policy-service for customer: {}", customerId);
            return webClient.get()
                    .uri("/api/policy/customer/{customerId}", customerId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<PolicyDTO>>() {})
                    .block();
        } catch (Exception e) {
            logger.error("Error fetching policies for customer {}", customerId, e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getPolicyCovers(String policyNumber) {
        try {
            logger.info("Calling policy-service for covers of: {}", policyNumber);
            return webClient.get()
                    .uri("/api/policy/{policyNumber}/covers", policyNumber)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();
        } catch (Exception e) {
            logger.error("Error fetching covers for policy {}", policyNumber, e);
            return Collections.emptyList();
        }
    }

    public PolicyDTO createPolicy(com.insurance.common.dto.PolicyCreationRequest dto) {
        logger.info("Calling policy-service POST /api/policy");
        return webClient.post()
                .uri("/api/policy")
                .bodyValue(dto)
                .retrieve()
                .bodyToMono(PolicyDTO.class)
                .block();
    }
}
