package iuh.fit.auditservice.infrastructure.config;

import iuh.fit.auditservice.application.port.monitoring.BackgroundJobPort;
import iuh.fit.auditservice.application.port.monitoring.InfrastructurePort;
import iuh.fit.auditservice.application.port.monitoring.MetricsQueryPort;
import iuh.fit.auditservice.application.port.notification.AlertPort;
import iuh.fit.auditservice.application.port.persistence.IncidentRepository;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.application.service.SystemHealthService;
import iuh.fit.auditservice.application.service.impl.LogServiceImpl;
import iuh.fit.auditservice.application.service.impl.SystemHealthServiceImpl;
import iuh.fit.auditservice.infrastructure.external.infra.InfrastructureAdapter;
import iuh.fit.auditservice.infrastructure.external.jobs.BackgroundJobAdapter;
import iuh.fit.auditservice.infrastructure.external.notification.NotificationAlertAdapter;
import iuh.fit.auditservice.infrastructure.external.prometheus.PrometheusMetricsAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ApplicationServiceConfig {
    @Bean
    public AlertPort alertPort(@Value("${notification.service.url}") String notificationServiceUrl,
                               @Value("${app.jwt.secret}") String gatewaySecret) {
        return new NotificationAlertAdapter(notificationServiceUrl, gatewaySecret);
    }

    @Bean
    public LogServiceImpl logService(LogRepository logRepository, AlertPort alertPort,
                                     @Value("${app.audit.retention-days:180}") int retentionDays,
                                     @Value("${app.security.failed-login.window-minutes:15}") int windowMinutes,
                                     @Value("${app.security.failed-login.threshold:5}") int threshold) {
        return new LogServiceImpl(logRepository, alertPort, Clock.systemDefaultZone(), retentionDays, windowMinutes, threshold);
    }

    @Bean
    public MetricsQueryPort metricsQueryPort(@Value("${app.monitoring.prometheus-url}") String prometheusUrl) {
        return new PrometheusMetricsAdapter(prometheusUrl);
    }

    @Bean
    public InfrastructurePort infrastructurePort(@Value("${app.monitoring.postgres-address:localhost:5432}") String postgres,
                                                 @Value("${app.monitoring.redis-url:redis://localhost:6379}") String redis,
                                                 @Value("${spring.kafka.bootstrap-servers}") String kafka,
                                                 MongoTemplate mongoTemplate) {
        return new InfrastructureAdapter(postgres, redis, kafka, mongoTemplate);
    }

    @Bean
    public BackgroundJobPort backgroundJobPort(@Value("${report.service.url}") String reportServiceUrl,
                                               @Value("${notification.service.url}") String notificationServiceUrl,
                                               @Value("${payment.service.url}") String paymentServiceUrl,
                                               @Value("${app.jwt.secret}") String gatewaySecret) {
        Map<String, String> services = new LinkedHashMap<>();
        services.put("report-service", reportServiceUrl);
        services.put("notification-service", notificationServiceUrl);
        services.put("payment-service", paymentServiceUrl);
        return new BackgroundJobAdapter(services, gatewaySecret);
    }

    @Bean
    public SystemHealthService systemHealthService(MetricsQueryPort metricsQueryPort,
                                                   @Value("${app.monitoring.grafana-url:}") String grafanaUrl,
                                                   IncidentRepository incidentRepository, AlertPort alertPort,
                                                   InfrastructurePort infrastructurePort, BackgroundJobPort backgroundJobPort) {
        return new SystemHealthServiceImpl(metricsQueryPort, grafanaUrl.isBlank() ? null : grafanaUrl, Clock.systemUTC(),
                incidentRepository, alertPort, infrastructurePort, backgroundJobPort);
    }
}
