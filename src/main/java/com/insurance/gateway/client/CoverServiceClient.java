package com.insurance.gateway.client;

import com.insurance.common.dto.CoverDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CoverServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(CoverServiceClient.class);
    private final WebClient webClient;

    public CoverServiceClient(@Qualifier("coverWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Optional<CoverDTO> getById(Long id) {
        try {
            CoverDTO cover = webClient.get()
                    .uri("/api/cover/{id}", id)
                    .retrieve()
                    .bodyToMono(CoverDTO.class)
                    .block();
            return Optional.ofNullable(cover);
        } catch (Exception e) {
            logger.error("Error fetching cover {}", id, e);
            return Optional.empty();
        }
    }

    public List<CoverDTO> getByIds(List<Long> ids) {
        try {
            String idsParam = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/cover/batch")
                            .queryParam("ids", idsParam)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<CoverDTO>>() {})
                    .block();
        } catch (Exception e) {
            logger.error("Error fetching covers by ids", e);
            return Collections.emptyList();
        }
    }

    public List<CoverDTO> getAll() {
        try {
            return webClient.get()
                    .uri("/api/cover")
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<CoverDTO>>() {})
                    .block();
        } catch (Exception e) {
            logger.error("Error fetching all covers", e);
            return Collections.emptyList();
        }
    }
}
