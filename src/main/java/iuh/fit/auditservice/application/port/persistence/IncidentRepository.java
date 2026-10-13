package iuh.fit.auditservice.application.port.persistence;

import iuh.fit.auditservice.domain.Incident;

import java.time.Instant;
import java.util.List;

public interface IncidentRepository {
    /** Các sự cố chưa đóng (mỗi dịch vụ tối đa một). */
    List<Incident> findOpen();

    Incident save(Incident incident);

    /** Sự cố bắt đầu từ {@code since} hoặc còn đang mở, mới nhất trước. */
    List<Incident> findRecent(Instant since, int limit);
}
