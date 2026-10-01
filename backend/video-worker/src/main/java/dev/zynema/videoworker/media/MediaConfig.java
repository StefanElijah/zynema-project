package dev.zynema.videoworker.media;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The one production binding of the FFmpeg seam. */
@Configuration
public class MediaConfig {

    @Bean
    public ProcessRunner processRunner() {
        return ProcessRunner.system();
    }
}
