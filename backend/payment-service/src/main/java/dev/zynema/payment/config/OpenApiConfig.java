package dev.zynema.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Zynema Payment API")
            .version("v1")
            .description("""
                Plans, subscriptions and payments.

                `POST /api/v1/payments/subscriptions` is idempotent: it requires an
                `Idempotency-Key` header and replays the original response when the
                same key is retried.
                """)
            .contact(new Contact().name("Zynema"))
            .license(new License().name("Private")));
    }
}
