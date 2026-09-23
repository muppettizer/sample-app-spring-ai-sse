package com.example.chat.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Stand-in for the real customer/pricing/inventory services ExternalApiTools
 * calls. app.external-apis.base-url points here by default so the whole app
 * -- including the e2e test -- runs without any external dependencies beyond
 * Gemini and Redis. Delete this once real service URLs are wired in.
 */
@RestController
public class MockExternalApiController {

    @GetMapping("/mock/customers/{id}")
    public Map<String, Object> customer(@PathVariable String id) {
        return Map.of("customerId", id, "name", "Acme Corp", "tier", "gold");
    }

    @GetMapping("/mock/pricing/{sku}")
    public Map<String, Object> pricing(@PathVariable String sku) {
        return Map.of("sku", sku, "listPrice", 49.99, "currency", "USD");
    }

    @GetMapping("/mock/inventory/{sku}")
    public Map<String, Object> inventory(@PathVariable String sku) {
        return Map.of("sku", sku, "quantityOnHand", 128, "warehouse", "EU-WEST-1");
    }
}
