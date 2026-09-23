package dev.zynema.catalog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI catalogOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Zynema Catalog API")
            .version("v1")
            .description("""
                Movies, series, seasons, episodes, credits and genres.

                Read endpoints are anonymous in Phase 2. Write endpoints under
                `/api/v1/catalog/admin/**` are admin-only from Phase 3.
                """)
            .contact(new Contact().name("Zynema"))
            .license(new License().name("Private")));
    }
}
