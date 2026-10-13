package iuh.fit.auditservice.application.dto.response.system;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Lưu lượng request/giây của từng service theo thời gian (biểu đồ nhỏ trên trang Giám sát). Mỗi danh sách có cùng số
 * điểm, điểm i ứng với {@code start + i * stepSeconds}; null = không có số liệu ở thời điểm đó.
 */
public record TrendResponseCommand(
        String range,
        Instant start,
        long stepSeconds,
        Map<String, List<Double>> requestsPerSecond
) {
}
