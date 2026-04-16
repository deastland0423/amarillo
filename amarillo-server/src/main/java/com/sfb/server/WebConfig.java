package com.sfb.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the web client (Vite dev server on :5173, or any other origin in dev)
 * to call the REST API and WebSocket endpoint. The Java client is unaffected.
 *
 * WebMvcConfigurer.addCorsMappings covers Spring MVC controllers (/api/**).
 * The CorsFilter bean covers everything else at the servlet level, including
 * SockJS HTTP transports (/ws/**) which bypass the MVC dispatcher.
 *
 * In production, replace allowedOriginPatterns("*") with your actual domain.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Player-Token")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true); // SockJS sends withCredentials:true; must echo actual origin

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/ws/**", config);

        return new CorsFilter(source);
    }
}
