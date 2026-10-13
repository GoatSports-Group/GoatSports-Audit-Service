package iuh.fit.auditservice.presentation;

import iuh.fit.auditservice.application.dto.response.system.IncidentResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.InfrastructureResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.JobSummaryResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.SystemHealthResponseCommand;
import iuh.fit.auditservice.application.dto.response.system.TrendResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;
import iuh.fit.auditservice.application.service.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
public class SystemHealthController {
    private final SystemHealthService systemHealthService;

    @GetMapping("/services")
    @PreAuthorize("hasAuthority('GET_SYSTEM_HEALTH')")
    public SystemHealthResponseCommand services() {
        return systemHealthService.getServicesHealth();
    }

    @GetMapping("/trends")
    @PreAuthorize("hasAuthority('GET_SYSTEM_HEALTH')")
    public TrendResponseCommand trends(@RequestParam(defaultValue = "1h") String range) throws ServiceException {
        return systemHealthService.getTrends(range);
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasAuthority('GET_SYSTEM_HEALTH')")
    public List<IncidentResponseCommand> incidents(@RequestParam(defaultValue = "7") int days) {
        return systemHealthService.getIncidents(days);
    }

    @GetMapping("/infrastructure")
    @PreAuthorize("hasAuthority('GET_SYSTEM_HEALTH')")
    public InfrastructureResponseCommand infrastructure() {
        return systemHealthService.getInfrastructure();
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('GET_SYSTEM_HEALTH')")
    public List<JobSummaryResponseCommand> jobs() {
        return systemHealthService.getJobs();
    }
}
