package iuh.fit.auditservice.application.mapper;

import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.dto.response.log.LogResponseCommand;
import iuh.fit.auditservice.domain.Log;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LogMapper {
    List<LogResponseCommand> toLogResponseCommandList(List<Log> logs);

    default PageResult<LogResponseCommand> toLogResponseCommand(PageResult<Log> logs) {
        if (logs == null) return null;
        return new PageResult<>(
                toLogResponseCommandList(logs.items()),
                logs.total(),
                logs.page(),
                logs.pageSize(),
                logs.totalPages()
        );
    }
}