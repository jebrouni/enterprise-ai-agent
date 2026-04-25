package com.enterprise.aiagent.infra.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration de l'infrastructure technique.
 */
@Configuration
@EnableCaching
public class InfrastructureConfig {

    @Value("${anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${anthropic.base-url:https://api.anthropic.com}")
    private String anthropicBaseUrl;

    @Value("${anthropic.api-version:2023-06-01}")
    private String anthropicApiVersion;

    // ── WebClient Anthropic ──────────────────────────────────────────────

    @Bean("anthropicWebClient")
    public WebClient anthropicWebClient() {
        return WebClient.builder()
                .baseUrl(anthropicBaseUrl)          // https://api.groq.com/openai
                .defaultHeader("Content-Type",   "application/json")
                .defaultHeader("Authorization",  "Bearer " + anthropicApiKey)
                // ⚠️ Supprimer ces lignes si elles existent encore :
                // .defaultHeader("x-api-key",         anthropicApiKey)
                // .defaultHeader("anthropic-version", anthropicApiVersion)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    // ── Cache Redis — Stratégie par type de données ──────────────────────

    /**
     * Configuration du Cache Manager Redis avec TTL différenciés par cache :
     *
     * - agent-responses   → 5 minutes  (réponses aux questions répétées)
     * - user-permissions  → 10 minutes (rôles Keycloak)
     * - prompt-templates  → 1 heure    (templates de prompts statiques)
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {

        // ← Configurer Jackson pour supporter Instant, LocalDateTime, etc.
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer))   // ← utiliser notre serializer
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("agent-responses",  defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("user-permissions", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("prompt-templates", defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
