package com.acme.platform.quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the stable demo-quote self-seed (brief §9) as a startup runner. Kept in
 * its own {@code @Configuration} (rather than on the application class) so the
 * JPA-slice tests ({@code @DataJpaTest}) don't try to load the runner and its
 * {@link DemoSeeder} dependency, which live outside that slice.
 */
@Configuration
public class DemoSeederConfig {

    private static final Logger log = LoggerFactory.getLogger(DemoSeederConfig.class);

    /**
     * Self-seed the stable demo quote on startup so it is always resolvable. Seed
     * failures are logged, not fatal — under the {@code live} vendor seam the
     * (unimplemented) stub throws while pricing the sample, so the app still boots
     * to demonstrate the seam rather than crashing on startup.
     */
    @Bean
    CommandLineRunner seedDemo(DemoSeeder demo) {
        return args -> {
            try {
                demo.ensureSeeded();
            } catch (RuntimeException e) {
                log.warn("Demo quote seeding skipped: {}", e.getMessage());
            }
        };
    }
}
