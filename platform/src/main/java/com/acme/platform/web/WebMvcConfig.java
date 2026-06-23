package com.acme.platform.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the repo's vanilla-JS dashboard ({@code dashboard/}) same-origin at
 * {@code /dashboard} (brief §14) so it can use the SSE/WS channel without CORS.
 *
 * <p>The directory is resolved relative to the process working directory (which
 * is {@code platform/} when launched via {@code ./mvnw spring-boot:run} or the
 * jar), defaulting to {@code ../dashboard}. Override with the
 * {@code platform.dashboard-dir} property. Guarded: if the directory is absent
 * the handler is simply not registered, so the app still boots.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebMvcConfig.class);

    private final String dashboardDir;

    public WebMvcConfig(@Value("${platform.dashboard-dir:../dashboard}") String dashboardDir) {
        this.dashboardDir = dashboardDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(dashboardDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            log.warn("Dashboard directory not found at {} — /dashboard will not be served", dir);
            return;
        }
        String location = dir.toUri().toString();
        // Assets resolve under /dashboard/**; a directory request (/dashboard or
        // /dashboard/) falls back to index.html — mirroring the Python
        // StaticFiles(html=True) behaviour the dashboard relies on.
        registry.addResourceHandler("/dashboard", "/dashboard/", "/dashboard/**")
            .addResourceLocations(location)
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String resourcePath, Resource loc) throws java.io.IOException {
                    Resource requested = (resourcePath == null || resourcePath.isEmpty())
                        ? null
                        : loc.createRelative(resourcePath);
                    if (requested != null && requested.exists() && requested.isReadable()) {
                        return requested;
                    }
                    // Directory / unknown path → serve the dashboard's index.html.
                    Resource index = loc.createRelative("index.html");
                    return (index.exists() && index.isReadable()) ? index : null;
                }
            });
        log.info("Serving dashboard from {} at /dashboard", dir);
    }
}
