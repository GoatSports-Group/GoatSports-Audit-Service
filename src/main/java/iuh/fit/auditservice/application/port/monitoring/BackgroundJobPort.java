package iuh.fit.auditservice.application.port.monitoring;

import iuh.fit.auditservice.application.dto.response.system.JobSummaryResponseCommand;

import java.util.List;

/** Hỏi các service sở hữu job nền (xuất báo cáo, gửi thông báo hàng loạt, chi trả / hoàn tiền) về tình trạng. */
public interface BackgroundJobPort {
    List<JobSummaryResponseCommand> summaries();
}
