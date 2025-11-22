package com.aria.api.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to forward all non-API routes to React's index.html
 * This ensures that refreshing any page works correctly with React Router
 */
@Controller
public class ReactController {
    
    /**
     * Catch-all for React Router routes
     * This will be matched last (after all API routes)
     */
    @GetMapping(value = {
        "/",
        "/targets",
        "/platforms",
        "/conversations/**",
        "/analysis",
        "/analysis/**",
        "/payments",
        "/api-keys",
        "/settings",
        "/login"
    })
    public String index() {
        return "forward:/index.html";
    }
}

