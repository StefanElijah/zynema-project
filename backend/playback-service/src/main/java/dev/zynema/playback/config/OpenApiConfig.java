package dev.zynema.playback.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI playbackOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Zynema Playback API")
            .version("v1")
            .description("""
                Playback sessions: start, heartbeat and end.

                The number of concurrent open sessions is limited by the plan's
                `maxStreams`, resolved from payment-service; without a
                subscription the free tier allows one.
                """)
            .contact(new Contact().name("Zynema"))
            .license(new License().name("Private")));
    }
}
