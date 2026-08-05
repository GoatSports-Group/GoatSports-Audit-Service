package iuh.fit.auditservice.infrastructure.decorator;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.service.LogService;
import iuh.fit.auditservice.application.service.impl.LogServiceImpl;
import iuh.fit.auditservice.domain.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogDecorator implements LogService {
    private final LogServiceImpl logServiceImpl;

    @Override
    public PageResult<Log> getAllLogs(PageFilter filter) {
        return logServiceImpl.getAllLogs(filter);
    }
}
