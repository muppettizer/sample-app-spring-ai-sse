package com.example.chat.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRequest(
        @NotBlank String sessionId,
        @NotBlank String message,
        @NotNull PromptType type,
        GridContext gridContext // required only when type == GRID_UPDATE
) {}
