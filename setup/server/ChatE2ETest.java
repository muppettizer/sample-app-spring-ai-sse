package com.example.chat;

import com.example.chat.domain.ChatRequest;
import com.example.chat.domain.GridContext;
import com.example.chat.domain.PromptType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.reactive.server.WebTestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A genuine end-to-end test: real Redis (via Testcontainers), the real
 * ChatController/SSE pipeline, and a real call to Gemini -- no mocked
 * ChatModel. Requires GOOGLE_API_KEY to be set in the environment; skipped
 * otherwise so the build doesn't fail for contributors without a key.
 *
 * Run with: GOOGLE_API_KEY=xxx ./mvnw test -Dtest=ChatE2ETest
 */
// Fixed port (not RANDOM_PORT): ExternalApiTools calls the app's own
// bundled MockExternalApiController, and its base-url has to be known at
// bean-creation time, before a random port would be assigned.
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=8099",
                "app.external-apis.base-url=http://localhost:8099/mock"
        }
)
@EnabledIfEnvironmentVariable(named = "GOOGLE_API_KEY", matches = ".+")
class ChatE2ETest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.ai.chat.memory.repository.redis.host", redis::getHost);
        registry.add("spring.ai.chat.memory.repository.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    WebTestClient webTestClient;

    private static final ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>> SSE_TYPE =
            new ParameterizedTypeReference<>() {};

    @BeforeAll
    static void checkRedisRunning() {
        assertThat(redis.isRunning()).isTrue();
    }

    @Test
    void toolChainPromptCallsExternalApisAndStreamsAnAnswer() {
        ChatRequest req = new ChatRequest(
                "e2e-session-1",
                "Look up customer C123, then get pricing and inventory for SKU ABC-1. Summarize in one sentence.",
                PromptType.TOOL_CHAIN,
                null
        );

        List<ServerSentEvent<Map<String, Object>>> events = webTestClient.post()
                .uri("/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SSE_TYPE)
                .getResponseBody()
                .timeout(Duration.ofSeconds(60))
                .collectList()
                .block(Duration.ofSeconds(65));

        assertThat(events).isNotNull().isNotEmpty();

        List<String> eventNames = events.stream().map(ServerSentEvent::event).toList();

        // The chain should report progress for at least the tools it needed...
        assertThat(eventNames).contains("tool");
        // ...produce actual model text...
        assertThat(eventNames).contains("message");
        // ...and terminate cleanly.
        assertThat(eventNames).contains("done");

        String fullText = events.stream()
                .filter(e -> "message".equals(e.event()))
                .map(e -> String.valueOf(e.data().get("text")))
                .reduce("", String::concat);
        assertThat(fullText).isNotBlank();
    }

    @Test
    void gridUpdatePromptDrivesGridToolsFromSchemaAndState() {
        GridContext gridContext = new GridContext(
                "[{\"field\":\"status\"},{\"field\":\"amount\"}]",
                "{\"filterModel\":{},\"sortModel\":[]}",
                "{\"id\":42,\"status\":\"pending\"}"
        );

        ChatRequest req = new ChatRequest(
                "e2e-session-2",
                "Filter the status column to only show rows where status equals pending",
                PromptType.GRID_UPDATE,
                gridContext
        );

        List<ServerSentEvent<Map<String, Object>>> events = webTestClient.post()
                .uri("/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SSE_TYPE)
                .getResponseBody()
                .timeout(Duration.ofSeconds(60))
                .collectList()
                .block(Duration.ofSeconds(65));

        assertThat(events).isNotNull().isNotEmpty();
        List<String> eventNames = events.stream().map(ServerSentEvent::event).toList();

        assertThat(eventNames).contains("grid");
        assertThat(eventNames).contains("done");

        boolean hasFilterCommand = events.stream()
                .filter(e -> "grid".equals(e.event()))
                .anyMatch(e -> "applyFilter".equals(e.data().get("action")));
        assertThat(hasFilterCommand).isTrue();
    }

    @Test
    void chatMemoryPersistsAcrossTurnsInRedis() {
        String sessionId = "e2e-session-memory";

        ChatRequest first = new ChatRequest(sessionId, "My name is Priya. Remember that.", PromptType.TOOL_CHAIN, null);
        drain(first);

        ChatRequest second = new ChatRequest(sessionId, "What is my name?", PromptType.TOOL_CHAIN, null);
        List<ServerSentEvent<Map<String, Object>>> events = drain(second);

        String fullText = events.stream()
                .filter(e -> "message".equals(e.event()))
                .map(e -> String.valueOf(e.data().get("text")))
                .reduce("", String::concat);

        assertThat(fullText.toLowerCase()).contains("priya");
    }

    private List<ServerSentEvent<Map<String, Object>>> drain(ChatRequest req) {
        Flux<ServerSentEvent<Map<String, Object>>> body = webTestClient.post()
                .uri("/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SSE_TYPE)
                .getResponseBody();

        return body.timeout(Duration.ofSeconds(60)).collectList().block(Duration.ofSeconds(65));
    }
}
