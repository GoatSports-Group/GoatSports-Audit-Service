package iuh.fit.auditservice.application.service;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.domain.Log;

public interface LogService {
    PageResult<Log> getAllLogs(PageFilter filter);
}
