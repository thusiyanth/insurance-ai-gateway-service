package com.insurance.gateway.client;

import com.insurance.common.dto.CustomerDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Component
public class CustomerServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(CustomerServiceClient.class);
    private final WebClient webClient;

    public CustomerServiceClient(@Qualifier("customerWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Optional<CustomerDTO> getById(Long id) {
        try {
            logger.info("Calling customer-service for id: {}", id);
            CustomerDTO customer = webClient.get()
                    .uri("/api/customer/{id}", id)
                    .retrieve()
                    .bodyToMono(CustomerDTO.class)
                    .block();
            return Optional.ofNullable(customer);
        } catch (WebClientResponseException.NotFound e) {
            logger.warn("Customer not found: {}", id);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error calling customer-service", e);
            return Optional.empty();
        }
    }

    public Optional<CustomerDTO> getByNic(String nic) {
        try {
            logger.info("Calling customer-service for NIC: {}", nic);
            CustomerDTO customer = webClient.get()
                    .uri("/api/customer/nic/{nic}", nic)
                    .retrieve()
                    .bodyToMono(CustomerDTO.class)
                    .block();
            return Optional.ofNullable(customer);
        } catch (WebClientResponseException.NotFound e) {
            logger.warn("Customer not found by NIC: {}", nic);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error calling customer-service by NIC", e);
            return Optional.empty();
        }
    }

    public Optional<CustomerDTO> getByPhone(String phone) {
        try {
            logger.info("Calling customer-service for phone: {}", phone);
            CustomerDTO customer = webClient.get()
                    .uri("/api/customer/phone/{phone}", phone)
                    .retrieve()
                    .bodyToMono(CustomerDTO.class)
                    .block();
            return Optional.ofNullable(customer);
        } catch (WebClientResponseException.NotFound e) {
            logger.warn("Customer not found by phone: {}", phone);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error calling customer-service by phone", e);
            return Optional.empty();
        }
    }

    public CustomerDTO createCustomer(CustomerDTO dto) {
        logger.info("Calling customer-service POST /api/customer");
        return webClient.post()
                .uri("/api/customer")
                .bodyValue(dto)
                .retrieve()
                .bodyToMono(CustomerDTO.class)
                .block();
    }
}
