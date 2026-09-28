package dev.zynema.bff.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One spec for the whole frontend.
 *
 * <p>This is the document the SPA's generated client is built from in Fase 8:
 * the frontend depends on these aggregated views, not on the domain services'
 * specs, which are free to change as long as this contract holds.
 */
@Configuration
public class BffOpenApiConfig {

    @Bean
    public OpenAPI bffOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Zynema Web BFF API")
            .version("v1")
            .description("""
                Aggregated views for the single-page application.

                The SPA talks only to `/api/v1/web/**`. Each endpoint composes the
                domain services so a screen is one round trip:

                - `GET /home` — landing page (public)
                - `GET /catalog/{idOrSlug}` — detail plus playback context (public)
                - `GET /account` — identity, account, subscription
                - `GET /profiles/{profileId}/home` — continue watching and my list

                Optional sections that could not be composed are reported in
                `degraded` instead of failing the whole screen.
                """)
            .contact(new Contact().name("Zynema"))
            .license(new License().name("Private")));
    }
}
