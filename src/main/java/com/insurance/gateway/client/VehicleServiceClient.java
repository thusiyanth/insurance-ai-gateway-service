package com.insurance.gateway.client;

import com.insurance.common.dto.VehicleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Optional;

@Component
public class VehicleServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(VehicleServiceClient.class);
    private final WebClient webClient;

    public VehicleServiceClient(@Qualifier("vehicleWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Optional<VehicleDTO> getById(Long id) {
        try {
            logger.info("Calling vehicle-service for id: {}", id);
            VehicleDTO vehicle = webClient.get()
                    .uri("/api/vehicle/{id}", id)
                    .retrieve()
                    .bodyToMono(VehicleDTO.class)
                    .block();
            return Optional.ofNullable(vehicle);
        } catch (WebClientResponseException.NotFound e) {
            logger.warn("Vehicle not found: {}", id);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error calling vehicle-service", e);
            return Optional.empty();
        }
    }

    public Optional<VehicleDTO> getByRegistration(String regNumber) {
        try {
            logger.info("Calling vehicle-service for reg: {}", regNumber);
            VehicleDTO vehicle = webClient.get()
                    .uri("/api/vehicle/registration/{regNumber}", regNumber)
                    .retrieve()
                    .bodyToMono(VehicleDTO.class)
                    .block();
            return Optional.ofNullable(vehicle);
        } catch (WebClientResponseException.NotFound e) {
            logger.warn("Vehicle not found: {}", regNumber);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Error calling vehicle-service by reg", e);
            return Optional.empty();
        }
    }

    public VehicleDTO createVehicle(VehicleDTO dto) {
        logger.info("Calling vehicle-service POST /api/vehicle");
        return webClient.post()
                .uri("/api/vehicle")
                .bodyValue(dto)
                .retrieve()
                .bodyToMono(VehicleDTO.class)
                .block();
    }
}
