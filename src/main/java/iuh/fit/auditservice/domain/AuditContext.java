package iuh.fit.auditservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditContext {
    private String ipAddress;
    private String userAgent;
    private String requestUri;
    private String httpMethod;
    private Integer statusCode;
}
