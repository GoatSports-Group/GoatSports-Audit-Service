package iuh.fit.auditservice.application.dto.response.system;

import java.time.Instant;
import java.util.List;

/** Hạ tầng dùng chung (Postgres, Kafka, Redis, MongoDB) và độ trễ xử lý của các consumer Kafka. */
public record InfrastructureResponseCommand(
        Instant checkedAt,
        List<Check> checks,
        List<ConsumerLag> consumers
) {
    public record Check(String name, String status, Long latencyMs, String detail) {
    }

    /** {@code lag} = số message đã vào topic mà group chưa xử lý (tổng mọi partition). */
    public record ConsumerLag(String groupId, String state, int members, List<String> topics, long lag) {
    }
}
