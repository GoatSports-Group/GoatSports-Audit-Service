package iuh.fit.auditservice.application.port.persistence;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.domain.Log;

public interface LogRepository {
    void save(Log log);

    /**
     * Ghi audit log một lần duy nhất theo {@code eventId}.
     *
     * @return false nếu eventId đã tồn tại (consumer retry / event trùng), true nếu vừa ghi mới.
     */
    boolean saveIfAbsent(Log log);

    PageResult<Log> findAll(PageFilter filter);
}
