package iuh.fit.auditservice.infrastructure.decorator;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.dto.response.log.ChainVerificationResponseCommand;
import iuh.fit.auditservice.application.dto.response.log.FailedLoginBurstResponseCommand;
import iuh.fit.auditservice.application.service.LogService;
import iuh.fit.auditservice.application.service.impl.LogServiceImpl;
import iuh.fit.auditservice.application.dto.response.log.LogStatsResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;
import iuh.fit.auditservice.domain.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Primary
@RequiredArgsConstructor
public class LogDecorator implements LogService {
    private final LogServiceImpl logServiceImpl;

    @Override
    public PageResult<Log> getAllLogs(PageFilter filter) {
        return logServiceImpl.getAllLogs(filter);
    }

    @Override
    public LogStatsResponseCommand getStats(LocalDateTime from, LocalDateTime to) throws ServiceException {
        return logServiceImpl.getStats(from, to);
    }

    @Override
    public List<FailedLoginBurstResponseCommand> getFailedLoginBursts() {
        return logServiceImpl.getFailedLoginBursts();
    }

    @Override
    public void resolveFailedLogins(String account, String resolvedBy) throws ServiceException {
        logServiceImpl.resolveFailedLogins(account, resolvedBy);
    }

    @Override
    public void alertFailedLogins() {
        logServiceImpl.alertFailedLogins();
    }

    @Override
    public long purgeExpired() {
        return logServiceImpl.purgeExpired();
    }

    @Override
    public ChainVerificationResponseCommand verifyChain() {
        return logServiceImpl.verifyChain();
    }
}
