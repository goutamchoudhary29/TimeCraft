package com.timecraft.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class OpenApiAndCacheConfig {

    @Bean
    public OpenAPI timeCraftOpenApi() {
        final String schemeName = "customerBearerJwt";
        return new OpenAPI()
                .info(new Info()
                        .title("TimeCraft Watch E-Commerce API")
                        .version("1.0.0")
                        .description("Customer REST API (/api/v1/**) authenticated with JWT Bearer tokens, "
                                + "and Admin REST API (/api/v1/admin/**) authenticated with ADMIN HTTP session + CSRF.")
                        .contact(new Contact().name("TimeCraft Engineering").email("support@timecraft.example")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
