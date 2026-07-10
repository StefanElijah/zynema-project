package dev.zynema.user;

import dev.zynema.common.exception.GlobalExceptionHandler;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Enables the shared common module's exception handler and any other beans.
 */
@Configuration
@Import(GlobalExceptionHandler.class)
@ComponentScan(basePackages = {
    "dev.zynema.user",
    "dev.zynema.common"
})
public class UserServiceConfig {
}
