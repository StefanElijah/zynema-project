package dev.zynema.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Zynema User API")
            .version("v1")
            .description("""
                Accounts, viewer profiles, watchlist and watch progress.

                Open in Phase 2. From Phase 3 every endpoint requires a Keycloak
                JWT and is scoped to the account in the path.
                """)
            .contact(new Contact().name("Zynema"))
            .license(new License().name("Private")));
    }
}
