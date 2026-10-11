package iuh.fit.auditservice.presentation;

import iuh.fit.auditservice.application.dto.response.system.SystemHealthResponseCommand;
import iuh.fit.auditservice.application.service.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
