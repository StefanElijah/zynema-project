package dev.zynema.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Marker auto-configuration for zynema-common.
 *
 * The real registrations live in the two sibling auto-configurations, which are
 * conditional on the web application type (servlet vs reactive). Keeping them
 * separate mirrors how Spring Boot itself structures web auto-configurations and
 * avoids loading WebFlux machinery into pure-MVC services (and vice versa).
 */
@AutoConfiguration
public class CommonAutoConfiguration {
    // Intentionally empty. See CommonServletAutoConfiguration and
    // CommonReactiveAutoConfiguration for the conditional bean registrations.
}
