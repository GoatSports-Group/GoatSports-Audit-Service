package iuh.fit.auditservice.application.service;

import iuh.fit.auditservice.application.dto.response.system.IncidentResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.InfrastructureResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.JobSummaryResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.SystemHealthResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.TrendResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;

import java.util.List;

public interface SystemHealthService {
    SystemHealthResponseCommand getServicesHealth();

    /** Request/giây của từng service trong 1 giờ ({@code "1h"}) hoặc 24 giờ ({@code "24h"}) gần nhất. */
    TrendResponseCommand getTrends(String range)
            throws ServiceException;

    /** Sự cố trong {@code days} ngày gần nhất và sự cố còn mở, mới nhất trước. */
    List<IncidentResponseCommand> getIncidents(int days);

    InfrastructureResponseCommand getInfrastructure();

    List<JobSummaryResponseCommand> getJobs();

    /**
     * Một lượt theo dõi (chạy định kỳ): mở / đóng sự cố theo trạng thái hiện tại và báo admin khi một dịch vụ ngừng
     * quá {@link iuh.fit.auditservice.application.service.impl.SystemHealthServiceImpl#DOWN_ALERT_AFTER}.
     */
    void trackIncidents();
}
