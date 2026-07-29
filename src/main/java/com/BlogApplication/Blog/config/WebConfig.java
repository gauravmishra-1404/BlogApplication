package com.BlogApplication.Blog.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Serves whatever LocalImageStorageService writes to ./uploads/ at /uploads/** - only needed
// when running without real Cloudinary credentials (local dev / cloudinary.enabled=false).
// SecurityConfig also needs to permitAll this same path.
@Configuration
@ConditionalOnProperty(prefix = "cloudinary", name = "enabled", havingValue = "false", matchIfMissing = true)
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}
