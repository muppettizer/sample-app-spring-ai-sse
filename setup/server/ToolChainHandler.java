package com.example.chat.handler;

import com.example.chat.domain.ChatRequest;
import com.example.chat.domain.PromptType;
import com.example.chat.tools.ExternalApiTools;
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
public class ToolChainHandler implements PromptHandler {

    private final ChatClient chatClient;
    private final ExternalApiTools externalApiTools;

    public ToolChainHandler(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, ExternalApiTools externalApiTools) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.externalApiTools = externalApiTools;
    }

    @Override
    public boolean supports(PromptType type) {
        return type == PromptType.TOOL_CHAIN;
    }

    @Override
    public Flux<ServerSentEvent<Object>> handle(ChatRequest req) {
        Sinks.Many<ServerSentEvent<Object>> progress = Sinks.many().multicast().onBackpressureBuffer();

        Flux<ServerSentEvent<Object>> content = chatClient.prompt()
                .user(req.message())
                .tools(externalApiTools)
                .toolContext(Map.of(ExternalApiTools.PROGRESS_SINK_KEY, progress))
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
