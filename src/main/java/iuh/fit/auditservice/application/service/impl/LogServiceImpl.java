package iuh.fit.auditservice.application.service.impl;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.application.service.LogService;
import iuh.fit.auditservice.application.dto.response.log.LogStatsResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;
import iuh.fit.auditservice.domain.Log;

import java.time.Duration;
import java.time.LocalDateTime;

public class LogServiceImpl implements LogService {
    static final int MAX_HOURS = 168;

    private final LogRepository logRepository;

    public LogServiceImpl(LogRepository logRepository) {
        this.logRepository = logRepository;
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
