package com.enterprise.aiagent.service.audit;

import com.enterprise.aiagent.core.domain.AgentQuery;
import com.enterprise.aiagent.core.domain.AgentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final StringRedisTemplate redis;

    @Async("auditExecutor")
    public void record(AgentQuery query, AgentResult result) {
        try {
            String today  = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String userId = query.getUserId() != null ? query.getUserId() : "anonymous";
            Duration ttl  = Duration.ofDays(90);

            String reqKey = "audit:daily:" + today + ":user:" + userId + ":requests";
            redis.opsForValue().increment(reqKey);
            redis.expire(reqKey, ttl);

            redis.opsForValue().increment("audit:daily:" + today + ":tokens:input",  result.getInputTokens());
            redis.opsForValue().increment("audit:daily:" + today + ":tokens:output", result.getOutputTokens());

            if (query.getType() != null)
                redis.opsForValue().increment("audit:daily:" + today + ":type:" + query.getType().name());

            redis.opsForValue().increment("audit:daily:" + today + ":status:" + result.getStatus().name());
            redis.opsForValue().increment("audit:daily:" + today + ":processing_ms_total", result.getProcessingMs());

            log.info("AUDIT queryId={} userId={} type={} status={} model={} inputTokens={} outputTokens={} processingMs={} requestId={}",
                    query.getQueryId(), userId, query.getType(), result.getStatus(), result.getModelUsed(),
                    result.getInputTokens(), result.getOutputTokens(), result.getProcessingMs(), MDC.get("requestId"));

        } catch (Exception e) {
            log.error("Erreur audit (non bloquant): {}", e.getMessage());
        }
    }

    public AuditStats getDailyStats(String date) {
        String p = "audit:daily:" + date;
        return new AuditStats(date,
                parseLong(redis.opsForValue().get(p + ":tokens:input")),
                parseLong(redis.opsForValue().get(p + ":tokens:output")),
                parseLong(redis.opsForValue().get(p + ":type:TECHNICAL_QUESTION")),
                parseLong(redis.opsForValue().get(p + ":type:CODE_REVIEW")),
                parseLong(redis.opsForValue().get(p + ":type:SECURITY_AUDIT")),
                parseLong(redis.opsForValue().get(p + ":status:SUCCESS")),
                parseLong(redis.opsForValue().get(p + ":status:FALLBACK")),
                parseLong(redis.opsForValue().get(p + ":processing_ms_total")));
    }

    private long parseLong(String v) {
        try { return v != null ? Long.parseLong(v) : 0L; } catch (NumberFormatException e) { return 0L; }
    }

    public record AuditStats(String date, long totalInputTokens, long totalOutputTokens,
            long askCount, long reviewCount, long securityAuditCount,
            long successCount, long fallbackCount, long totalProcessingMs) {
        public double avgProcessingMs() {
            long total = askCount + reviewCount + securityAuditCount;
            return total > 0 ? (double) totalProcessingMs / total : 0;
        }
        public long estimatedCostCentimes() {
            double cost = (totalInputTokens / 1_000_000.0) * 3.0 + (totalOutputTokens / 1_000_000.0) * 15.0;
            return Math.round(cost * 100);
        }
    }
}
