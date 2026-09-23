package com.example.chat.handler;

import com.example.chat.domain.ChatRequest;
import com.example.chat.domain.PromptType;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface PromptHandler {
    boolean supports(PromptType type);
    Flux<ServerSentEvent<Object>> handle(ChatRequest req);
}
