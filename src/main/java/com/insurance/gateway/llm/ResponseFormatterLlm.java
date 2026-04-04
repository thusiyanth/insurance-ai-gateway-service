package com.insurance.gateway.llm;

import com.insurance.common.dto.AggregatedPolicyResponse;
import com.insurance.common.dto.CustomerDTO;
import com.insurance.common.dto.CoverDTO;
import com.insurance.common.dto.VehicleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ResponseFormatterLlm {

    private static final Logger logger = LoggerFactory.getLogger(ResponseFormatterLlm.class);

    private final ChatClient chatClient;

    // NOTE: Data formatting prompts removed — all data reports are now built directly in Java.
    // Only WIZARD_PROMPT and GENERAL_PROMPT remain as they still use templates/LLM.

    private static final String WIZARD_PROMPT = """
            {
            "formType": "policy_creation_wizard",
            "currentStep": 1,
            "steps": [
            {
            "step": 1,
            "title": "Customer Details",
            "type": "customer",
            "fields": [
            {"name": "name", "label": "Full Name", "type": "text", "required": true},
            {"name": "nic", "label": "NIC", "type": "text", "required": true},
            {"name": "phone", "label": "Phone", "type": "text"},
            {"name": "email", "label": "Email", "type": "email"}
            ],
            "action": "next"
            },
            {
            "step": 2,
            "title": "Vehicle Details",
            "type": "vehicle",
            "fields": [
            {"name": "vehicle_number", "label": "Vehicle Number", "type": "text", "required": true},
            {"name": "brand", "label": "Brand", "type": "text"},
            {"name": "model", "label": "Model", "type": "text"},
            {"name": "fuel_type", "label": "Fuel Type", "type": "dropdown", "options": ["Petrol", "Diesel", "Hybrid"]}
            ],
            "action": "next"
            },
            {
            "step": 3,
            "title": "Select Covers",
            "type": "cover",
            "fields": [
            {
            "name": "covers",
            "label": "Choose Covers",
            "type": "multiselect",
            "options": %COVERS_JSON%
            }
            ],
            "action": "next"
            },
            {
            "step": 4,
            "title": "Premium Summary",
            "type": "summary",
            "fields": [
            {"label": "Base Premium", "value": "auto_calculated"},
            {"label": "Covers Total", "value": "auto_calculated"},
            {"label": "Total Premium", "value": "auto_calculated"}
            ],
            "action": "next"
            },
            {
            "step": 5,
            "title": "Create Policy",
            "type": "policy",
            "fields": [],
            "action": "submit"
            }
            ]
            }
            """;

    private static final String GENERAL_PROMPT = """
            You are an AI assistant for an insurance system.
            For general greetings or off-topic queries, provide a warm, professional, conversational response.
            IMPORTANT: Provide your response in plain text only. Do NOT use JSON format.
            """;

    public ResponseFormatterLlm(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // ==================== DIRECT JSON BUILDERS (No LLM dependency) ====================

    private void addItem(com.fasterxml.jackson.databind.node.ArrayNode items, String label, Object value) {
        if (value == null || value.toString().isBlank() || "null".equals(value.toString())) return;
        var mapper = items.arrayNode().numberNode(0); // just to get access, unused
        var item = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        item.put("label", label);
        item.put("value", value.toString());
        items.add(item);
    }

    private String buildReportJson(String title, String subtitle, String footer,
                                    java.util.List<java.util.Map.Entry<String, java.util.List<String[]>>> sections) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var report = mapper.createObjectNode();
            report.put("layout", "report");
            report.put("title", title);
            report.put("subtitle", subtitle);

            var sectionsArray = report.putArray("sections");
            for (var section : sections) {
                var sectionNode = sectionsArray.addObject();
                sectionNode.put("heading", section.getKey());
                var itemsArray = sectionNode.putArray("items");
                for (String[] pair : section.getValue()) {
                    if (pair[1] != null && !pair[1].isBlank() && !"null".equals(pair[1])) {
                        var itemNode = itemsArray.addObject();
                        itemNode.put("label", pair[0]);
                        itemNode.put("value", pair[1]);
                        // Optional description (3rd element)
                        if (pair.length > 2 && pair[2] != null && !pair[2].isBlank()) {
                            itemNode.put("description", pair[2]);
                        }
                    }
                }
            }

            report.put("footer", footer);
            return mapper.writeValueAsString(report);
        } catch (Exception e) {
            logger.error("Failed to build report JSON", e);
            return "{\"layout\":\"report\",\"title\":\"Error\",\"subtitle\":\"\",\"sections\":[],\"footer\":\"Failed to generate report\"}";
        }
    }

    /**
     * Build a multi-policy "stack" response.
     * Returns JSON: {"layout": "stack", "pages": [report1, report2, ...], "total": N}
     */
    public String formatMultiPolicyResponse(String userQuery, java.util.List<AggregatedPolicyResponse> allPolicies) {
        logger.info("Building multi-policy stack JSON for {} policies", allPolicies.size());
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var stack = mapper.createObjectNode();
            stack.put("layout", "stack");
            stack.put("total", allPolicies.size());

            var pagesArray = stack.putArray("pages");
            for (AggregatedPolicyResponse data : allPolicies) {
                // Build each page's report JSON as a node
                String singleReport = formatPolicyResponse(userQuery, data);
                var reportNode = mapper.readTree(singleReport);
                pagesArray.add(reportNode);
            }

            return mapper.writeValueAsString(stack);
        } catch (Exception e) {
            logger.error("Failed to build multi-policy stack JSON", e);
            // Fallback: just return the first one
            return formatPolicyResponse(userQuery, allPolicies.get(0));
        }
    }

    public String formatPolicyResponse(String userQuery, AggregatedPolicyResponse data) {
        logger.info("Building policy report JSON directly");

        var sections = new java.util.ArrayList<java.util.Map.Entry<String, java.util.List<String[]>>>();

        // Policy Section
        if (data.policy() != null) {
            var p = data.policy();
            var items = java.util.List.of(
                new String[]{"Policy Number", p.policyNumber()},
                new String[]{"Type", p.policyType()},
                new String[]{"Status", p.status()},
                new String[]{"Start Date", p.startDate() != null ? p.startDate().toString() : "N/A"},
                new String[]{"End Date", p.endDate() != null ? p.endDate().toString() : "N/A"},
                new String[]{"Premium Amount", p.premiumAmount() != null ? "LKR " + p.premiumAmount() : "N/A"}
            );
            sections.add(java.util.Map.entry("Policy Information", items));
        }

        // Customer Section
        if (data.customer() != null) {
            var c = data.customer();
            var items = java.util.List.of(
                new String[]{"Name", c.firstName() + " " + c.lastName()},
                new String[]{"NIC", c.nic()},
                new String[]{"Phone", c.phone()},
                new String[]{"Email", c.email()}
            );
            sections.add(java.util.Map.entry("Customer Details", items));
        }

        // Vehicle Section
        if (data.vehicle() != null) {
            var v = data.vehicle();
            var items = java.util.List.of(
                new String[]{"Registration", v.registrationNumber()},
                new String[]{"Make / Model", v.make() + " " + v.model() + " (" + v.year() + ")"},
                new String[]{"Fuel Type", v.fuelType()}
            );
            sections.add(java.util.Map.entry("Vehicle Details", items));
        }

        // Coverage Section
        if (data.covers() != null && !data.covers().isEmpty()) {
            var items = new java.util.ArrayList<String[]>();
            for (CoverDTO cover : data.covers()) {
                String coverName = cover.coverName();
                String sumVal = "LKR " + (cover.sumInsured() != null ? String.format("%,.2f", cover.sumInsured()) : "0.00");
                String desc = cover.deductible() != null && cover.deductible().compareTo(java.math.BigDecimal.ZERO) > 0
                    ? "Deductible: LKR " + String.format("%,.2f", cover.deductible())
                    : "No deductible";
                items.add(new String[]{coverName, sumVal, desc});
            }
            sections.add(java.util.Map.entry("Coverage Details", items));
        }

        String subtitle = data.policy() != null ? "Policy #" + data.policy().policyNumber() : "Policy Statement";
        String footer = data.policy() != null ? "Status: " + data.policy().status() : "Insurance AI System";
        return buildReportJson("Policy Statement", subtitle, footer, sections);
    }

    public String formatVehicleResponse(String userQuery, VehicleDTO v) {
        logger.info("Building vehicle report JSON directly");
        var items = java.util.List.of(
            new String[]{"Registration Number", v.registrationNumber()},
            new String[]{"Make", v.make()},
            new String[]{"Model", v.model()},
            new String[]{"Year", String.valueOf(v.year())},
            new String[]{"Engine Number", v.engineNumber()},
            new String[]{"Chassis Number", v.chassisNumber()},
            new String[]{"Fuel Type", v.fuelType()},
            new String[]{"Vehicle Class", v.vehicleClass()}
        );
        var sections = java.util.List.of(
            java.util.Map.entry("Vehicle Specifications", items)
        );
        return buildReportJson("Vehicle Identity Report", "Reg #" + v.registrationNumber(), "Vehicle Record Verified", sections);
    }

    public String formatCustomerResponse(String userQuery, CustomerDTO c) {
        logger.info("Building customer report JSON directly");
        var items = java.util.List.of(
            new String[]{"Full Name", c.firstName() + " " + c.lastName()},
            new String[]{"NIC", c.nic()},
            new String[]{"Phone", c.phone()},
            new String[]{"Email", c.email()},
            new String[]{"Address", c.address()},
            new String[]{"Date of Birth", c.dateOfBirth() != null ? c.dateOfBirth().toString() : "N/A"}
        );
        var sections = java.util.List.of(
            java.util.Map.entry("Personal Information", items)
        );
        return buildReportJson("Customer Profile", c.firstName() + " " + c.lastName(), "Customer Record Verified", sections);
    }

    public String formatCoverResponse(String userQuery, List<CoverDTO> covers) {
        logger.info("Building cover report JSON directly");
        var items = new java.util.ArrayList<String[]>();
        for (CoverDTO c : covers) {
            String coverName = c.coverName() + " (" + c.coverCode() + ")";
            String sumInsured = "LKR " + (c.maxLimit() != null ? String.format("%,.2f", c.maxLimit()) : "0.00");
            String description = c.description() != null ? c.description() : "Insurance coverage";
            items.add(new String[]{coverName, sumInsured, description});
        }
        var sections = java.util.List.of(
            java.util.Map.entry("Available Covers", (java.util.List<String[]>) items)
        );
        return buildReportJson("Coverage Entitlement Statement", "Full Coverage Details", "All covers shown with maximum limits", sections);
    }

    public String formatKnowledgeResponse(String userQuery, String dataContext) {
        String knowledgePrompt = """
                You are an expert insurance assistant.
                The user is asking a general question about insurance concepts or covers.
                Provide a warm, professional, and clear conversational summary.
                Use bullet points for clarity if needed, but keep it as friendly text.
                DO NOT use JSON. DO NOT use the "Report" format.
                """;
        return callLlm(knowledgePrompt, userQuery, dataContext);
    }

    public String formatPolicyCreationSuccess(String userQuery, AggregatedPolicyResponse data) {
        logger.info("Building policy creation success report JSON directly");
        var sections = new java.util.ArrayList<java.util.Map.Entry<String, java.util.List<String[]>>>();

        if (data.policy() != null) {
            var p = data.policy();
            var items = java.util.List.of(
                new String[]{"Policy Number", p.policyNumber()},
                new String[]{"Type", p.policyType()},
                new String[]{"Status", p.status()},
                new String[]{"Start Date", p.startDate() != null ? p.startDate().toString() : "N/A"},
                new String[]{"End Date", p.endDate() != null ? p.endDate().toString() : "N/A"},
                new String[]{"Premium Amount", p.premiumAmount() != null ? "LKR " + p.premiumAmount() : "N/A"}
            );
            sections.add(java.util.Map.entry("Policy Info", items));
        }

        if (data.customer() != null) {
            var c = data.customer();
            var items = java.util.List.of(
                new String[]{"Name", c.firstName() + " " + c.lastName()},
                new String[]{"NIC", c.nic()},
                new String[]{"Phone", c.phone()}
            );
            sections.add(java.util.Map.entry("Insured Person", items));
        }

        if (data.vehicle() != null) {
            var v = data.vehicle();
            var items = java.util.List.of(
                new String[]{"Registration", v.registrationNumber()},
                new String[]{"Make / Model", v.make() + " " + v.model()},
                new String[]{"Fuel Type", v.fuelType()}
            );
            sections.add(java.util.Map.entry("Vehicle Coverage", items));
        }

        String policyNo = data.policy() != null ? data.policy().policyNumber() : "NEW";
        return buildReportJson("Official Policy Receipt", "Policy #" + policyNo + " — Issued Successfully",
                "Policy Issued • Thank you for choosing Test Insurance", sections);
    }

    public String formatPolicyCreationError(String userQuery, String errorReason) {
        var items = java.util.List.of(
            new String[]{"Error", errorReason},
            new String[]{"Action Required", "Please review your submission and try again"}
        );
        var sections = java.util.List.of(
            java.util.Map.entry("Rejection Details", items)
        );
        return buildReportJson("Rejection Notice", "Submission Failed", "Contact support if this issue persists", sections);
    }

    public String formatPolicyWizard(List<CoverDTO> covers) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var optionsNode = mapper.createArrayNode();
            if (covers != null) {
                for (CoverDTO c : covers) {
                    var obj = mapper.createObjectNode();
                    obj.put("id", c.id());
                    obj.put("name", c.coverName());
                    obj.put("description", c.description() != null && !c.description().isBlank() ? c.description() : "Add extra protection for your vehicle.");
                    obj.put("price", c.sumInsured() != null ? c.sumInsured().longValue() : 0);
                    optionsNode.add(obj);
                }
            }
            String optionsJson = mapper.writeValueAsString(optionsNode);
            return WIZARD_PROMPT.replace("%COVERS_JSON%", optionsJson);
        } catch (Exception e) {
            return WIZARD_PROMPT.replace("%COVERS_JSON%", "[]");
        }
    }

    public String formatGeneralResponse(String userQuery) {
        return callLlm(GENERAL_PROMPT, userQuery, "No specific data available.");
    }

    public String formatNotFoundResponse(String userQuery, String searchDetails) {
        String context = "SEARCH RESULT: No records found. " + searchDetails;
        String fallbackPrompt = "You are an insurance assistant. The user asked a question but no records were found. " +
                                "Explain this politely in plain text. Do NOT use JSON.";
        return callLlm(fallbackPrompt, userQuery, context);
    }

    private String callLlm(String systemPrompt, String userQuery, String context) {
        try {
            String fullUserMessage = String.format("""
                    Customer's Question: %s
                    
                    Context:
                    %s
                    """, userQuery, context);

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(fullUserMessage)
                    .call()
                    .content();

            if (response != null) {
                response = response.trim();
                // Cleanup generic markdown wrapping if any leaked from LLM
                if (response.contains("```")) {
                    response = response.replaceAll("(?s)```json\\s*(.*?)\\s*```", "$1");
                    response = response.replaceAll("(?s)```\\s*(.*?)\\s*```", "$1");
                }
                return response.trim();
            }
            return "I apologize, I'm unable to process your request at this time.";
        } catch (Exception e) {
            logger.error("LLM formatting failed", e);
            return "I apologize for the inconvenience. I'm experiencing technical difficulties.";
        }
    }
}
