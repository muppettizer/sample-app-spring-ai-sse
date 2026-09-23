package com.example.chat.web;

import org.springframework.http.codec.ServerSentEvent;

import java.util.Map;

public final class SseEvents {

    private SseEvents() {}

    public static ServerSentEvent<Object> message(String textChunk) {
        return ServerSentEvent.builder()
                .event("message")
                .data(Map.of("text", textChunk))
                .build();
    }

    public static ServerSentEvent<Object> tool(String toolName, String status) {
        return ServerSentEvent.builder()
                .event("tool")
                .data(Map.of("tool", toolName, "status", status))
                .build();
    }

    public static ServerSentEvent<Object> grid(Object gridCommand) {
        return ServerSentEvent.builder()
                .event("grid")
                .data(gridCommand)
                .build();
    }

    public static ServerSentEvent<Object> done() {
        return ServerSentEvent.builder()
                .event("done")
                .data(Map.of("status", "complete"))
                .build();
    }

    public static ServerSentEvent<Object> error(String message) {
        return ServerSentEvent.builder()
                .event("error")
                .data(Map.of("message", message))
                .build();
    }
}
