package com.insurance.gateway.service;

import com.insurance.common.dto.*;
import com.insurance.common.enums.IntentCategory;
import com.insurance.gateway.client.*;
import com.insurance.gateway.llm.RagLlm;
import com.insurance.gateway.llm.ResponseFormatterLlm;
import com.insurance.gateway.llm.RouterLlm;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Chat Orchestrator — the central brain of the system.
 * 
 * Flow:
 * 1. User message → Router LLM → intent + identifiers
 * 2. Based on intent, route to appropriate service(s)
 * 3. Fetch data from domain microservices
 * 4. Format response using Response Formatter LLM
 */
@Service
@RequiredArgsConstructor
public class ChatOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(ChatOrchestrator.class);

    private final RouterLlm routerLlm;
    private final ResponseFormatterLlm formatterLlm;
    private final RagLlm ragLlm;
    private final AggregatorService aggregatorService;
    private final CustomerServiceClient customerClient;
    private final VehicleServiceClient vehicleClient;
    private final CoverServiceClient coverClient;
    private final PolicyServiceClient policyClient;

    public ChatResponse processMessage(ChatRequest request) {
        String userMessage = request.message();
        logger.info("=== Processing chat message: {} ===", userMessage);

        if (userMessage.startsWith("[SYSTEM_POLICY_SUBMIT]")) {
            return processWizardSubmission(userMessage);
        }

        // Step 1: Route intent
        RouterResult routerResult = routerLlm.classifyIntent(userMessage);
        IntentCategory intent = routerResult.intent();
        logger.info("Router result: intent={}, policyNumber={}, nic={}, phone={}, confidence={}",
                intent, routerResult.policyNumber(), routerResult.nic(), routerResult.phoneNumber(), routerResult.confidence());

        // Step 2: Process based on intent
        String response;
        String dataSource;

        switch (intent) {
            case POLICY_QUERY -> {
                var result = handlePolicyQuery(userMessage, routerResult);
                response = result.response;
                dataSource = result.dataSource;
            }
            case VEHICLE_QUERY -> {
                var result = handleVehicleQuery(userMessage, routerResult);
                response = result.response;
                dataSource = result.dataSource;
            }
            case CUSTOMER_QUERY -> {
                var result = handleCustomerQuery(userMessage, routerResult);
                response = result.response;
                dataSource = result.dataSource;
            }
            case COVER_QUERY -> {
                var result = handleCoverQuery(userMessage, routerResult);
                response = result.response;
                dataSource = result.dataSource;
            }
            case DOCUMENT_QUERY -> {
                response = ragLlm.answerFromDocuments(userMessage);
                dataSource = "knowledge_base";
            }
            case POLICY_CREATION -> {
                List<CoverDTO> covers = coverClient.getAll();
                response = formatterLlm.formatPolicyWizard(covers);
                dataSource = "wizard_ui";
            }
            default -> {
                response = formatterLlm.formatGeneralResponse(userMessage);
                dataSource = "llm";
            }
        }

        return new ChatResponse(
                response,
                intent.name(),
                dataSource,
                LocalDateTime.now().toString()
        );
    }

    // ==================== Intent Handlers ====================

    private QueryResult handlePolicyQuery(String userMessage, RouterResult router) {
        // Single policy lookup by policy number
        if (router.policyNumber() != null) {
            Optional<AggregatedPolicyResponse> aggregated = aggregatorService.aggregateByPolicyNumber(router.policyNumber());
            if (aggregated.isPresent()) {
                String response = formatterLlm.formatPolicyResponse(userMessage, aggregated.get());
                return new QueryResult(response, "policy_db + customer_db + vehicle_db + cover_db");
            }
        }

        // Multi-policy lookup by NIC — may return multiple policies
        if (router.nic() != null) {
            java.util.List<AggregatedPolicyResponse> allPolicies = aggregatorService.aggregateAllByNic(router.nic());
            if (!allPolicies.isEmpty()) {
                if (allPolicies.size() == 1) {
                    String response = formatterLlm.formatPolicyResponse(userMessage, allPolicies.get(0));
                    return new QueryResult(response, "policy_db + customer_db + vehicle_db + cover_db");
                }
                String response = formatterLlm.formatMultiPolicyResponse(userMessage, allPolicies);
                return new QueryResult(response, "policy_db + customer_db + vehicle_db + cover_db");
            }
        }

        // Multi-policy lookup by phone
        if (router.phoneNumber() != null) {
            java.util.List<AggregatedPolicyResponse> allPolicies = aggregatorService.aggregateAllByPhone(router.phoneNumber());
            if (!allPolicies.isEmpty()) {
                if (allPolicies.size() == 1) {
                    String response = formatterLlm.formatPolicyResponse(userMessage, allPolicies.get(0));
                    return new QueryResult(response, "policy_db + customer_db + vehicle_db + cover_db");
                }
                String response = formatterLlm.formatMultiPolicyResponse(userMessage, allPolicies);
                return new QueryResult(response, "policy_db + customer_db + vehicle_db + cover_db");
            }
        }

        String searchInfo = buildSearchInfo(router);
        String response = formatterLlm.formatNotFoundResponse(userMessage,
                "No policy found. " + searchInfo +
                " Please verify the information and try again, or contact our helpline at 011-2345678.");
        return new QueryResult(response, "no_data");
    }

    private QueryResult handleVehicleQuery(String userMessage, RouterResult router) {
        // If we have a policy number, we can get the vehicle through the policy
        if (router.policyNumber() != null) {
            Optional<AggregatedPolicyResponse> aggregated = aggregatorService.aggregateByPolicyNumber(router.policyNumber());
            if (aggregated.isPresent() && aggregated.get().vehicle() != null) {
                String response = formatterLlm.formatVehicleResponse(userMessage, aggregated.get().vehicle());
                return new QueryResult(response, "vehicle_db");
            }
        }

        // Try via NIC or phone
        if (router.nic() != null) {
            Optional<AggregatedPolicyResponse> aggregated = aggregatorService.aggregateByNic(router.nic());
            if (aggregated.isPresent() && aggregated.get().vehicle() != null) {
                String response = formatterLlm.formatVehicleResponse(userMessage, aggregated.get().vehicle());
                return new QueryResult(response, "vehicle_db");
            }
        }

        String response = formatterLlm.formatNotFoundResponse(userMessage,
                "I couldn't find vehicle information. Please provide a valid policy number, NIC, or phone number.");
        return new QueryResult(response, "no_data");
    }

    private QueryResult handleCustomerQuery(String userMessage, RouterResult router) {
        Optional<CustomerDTO> customer = Optional.empty();

        if (router.nic() != null) {
            customer = customerClient.getByNic(router.nic());
        }
        if (customer.isEmpty() && router.phoneNumber() != null) {
            customer = customerClient.getByPhone(router.phoneNumber());
        }
        if (customer.isEmpty() && router.policyNumber() != null) {
            Optional<AggregatedPolicyResponse> aggregated = aggregatorService.aggregateByPolicyNumber(router.policyNumber());
            if (aggregated.isPresent()) {
                customer = Optional.ofNullable(aggregated.get().customer());
            }
        }

        if (customer.isPresent()) {
            String response = formatterLlm.formatCustomerResponse(userMessage, customer.get());
            return new QueryResult(response, "customer_db");
        }

        String response = formatterLlm.formatNotFoundResponse(userMessage,
                "I couldn't find customer information. Please provide a valid NIC, phone number, or policy number.");
        return new QueryResult(response, "no_data");
    }

    private QueryResult handleCoverQuery(String userMessage, RouterResult router) {
        // If policy number provided, show covers for that specific policy in Report format
        if (router.policyNumber() != null) {
            Optional<AggregatedPolicyResponse> aggregated = aggregatorService.aggregateByPolicyNumber(router.policyNumber());
            if (aggregated.isPresent() && aggregated.get().covers() != null && !aggregated.get().covers().isEmpty()) {
                String response = formatterLlm.formatCoverResponse(userMessage, aggregated.get().covers());
                return new QueryResult(response, "cover_db + policy_db");
            }
        }

        // Check if query is conversational/knowledge-seeking
        String msg = userMessage.toLowerCase();
        boolean isKnowledgeQuery = msg.contains("tell me") || msg.contains("about") || 
                                    msg.contains("what is") || msg.contains("explain") || 
                                    msg.contains("how does") || msg.contains("list");

        // Otherwise show all available cover types
        List<CoverDTO> allCovers = coverClient.getAll();
        if (!allCovers.isEmpty()) {
            // For general "tell me about" queries, use a conversational summary
            if (isKnowledgeQuery && router.policyNumber() == null) {
                String context = allCovers.stream()
                        .map(c -> String.format("- %s: %s", c.coverName(), c.description()))
                        .collect(java.util.stream.Collectors.joining("\n"));
                String response = formatterLlm.formatKnowledgeResponse(userMessage, context);
                return new QueryResult(response, "knowledge_base (cover_db)");
            }
            
            // For default cover lookups without a specific "tell me" intent, use the Report
            String response = formatterLlm.formatCoverResponse(userMessage, allCovers);
            return new QueryResult(response, "cover_db");
        }

        String response = formatterLlm.formatNotFoundResponse(userMessage, "No coverage information available.");
        return new QueryResult(response, "no_data");
    }

    // ==================== Utilities ====================

    private ChatResponse processWizardSubmission(String userMessage) {
        try {
            String jsonPart = userMessage.replace("[SYSTEM_POLICY_SUBMIT]", "").trim();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(jsonPart);
            
            String name = node.has("name") ? node.get("name").asText() : "New Customer";
            String nic = node.has("nic") ? node.get("nic").asText() : "UNK-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            String phone = node.has("phone") ? node.get("phone").asText() : "";
            
            String reg = node.has("vehicle_number") ? node.get("vehicle_number").asText() : "REG-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            String brand = node.has("brand") ? node.get("brand").asText() : "";
            String model = node.has("model") ? node.get("model").asText() : "";
            
            java.util.List<String> requestedCovers = new java.util.ArrayList<>();
            if (node.has("covers") && node.get("covers").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode c : node.get("covers")) {
                    requestedCovers.add(c.asText());
                }
            }
            
            RouterResult fakeRouter = new RouterResult(IntentCategory.POLICY_CREATION, null, nic, phone, 1.0, name, reg, brand + " " + model, requestedCovers);
            QueryResult result = handlePolicyCreation(userMessage, fakeRouter);
            
            return new ChatResponse(result.response(), IntentCategory.POLICY_CREATION.name(), result.dataSource(), LocalDateTime.now().toString());

        } catch (Exception e) {
            logger.error("Error processing wizard submission", e);
            String response = formatterLlm.formatPolicyCreationError(userMessage, "Invalid form data submitted.");
            return new ChatResponse(response, IntentCategory.POLICY_CREATION.name(), "system_error", LocalDateTime.now().toString());
        }
    }

    private QueryResult handlePolicyCreation(String userMessage, RouterResult router) {
        try {
            // 1. Resolve Customer
            String nic = router.nic() != null ? router.nic() : "UNK-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            CustomerDTO customer = customerClient.getByNic(nic).orElseGet(() -> {
                String[] names = router.customerName() != null ? router.customerName().split(" ", 2) : new String[]{"New", "Customer"};
                CustomerDTO newCust = new CustomerDTO(null, nic, names[0], names.length > 1 ? names[1] : "", "unknown@email.com", router.phoneNumber(), "Unknown Address", null);
                return customerClient.createCustomer(newCust);
            });

            // 2. Resolve Vehicle
            String reg = router.vehicleRegistration() != null ? router.vehicleRegistration() : "REG-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            VehicleDTO vehicle = vehicleClient.getByRegistration(reg).orElseGet(() -> {
                VehicleDTO newVeh = new VehicleDTO(null, reg, router.vehicleMake(), "Model Phase", 2024, "ENG", "CHAS", "Petrol", "Car");
                return vehicleClient.createVehicle(newVeh);
            });

            // 3. Resolve Covers
            List<Long> coverIds = new java.util.ArrayList<>();
            if (router.requestedCovers() != null && !router.requestedCovers().isEmpty()) {
                List<CoverDTO> allCovers = coverClient.getAll();
                for (String reqCover : router.requestedCovers()) {
                    allCovers.stream()
                             .filter(c -> c.coverName().toLowerCase().contains(reqCover.toLowerCase()) || 
                                          reqCover.toLowerCase().contains(c.coverName().toLowerCase()))
                             .findFirst()
                             .ifPresent(c -> coverIds.add(c.id()));
                }
            }

            // 4. Create Policy
            PolicyCreationRequest creationReq = new PolicyCreationRequest(customer.id(), vehicle.id(), coverIds);
            PolicyDTO newPolicy = policyClient.createPolicy(creationReq);

            // Fetch fully aggregated for rich formatting
            AggregatedPolicyResponse fullData = aggregatorService.aggregateByPolicyNumber(newPolicy.policyNumber()).orElseThrow();
            return new QueryResult(formatterLlm.formatPolicyCreationSuccess(userMessage, fullData), "policy_db + customer_db + vehicle_db");

        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest e) {
            String errorMsg = "Policy creation rejected.";
            try {
                com.fasterxml.jackson.databind.JsonNode errNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getResponseBodyAsString());
                if (errNode.has("error")) errorMsg = errNode.get("error").asText();
            } catch (Exception parseEx) { }
            return new QueryResult(formatterLlm.formatPolicyCreationError(userMessage, errorMsg), "backend_validation");
        } catch (Exception e) {
            logger.error("Error creating policy", e);
            return new QueryResult(formatterLlm.formatPolicyCreationError(userMessage, "An unexpected error occurred during creation."), "system_error");
        }
    }

    private String buildSearchInfo(RouterResult router) {
        StringBuilder sb = new StringBuilder("Searched with: ");
        if (router.policyNumber() != null) sb.append("Policy Number=").append(router.policyNumber()).append(" ");
        if (router.nic() != null) sb.append("NIC=").append(router.nic()).append(" ");
        if (router.phoneNumber() != null) sb.append("Phone=").append(router.phoneNumber()).append(" ");
        return sb.toString().trim();
    }

    private record QueryResult(String response, String dataSource) {}
}
