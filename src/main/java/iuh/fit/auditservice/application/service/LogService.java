package iuh.fit.auditservice.application.service;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.dto.response.log.LogStatsResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;
import iuh.fit.auditservice.domain.Log;

import java.time.LocalDateTime;

public interface LogService {
    PageResult<Log> getAllLogs(PageFilter filter);

    /** Thong ke [from, to), toi da 7 ngay (168 cot gio). */
    LogStatsResponseCommand getStats(LocalDateTime from, LocalDateTime to) throws ServiceException;

    /** Tài khoản đăng nhập thất bại liên tục trong cửa sổ theo dõi hiện tại. */
    java.util.List<iuh.fit.auditservice.application.dto.response.log.FailedLoginBurstResponseCommand> getFailedLoginBursts();

    /** Báo admin các tài khoản đăng nhập thất bại liên tục (mỗi tài khoản tối đa một lần mỗi cửa sổ). */
    void alertFailedLogins();

    /** Xóa nhật ký cũ hơn số ngày lưu giữ; trả số bản ghi đã xóa. */
    long purgeExpired();
}
