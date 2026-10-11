package iuh.fit.auditservice.infrastructure.config;

import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.application.service.impl.LogServiceImpl;
import iuh.fit.auditservice.application.port.monitoring.MetricsQueryPort;
import iuh.fit.auditservice.application.service.SystemHealthService;
import iuh.fit.auditservice.application.service.impl.SystemHealthServiceImpl;
import iuh.fit.auditservice.infrastructure.external.prometheus.PrometheusMetricsAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationServiceConfig {
    @Bean
    public LogServiceImpl logService(LogRepository logRepository) {
        return new LogServiceImpl(logRepository);
    }

    @Bean
    public MetricsQueryPort metricsQueryPort(@Value("${app.monitoring.prometheus-url}") String prometheusUrl) {
        return new PrometheusMetricsAdapter(prometheusUrl);
    }

    @Bean
    public SystemHealthService systemHealthService(MetricsQueryPort metricsQueryPort,
                                                   @Value("${app.monitoring.grafana-url:}") String grafanaUrl) {
        return new SystemHealthServiceImpl(metricsQueryPort, grafanaUrl.isBlank() ? null : grafanaUrl, Clock.systemUTC());
    }
}
