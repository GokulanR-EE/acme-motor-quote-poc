package com.acme.platform.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides an {@link ObjectMapper} used to serialize event payloads to the
 * SSE/WS wire format. Declared explicitly so it is available regardless of
 * which Jackson auto-configuration is active.
 */
@Configuration
public class JsonConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
