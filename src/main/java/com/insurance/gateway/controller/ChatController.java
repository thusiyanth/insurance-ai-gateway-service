package com.insurance.gateway.controller;

import com.insurance.common.dto.ChatRequest;
import com.insurance.common.dto.ChatResponse;
import com.insurance.gateway.service.ChatOrchestrator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
//@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private final ChatOrchestrator chatOrchestrator;

    /**
     * Main chat endpoint — receives user message and returns AI-processed response.
     *
     * POST /api/chat
     * Body: { "message": "What is the status of policy POL-2024-001?", "sessionId": "abc123" }
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        logger.info("Received chat request: {}", request.message());
        ChatResponse response = chatOrchestrator.processMessage(request);
        logger.info("Returning response with intent: {}", response.intent());
        return ResponseEntity.ok(response);
    }

    /**
     * Legacy GET endpoint for backward compatibility with old frontend.
     */
    @GetMapping("/chat")
    public ResponseEntity<Map<String, String>> chatLegacy(@RequestParam(value = "prompt", defaultValue = "Hello!") String prompt) {
        logger.info("Legacy chat request: {}", prompt);
        ChatRequest request = new ChatRequest(prompt, null);
        ChatResponse response = chatOrchestrator.processMessage(request);
        return ResponseEntity.ok(Map.of("response", response.response()));
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "gateway-service",
                "description", "Insurance AI Gateway"
        ));
    }
}
