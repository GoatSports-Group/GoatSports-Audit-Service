package iuh.fit.auditservice.application.port.monitoring;

import iuh.fit.auditservice.application.dto.response.system.InfrastructureResponseCommand;

import java.util.List;

/** Kiểm tra trực tiếp hạ tầng dùng chung, không qua Prometheus (chưa có exporter cho chúng). */
public interface InfrastructurePort {
    List<InfrastructureResponseCommand.Check> check();

    /** Rỗng khi không gọi được Kafka (kết quả kiểm tra Kafka đã báo lỗi). */
    List<InfrastructureResponseCommand.ConsumerLag> consumerLag();
}
