package com.example.chat.web;

import com.example.chat.domain.ChatRequest;
import com.example.chat.handler.PromptHandler;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
public class ChatController {

    private final List<PromptHandler> handlers;

    public ChatController(List<PromptHandler> handlers) {
        this.handlers = handlers;
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody ChatRequest req) {
        return handlers.stream()
                .filter(h -> h.supports(req.type()))
                .findFirst()
                .map(h -> h.handle(req))
                .orElseGet(() -> Flux.just(SseEvents.error("No handler for prompt type " + req.type())));
    }
}
