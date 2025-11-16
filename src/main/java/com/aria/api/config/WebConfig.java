package com.aria.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration to serve React frontend static files
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Try external directory first (Docker: /app/static/), then fallback to classpath
        String externalStaticPath = "file:/app/static/";
        String classpathStaticPath = "classpath:/static/";
        
        // Serve React static assets (CSS, JS, images from build/static/)
        registry.addResourceHandler("/static/**")
            .addResourceLocations(externalStaticPath + "static/", classpathStaticPath + "static/");

        // Serve React build files (JS, CSS, JSON, images in root)
        registry.addResourceHandler("/*.js", "/*.js.map", "/*.css", "/*.css.map", "/*.json", "/*.ico", "/*.png", "/*.jpg", "/*.svg", "/*.woff", "/*.woff2")
            .addResourceLocations(externalStaticPath, classpathStaticPath);

        // Serve React index.html and all other non-API routes
        // Note: This should NOT match /api/** routes (handled by Spring controllers)
        registry.addResourceHandler("/**")
            .addResourceLocations(externalStaticPath, classpathStaticPath)
            .resourceChain(true);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward all non-API requests to React index.html
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/targets").setViewName("forward:/index.html");
        registry.addViewController("/platforms").setViewName("forward:/index.html");
        registry.addViewController("/conversations").setViewName("forward:/index.html");
    }
}

