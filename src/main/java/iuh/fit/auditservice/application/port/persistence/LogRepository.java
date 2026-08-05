package iuh.fit.auditservice.application.port.persistence;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.domain.Log;

public interface LogRepository {
    void save(Log log);

    PageResult<Log> findAll(PageFilter filter);
}
