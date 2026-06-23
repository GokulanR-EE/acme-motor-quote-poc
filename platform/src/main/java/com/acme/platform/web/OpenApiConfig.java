package com.acme.platform.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * springdoc OpenAPI metadata. Exposes {@code /v3/api-docs} and the swagger-ui
 * for the platform's REST surface.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI platformOpenApi() {
        return new OpenAPI().info(new Info()
            .title("ACME Mock Motor Quote Platform")
            .description("Mock insurer platform — REST + event contract (Java/Spring Boot).")
            .version("0.0.1"));
    }
}
