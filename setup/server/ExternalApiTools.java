package com.example.chat.tools;

import com.example.chat.web.SseEvents;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * @Tool methods the model can chain together (customer -> pricing ->
 * inventory, etc). Each takes an extra ToolContext parameter, which Spring AI
 * strips from the JSON schema sent to the model -- it's a side channel only,
 * used here to push "calling X" progress events back through the SSE stream
 * while the call executes synchronously inside the tool-calling loop.
 *
 * app.external-apis.base-url defaults to the bundled mock controller
 * (MockExternalApiController) so this app runs end-to-end out of the box.
 * Point it at real services for production.
 */
@Component
public class ExternalApiTools {

    public static final String PROGRESS_SINK_KEY = "progressSink";

    private final RestClient restClient;

    public ExternalApiTools(@Value("${app.external-apis.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    @Tool(description = "Look up customer details by customer id")
    public Map<String, Object> getCustomer(String customerId, ToolContext toolContext) {
        return call(toolContext, "Customer API", "/customers/" + customerId);
    }

    @Tool(description = "Fetch current pricing for a SKU")
    public Map<String, Object> getPricing(String sku, ToolContext toolContext) {
        return call(toolContext, "Pricing API", "/pricing/" + sku);
    }

    @Tool(description = "Fetch current inventory level for a SKU")
    public Map<String, Object> getInventory(String sku, ToolContext toolContext) {
        return call(toolContext, "Inventory API", "/inventory/" + sku);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(ToolContext toolContext, String toolName, String path) {
        emit(toolContext, toolName, "start");
        try {
            return restClient.get().uri(path).retrieve().body(Map.class);
        } finally {
            emit(toolContext, toolName, "done");
        }
    }

    @SuppressWarnings("unchecked")
    private void emit(ToolContext toolContext, String toolName, String status) {
        Object raw = toolContext.getContext().get(PROGRESS_SINK_KEY);
        if (raw instanceof Sinks.Many<?> sink) {
            ((Sinks.Many<ServerSentEvent<Object>>) sink).tryEmitNext(SseEvents.tool(toolName, status));
        }
    }
}
