package iuh.fit.auditservice.application.service.impl;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.application.service.LogService;
import iuh.fit.auditservice.domain.Log;

public class LogServiceImpl implements LogService {
    private final LogRepository logRepository;

    public LogServiceImpl(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Override
    public PageResult<Log> getAllLogs(PageFilter filter) {
        return logRepository.findAll(filter);
    }
}
