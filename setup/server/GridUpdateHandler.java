package com.example.chat.handler;

import com.example.chat.domain.ChatRequest;
import com.example.chat.domain.GridContext;
import com.example.chat.domain.PromptType;
import com.example.chat.tools.GridTools;
import com.example.chat.web.SseEvents;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

@Component
public class GridUpdateHandler implements PromptHandler {

    private static final String SYSTEM_TEMPLATE = """
            You control an AG Grid instance. Use the grid tools to express any
            change to filters, sorting, grouping, or cell values -- never
            describe the change in prose only, always call the matching tool.

            Grid schema (column defs):
            %s

            Current grid state (filter model, sort model, row data):
            %s

            Currently selected row:
            %s
            """;

    private final ChatClient chatClient;
    private final GridTools gridTools;

    public GridUpdateHandler(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, GridTools gridTools) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.gridTools = gridTools;
    }

    @Override
    public boolean supports(PromptType type) {
        return type == PromptType.GRID_UPDATE;
    }

    @Override
    public Flux<ServerSentEvent<Object>> handle(ChatRequest req) {
        GridContext ctx = req.gridContext();
        if (ctx == null) {
            return Flux.just(SseEvents.error("GRID_UPDATE prompt requires gridContext"));
        }

        String system = SYSTEM_TEMPLATE.formatted(
                ctx.gridSchema(),
                ctx.gridState(),
                ctx.selectedRow() == null ? "none" : ctx.selectedRow()
        );

        Sinks.Many<ServerSentEvent<Object>> progress = Sinks.many().multicast().onBackpressureBuffer();

        Flux<ServerSentEvent<Object>> content = chatClient.prompt()
                .system(system)
                .user(req.message())
                .tools(gridTools)
                .toolContext(Map.of(GridTools.PROGRESS_SINK_KEY, progress))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, req.sessionId()))
                .stream()
                .content()
                .map(SseEvents::message);

        return Flux.merge(progress.asFlux(), content)
                .concatWith(Flux.just(SseEvents.done()))
                .doFinally(signal -> progress.tryEmitComplete())
                .onErrorResume(ex -> Flux.just(SseEvents.error(ex.getMessage())));
    }
}
