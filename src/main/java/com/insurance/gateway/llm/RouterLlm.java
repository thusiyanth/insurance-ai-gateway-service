package com.insurance.gateway.llm;

import com.insurance.common.dto.RouterResult;
import com.insurance.common.enums.IntentCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class RouterLlm {

    private static final Logger logger = LoggerFactory.getLogger(RouterLlm.class);

    private final ChatClient chatClient;

    private static final String ROUTER_SYSTEM_PROMPT = """
            You are an insurance query classification system for Test Insurance Sri Lanka.
            
            Analyze the user's message and classify it into exactly ONE category.
            Also extract any identifiers you find in the message.
            
            **Categories:**
            - POLICY_QUERY: Questions about policy status, premium, dates, renewal, policy details, policy number lookup
            - VEHICLE_QUERY: Questions about insured vehicle details, registration, make, model
            - CUSTOMER_QUERY: Questions about policyholder/customer information, personal details, contact info
            - COVER_QUERY: Questions about coverage types, limits, deductibles, what is covered
            - DOCUMENT_QUERY: Questions about insurance terms, conditions, general insurance knowledge, definitions
            - POLICY_CREATION: Requests to create, issue, buy, or generate a new insurance policy
            - GENERAL_QUERY: Greetings, off-topic, general questions not related to a specific policy/customer/vehicle/cover
            
            **Identifier Patterns:**
            - Policy Number: Starts with "POL-" followed by digits (e.g., POL-2024-001)
            - NIC (National Identity Card): 12-digit number (e.g., 200012345678) or old format 9 digits + V/X
            - Phone Number: Sri Lankan format starting with 07 (e.g., 0771234567)
            
            **Rules:**
            - If the user mentions a policy number, NIC, or phone number, extract it
            - If identifiers are found, the intent is most likely POLICY_QUERY unless explicitly asking about vehicle/customer/cover
            - If asking to create a policy, classify as POLICY_CREATION and extract creation fields.
            - For POLICY_CREATION extract:
               - customerName (String)
               - vehicleRegistration (e.g. WP-CBE-1234)
               - vehicleMake (e.g. Toyota Aqua)
               - requestedCovers: JSON array of string cover names (e.g., ["Comprehensive", "Third Party"])
            - Confidence should reflect how certain you are (0.0 to 1.0)
            
            You MUST respond with ONLY valid JSON in this exact format, nothing else:
            {"intent":"POLICY_QUERY","policyNumber":"POL-2024-001","nic":null,"phoneNumber":null,"confidence":0.95, "customerName":null, "vehicleRegistration":null, "vehicleMake":null, "requestedCovers":[]}
            """;

    public RouterLlm(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public RouterResult classifyIntent(String userMessage) {
        logger.info("Router LLM classifying intent for message: {}", userMessage);

        try {
            String response = chatClient.prompt()
                    .system(ROUTER_SYSTEM_PROMPT)
                    .user(userMessage)
                    .call()
                    .content();

            logger.info("Router raw response: {}", response);

            // Parse the JSON response manually for robustness
            return parseRouterResponse(response);

        } catch (Exception e) {
            logger.error("Router LLM classification failed, defaulting to GENERAL_QUERY", e);
            return new RouterResult(IntentCategory.GENERAL_QUERY, null, null, null, 0.5, null, null, null, new java.util.ArrayList<>());
        }
    }

    private RouterResult parseRouterResponse(String response) {
        try {
            // Clean up response - strip markdown code fences if present
            String cleaned = response.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            }
            cleaned = cleaned.trim();

            // Use simple Jackson parsing
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(cleaned);

            IntentCategory intent;
            try {
                intent = IntentCategory.valueOf(node.get("intent").asText("GENERAL_QUERY"));
            } catch (IllegalArgumentException e) {
                intent = IntentCategory.GENERAL_QUERY;
            }

            String policyNumber = getTextOrNull(node, "policyNumber");
            String nic = getTextOrNull(node, "nic");
            String phoneNumber = getTextOrNull(node, "phoneNumber");
            double confidence = node.has("confidence") ? node.get("confidence").asDouble(0.5) : 0.5;

            String customerName = getTextOrNull(node, "customerName");
            String vehicleRegistration = getTextOrNull(node, "vehicleRegistration");
            String vehicleMake = getTextOrNull(node, "vehicleMake");
            
            java.util.List<String> requestedCovers = new java.util.ArrayList<>();
            if (node.has("requestedCovers") && node.get("requestedCovers").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode coverNode : node.get("requestedCovers")) {
                    requestedCovers.add(coverNode.asText());
                }
            }

            RouterResult result = new RouterResult(intent, policyNumber, nic, phoneNumber, confidence, customerName, vehicleRegistration, vehicleMake, requestedCovers);
            logger.info("Parsed RouterResult: {}", result);
            return result;

        } catch (Exception e) {
            logger.warn("Failed to parse Router JSON response: '{}', falling back", response, e);
            // Try to detect intent from keywords
            return fallbackClassification(response);
        }
    }

    private String getTextOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            String val = node.get(field).asText();
            return (val.isEmpty() || "null".equalsIgnoreCase(val)) ? null : val;
        }
        return null;
    }

    private RouterResult fallbackClassification(String response) {
        String upper = response.toUpperCase();
        if (upper.contains("POLICY_CREATION") || upper.contains("CREATE A POLICY") || upper.contains("ISSUE A POLICY")) {
            return new RouterResult(IntentCategory.POLICY_CREATION, null, null, null, 0.5, null, null, null, new java.util.ArrayList<>());
        }
        if (upper.contains("POLICY_QUERY")) return new RouterResult(IntentCategory.POLICY_QUERY, null, null, null, 0.5, null, null, null, new java.util.ArrayList<>());
        if (upper.contains("VEHICLE_QUERY")) return new RouterResult(IntentCategory.VEHICLE_QUERY, null, null, null, 0.5, null, null, null, new java.util.ArrayList<>());
        if (upper.contains("CUSTOMER_QUERY")) return new RouterResult(IntentCategory.CUSTOMER_QUERY, null, null, null, 0.5, null, null, null, new java.util.ArrayList<>());
        if (upper.contains("COVER_QUERY")) return new RouterResult(IntentCategory.COVER_QUERY, null, null, null, 0.5, null, null, null, new java.util.ArrayList<>());
        if (upper.contains("DOCUMENT_QUERY")) return new RouterResult(IntentCategory.DOCUMENT_QUERY, null, null, null, 0.5, null, null, null, new java.util.ArrayList<>());
        return new RouterResult(IntentCategory.GENERAL_QUERY, null, null, null, 0.3, null, null, null, new java.util.ArrayList<>());
    }
}
