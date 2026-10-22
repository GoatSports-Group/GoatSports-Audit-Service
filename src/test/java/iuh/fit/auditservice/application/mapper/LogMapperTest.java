package iuh.fit.auditservice.application.mapper;

import iuh.fit.auditservice.application.dto.response.log.LogResponseCommand;
import iuh.fit.auditservice.domain.AuditContext;
import iuh.fit.auditservice.domain.Log;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMapperTest {

    @Test
    void exposesSourceResourceAndRequestContext() {
        Log log = Log.builder().logId("l-1").sourceService("payment-service").correlationId("c-1")
                .resourceType("BOOKING").resourceId("b-1")
                .context(AuditContext.builder().httpMethod("POST").requestUri("/api/v1/x").userAgent("Firefox").build())
                .build();

        LogResponseCommand command = new LogMapperImpl().toCommand(log);

        assertThat(command.sourceService()).isEqualTo("payment-service");
        assertThat(command.resourceId()).isEqualTo("b-1");
        assertThat(command.httpMethod()).isEqualTo("POST");
        assertThat(command.requestUri()).isEqualTo("/api/v1/x");
        assertThat(command.userAgent()).isEqualTo("Firefox");
        assertThat(new LogMapperImpl().toCommand(Log.builder().logId("l-2").build()).httpMethod()).isNull();
    }
}
