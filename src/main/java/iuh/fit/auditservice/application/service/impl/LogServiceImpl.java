package iuh.fit.auditservice.application.service.impl;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.dto.response.log.FailedLoginBurstResponseCommand;
import iuh.fit.auditservice.application.dto.response.log.LogStatsResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;
import iuh.fit.auditservice.application.port.notification.AlertPort;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.application.service.LogService;
import iuh.fit.auditservice.domain.Log;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class LogServiceImpl implements LogService {
    static final int MAX_HOURS = 168;

    private final LogRepository logRepository;
    private final AlertPort alerts;
    private final Clock clock;
    private final int retentionDays;
    private final int failedLoginWindowMinutes;
    private final int failedLoginThreshold;

    public LogServiceImpl(LogRepository logRepository, AlertPort alerts, Clock clock, int retentionDays,
                          int failedLoginWindowMinutes, int failedLoginThreshold) {
        this.logRepository = logRepository;
        this.alerts = alerts;
        this.clock = clock;
        this.retentionDays = retentionDays;
        this.failedLoginWindowMinutes = failedLoginWindowMinutes;
        this.failedLoginThreshold = failedLoginThreshold;
    }

    @Override
    public List<FailedLoginBurstResponseCommand> getFailedLoginBursts() {
        // timestamp lưu theo giờ máy chủ (LocalDateTime), nên so cũng theo giờ máy chủ.
        return logRepository.failedLoginBursts(LocalDateTime.now(clock).minusMinutes(failedLoginWindowMinutes),
                failedLoginThreshold);
    }

    @Override
    public void alertFailedLogins() {
        long window = Math.floorDiv(clock.instant().getEpochSecond() / 60, failedLoginWindowMinutes);
        for (FailedLoginBurstResponseCommand burst : getFailedLoginBursts()) {
            // Khóa theo tài khoản + cửa sổ: lượt chạy sau trong cùng cửa sổ không báo lại.
            UUID eventId = UUID.nameUUIDFromBytes(("failed-login:" + burst.account() + ":" + window).getBytes(StandardCharsets.UTF_8));
            alerts.notifyAdmin(eventId, "Đăng nhập thất bại liên tục",
                    "Tài khoản " + burst.account() + " đăng nhập sai " + burst.attempts() + " lần trong "
                            + failedLoginWindowMinutes + " phút" + (burst.lastIp() == null ? "" : ", IP gần nhất " + burst.lastIp())
                            + ". Xem ở trang Nhật ký.",
                    "SECURITY_ALERT", UUID.nameUUIDFromBytes(burst.account().getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Override
    public long purgeExpired() {
        return logRepository.deleteOlderThan(LocalDateTime.now(clock).minusDays(retentionDays));
    }

    @Override
    public PageResult<Log> getAllLogs(PageFilter filter) {
        return logRepository.findAll(filter);
    }

    @Override
    public LogStatsResponseCommand getStats(LocalDateTime from, LocalDateTime to) throws ServiceException {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new ServiceException("Khoảng thời gian thống kê không hợp lệ.");
        }
        long hours = (long) Math.ceil(Duration.between(from, to).toMinutes() / 60.0);
        if (hours > MAX_HOURS) {
            throw new ServiceException("Khoảng thời gian thống kê tối đa 7 ngày.");
        }
        return logRepository.stats(from, to, (int) hours);
    }
}
