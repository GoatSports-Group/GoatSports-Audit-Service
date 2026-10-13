package iuh.fit.auditservice.application.service;

import iuh.fit.auditservice.application.dto.response.system.SystemHealthResponseCommand;

public interface SystemHealthService {
    SystemHealthResponseCommand getServicesHealth();

    /** Request/giây của từng service trong 1 giờ ({@code "1h"}) hoặc 24 giờ ({@code "24h"}) gần nhất. */
    iuh.fit.auditservice.application.dto.response.system.TrendResponseCommand getTrends(String range)
            throws iuh.fit.auditservice.application.exception.ServiceException;

    /** Sự cố trong {@code days} ngày gần nhất và sự cố còn mở, mới nhất trước. */
    java.util.List<iuh.fit.auditservice.application.dto.response.system.IncidentResponseCommand> getIncidents(int days);

    iuh.fit.auditservice.application.dto.response.system.InfrastructureResponseCommand getInfrastructure();

    java.util.List<iuh.fit.auditservice.application.dto.response.system.JobSummaryResponseCommand> getJobs();

    /**
     * Một lượt theo dõi (chạy định kỳ): mở / đóng sự cố theo trạng thái hiện tại và báo admin khi một dịch vụ ngừng
     * quá {@link iuh.fit.auditservice.application.service.impl.SystemHealthServiceImpl#DOWN_ALERT_AFTER}.
     */
    void trackIncidents();
}
