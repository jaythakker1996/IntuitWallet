package com.intuit.walletservice.service;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI walletOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Intuit Wallet Service")
                .version("0.1.0")
                .description("POC wallet service. See ADR 001 + ADR 002 for scope and the User-domain caveat."));
    }
}
