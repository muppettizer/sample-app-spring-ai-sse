package com.example.chat.domain;

public record GridContext(
        String gridSchema,
        String gridState,
        String selectedRow
) {}
