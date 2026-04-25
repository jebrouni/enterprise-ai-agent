package com.enterprise.aiagent.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration Web MVC — RestTemplate, CORS, etc.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:3000",
                        "http://localhost:4200",
                        "http://localhost:8080",
                        "https://*.votredomaine.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-Request-ID",
                        "X-Correlation-ID", "Accept-Language")
                .exposedHeaders("X-Request-ID", "X-Rate-Limit-Remaining",
                        "X-Processing-Time-Ms")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
