package iuh.fit.auditservice.application.port.monitoring;

import java.util.Map;

public interface MetricsQueryPort {
    /**
     * Chạy một truy vấn tức thời, trả giá trị theo nhãn {@code service}. Giá trị NaN (vd. quantile khi chưa có
     * request) bị bỏ qua. Ném {@link MetricsUnavailableException} khi không đọc được nguồn số liệu.
     */
    Map<String, Double> queryByService(String promQl);

    class MetricsUnavailableException extends RuntimeException {
        public MetricsUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
