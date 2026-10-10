package com.ironoak.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI docs at /v3/api-docs and Swagger UI at /swagger-ui, split into a "public" group
 * (storefront, checkout, webhook) and an "admin" group (/api/admin/**, /api/dashboard/**).
 * The admin group sends the bearer token from the UI's Authorize button: paste the accessToken
 * returned by POST /api/admin/auth/login. springdoc is off unless enabled (the local profile does).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    private static final String[] ADMIN_PATHS = {"/api/admin/**", "/api/dashboard/**"};

    /** Admin endpoints that take their credential in the body, so they need no bearer token. */
    private static final java.util.Set<String> NO_TOKEN = java.util.Set.of(
            "/api/admin/auth/login", "/api/admin/auth/refresh", "/api/admin/auth/logout");

    @Bean
    public OpenAPI ironOakOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Iron & Oak API")
                        .version("v1")
                        .description("Hardware-store e-commerce backend. Admin endpoints need a JWT: "
                                + "log in with POST /api/admin/auth/login, then use Authorize."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("public")
                .displayName("Public (storefront, checkout)")
                .pathsToMatch("/api/**")
                .pathsToExclude(ADMIN_PATHS)
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("Admin (staff only)")
                .pathsToMatch(ADMIN_PATHS)
                .addOpenApiCustomizer(api -> api.getPaths().forEach((url, path) -> {
                    if (!NO_TOKEN.contains(url)) {
                        path.readOperations()
                                .forEach(op -> op.addSecurityItem(new SecurityRequirement().addList(BEARER)));
                    }
                }))
                .build();
    }
}
