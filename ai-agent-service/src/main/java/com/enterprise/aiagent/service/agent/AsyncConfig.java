package com.enterprise.aiagent.service.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * ┌──────────────────────────────────────────────────────────────────────┐
 *  CONFIGURATION DU THREAD POOL ASYNCHRONE
 *
 *  Utilisé par AuditService (@Async) pour que l'audit ne bloque
 *  jamais le thread principal de traitement de la requête.
 *
 *  Pool configuré pour :
 *  - 5 threads minimum (prêts à tout moment)
 *  - 20 threads maximum (sous charge)
 *  - File d'attente de 500 tâches (absorption des pics)
 *  - Nom de thread : "audit-" (visible dans les logs/JVM)
 * └──────────────────────────────────────────────────────────────────────┘
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        executor.setWaitForTasksToCompleteOnShutdown(true);  // attendre les audits en cours
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
