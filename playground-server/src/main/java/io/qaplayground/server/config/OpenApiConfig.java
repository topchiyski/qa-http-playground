package io.qaplayground.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The title, description and license at the top of Swagger UI ({@code /swagger-ui}).
 */
@Configuration
public class OpenApiConfig {

    /**
     * The API description that Swagger UI and {@code /v3/api-docs} show. The endpoints themselves come from the
     * controllers' annotations.
     */
    @Bean
    public OpenAPI playgroundOpenApi() {
        return new OpenAPI().info(new Info()
                .title("qa-http-playground")
                .version("v1")
                .description("""
                        A standalone, self-hosted, httpbin-compatible HTTP API for learning API testing and \
                        load testing. Every endpoint below behaves like its go-httpbin counterpart; \
                        see docs/COMPATIBILITY.md for the few deviations.

                        **Local and lab networks only — do not expose this server to the internet.**""")
                .license(new License().name("MIT").url("https://opensource.org/license/mit")));
    }
}
