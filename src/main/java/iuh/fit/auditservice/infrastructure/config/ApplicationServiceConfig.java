package iuh.fit.auditservice.infrastructure.config;

import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.application.service.impl.LogServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationServiceConfig {
    @Bean
    public LogServiceImpl logService(LogRepository logRepository) {
        return new LogServiceImpl(logRepository);
    }
}
