package dev.zynema.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Liveness controller — used to verify the service is up.
 * Real endpoints are added in subsequent phases.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/_ping")
    public Map<String, Object> ping() {
        return Map.of(
            "service", "user-service",
            "status", "UP",
            "time", Instant.now().toString()
        );
    }
}
