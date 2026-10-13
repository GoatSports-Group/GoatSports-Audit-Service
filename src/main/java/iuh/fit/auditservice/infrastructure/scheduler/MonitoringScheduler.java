package iuh.fit.auditservice.infrastructure.scheduler;

import iuh.fit.auditservice.application.service.LogService;
import iuh.fit.auditservice.application.service.SystemHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Việc định kỳ của audit-service: theo dõi sự cố dịch vụ (30 giây), cảnh báo đăng nhập thất bại (1 phút),
 * xóa nhật ký quá hạn lưu (03:00 mỗi ngày).
 */
// ponytail: chạy trên mọi instance; audit-service hiện một instance. Nhiều instance thì cần khóa (ShedLock / lease Mongo),
// eventId cố định của cảnh báo đã chặn gửi trùng nhưng sự cố có thể bị mở hai lần.
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.monitoring.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringScheduler {
    private final SystemHealthService systemHealthService;
    private final LogService logService;

    @Scheduled(fixedDelayString = "${app.monitoring.incident-poll-ms:30000}", initialDelayString = "${app.monitoring.initial-delay-ms:20000}")
    public void trackIncidents() {
        try {
            systemHealthService.trackIncidents();
        } catch (Exception exception) {
            log.warn("Theo dõi sự cố dịch vụ lỗi: {}", exception.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.security.failed-login.poll-ms:60000}", initialDelayString = "${app.monitoring.initial-delay-ms:20000}")
    public void alertFailedLogins() {
        try {
            logService.alertFailedLogins();
        } catch (Exception exception) {
            log.warn("Kiểm tra đăng nhập thất bại lỗi: {}", exception.getMessage());
        }
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    public void purgeExpiredLogs() {
        long deleted = logService.purgeExpired();
        if (deleted > 0) log.info("Đã xóa {} bản ghi nhật ký quá hạn lưu", deleted);
    }
}
