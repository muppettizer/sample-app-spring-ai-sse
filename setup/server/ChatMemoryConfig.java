package com.example.chat.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * spring-ai-starter-model-chat-memory-repository-redis auto-configures a
 * RedisChatMemoryRepository (a ChatMemoryRepository bean) from the
 * spring.ai.chat.memory.repository.redis.* + spring.data.redis.* properties.
 * We just need to wrap it in a windowed ChatMemory so each conversation only
 * carries the last N messages into the prompt.
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public ChatMemory chatMemory(
            ChatMemoryRepository chatMemoryRepository,
            @Value("${app.chat-memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMessages)
                .build();
    }
}
