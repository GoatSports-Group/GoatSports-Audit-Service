package iuh.fit.auditservice.application.port.persistence;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.dto.response.log.LogStatsResponseCommand;
import iuh.fit.auditservice.domain.Log;

import java.time.LocalDateTime;

public interface LogRepository {
    void save(Log log);

    /**
     * Ghi audit log một lần duy nhất theo {@code eventId}.
     *
     * @return false nếu eventId đã tồn tại (consumer retry / event trùng), true nếu vừa ghi mới.
     */
    boolean saveIfAbsent(Log log);

    PageResult<Log> findAll(PageFilter filter);

    /** Dem trong [from, to): tong, theo nhom ma trang thai, nguoi dung, nghiep vu, va theo tung gio. */
    LogStatsResponseCommand stats(LocalDateTime from, LocalDateTime to, int hours);

    /** Xóa bản ghi có timestamp trước {@code cutoff}; trả số bản ghi đã xóa. */
    long deleteOlderThan(LocalDateTime cutoff);

    /** Ghi nhận admin đã xử lý cảnh báo đăng nhập thất bại của {@code account} tại {@code at}. */
    void resolveFailedLogins(String account, LocalDateTime at, String resolvedBy);

    /** Lần xử lý gần nhất của từng tài khoản (không có thì không nằm trong map). */
    java.util.Map<String, LocalDateTime> failedLoginResolutions(java.util.Collection<String> accounts);

    /** Tài khoản có ít nhất {@code threshold} lần LOGIN lỗi (4xx/5xx) từ {@code since}, nhiều lần nhất trước. */
    java.util.List<iuh.fit.auditservice.application.dto.response.log.FailedLoginBurstResponseCommand> failedLoginBursts(
            LocalDateTime since, int threshold);
}
