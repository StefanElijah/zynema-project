package dev.zynema.bff.controller;

import dev.zynema.bff.dto.HomeView;
import dev.zynema.bff.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The landing screen of the frontend. Public, like the catalogue it shows.
 */
@RestController
@RequestMapping("/api/v1/web")
@RequiredArgsConstructor
@Tag(name = "Web BFF", description = "Aggregated views for the SPA")
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/home")
    @Operation(summary = "Hero and rails for the landing screen")
    public Mono<HomeView> home() {
        return homeService.home();
    }
}
