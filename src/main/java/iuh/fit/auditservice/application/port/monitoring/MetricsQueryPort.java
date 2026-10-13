package iuh.fit.auditservice.application.port.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface MetricsQueryPort {
    /**
     * Chạy một truy vấn tức thời, trả giá trị theo nhãn {@code service}. Giá trị NaN (vd. quantile khi chưa có
     * request) bị bỏ qua. Ném {@link MetricsUnavailableException} khi không đọc được nguồn số liệu.
     */
    Map<String, Double> queryByService(String promQl);

    /**
     * Truy vấn theo khoảng [start, end] với bước {@code step}: mỗi service một dãy cùng độ dài
     * ({@code (end - start) / step + 1} điểm), điểm thiếu là null.
     */
    Map<String, List<Double>> rangeByService(String promQl, Instant start, Instant end, Duration step);

    class MetricsUnavailableException extends RuntimeException {
        public MetricsUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
