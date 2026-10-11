package iuh.fit.auditservice.application.service;

import iuh.fit.auditservice.application.dto.response.system.SystemHealthResponseCommand;

public interface SystemHealthService {
    SystemHealthResponseCommand getServicesHealth();
}
