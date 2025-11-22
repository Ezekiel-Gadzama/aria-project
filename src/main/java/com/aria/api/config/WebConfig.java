package com.aria.api.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

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
            .resourceChain(true)
            .addResolver(new ResourceResolver() {
                @Override
                public Resource resolveResource(HttpServletRequest request, String requestPath,
                        List<? extends Resource> locations, ResourceResolverChain chain) {
                    Resource resource = chain.resolveResource(request, requestPath, locations);
                    // If resource not found and it's not an API route, serve index.html
                    if (resource == null && !requestPath.startsWith("api/") && !request.getRequestURI().startsWith("/api/")) {
                        try {
                            return chain.resolveResource(request, "index.html", locations);
                        } catch (Exception e) {
                            return null;
                        }
                    }
                    return resource;
                }

                @Override
                public String resolveUrlPath(String resourcePath, List<? extends Resource> locations,
                        ResourceResolverChain chain) {
                    return chain.resolveUrlPath(resourcePath, locations);
                }
            });
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward all non-API requests to React index.html for client-side routing
        // This ensures that refreshing any page (like /settings, /analysis, etc.) works correctly
        // The ReactController handles specific routes, and the resource handler with /** catches everything else
        registry.setOrder(1); // Lower priority so API routes are matched first
    }

    /**
     * Configure multipart file upload settings (up to 200 MB)
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        // Allow up to 200 MB per file
        factory.setMaxFileSize(DataSize.ofMegabytes(200));
        // Allow up to 200 MB per request (file + form data)
        factory.setMaxRequestSize(DataSize.ofMegabytes(200));
        // Files larger than 10 MB will be written to disk during upload
        factory.setFileSizeThreshold(DataSize.ofMegabytes(10));
        return factory.createMultipartConfig();
    }
}

