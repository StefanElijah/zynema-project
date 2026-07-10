package dev.zynema.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CommonApplication {
    // Library-style module. No main run, but the annotation makes the
    // component scan work when this module is on the classpath of a service.
    public static void main(String[] args) {
        // Intentionally empty — common is a library, not a runnable service.
    }
}
