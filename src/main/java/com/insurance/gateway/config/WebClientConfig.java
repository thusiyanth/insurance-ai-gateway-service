package com.insurance.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${service.policy.url}")
    private String policyServiceUrl;

    @Value("${service.customer.url}")
    private String customerServiceUrl;

    @Value("${service.vehicle.url}")
    private String vehicleServiceUrl;

    @Value("${service.cover.url}")
    private String coverServiceUrl;

    @Bean
    public WebClient policyWebClient() {
        return WebClient.builder()
                .baseUrl(policyServiceUrl)
                .build();
    }

    @Bean
    public WebClient customerWebClient() {
        return WebClient.builder()
                .baseUrl(customerServiceUrl)
                .build();
    }

    @Bean
    public WebClient vehicleWebClient() {
        return WebClient.builder()
                .baseUrl(vehicleServiceUrl)
                .build();
    }

    @Bean
    public WebClient coverWebClient() {
        return WebClient.builder()
                .baseUrl(coverServiceUrl)
                .build();
    }
}
