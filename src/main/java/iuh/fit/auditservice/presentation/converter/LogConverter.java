package iuh.fit.auditservice.presentation.converter;

import iuh.fit.auditservice.application.dto.response.log.LogResponseCommand;
import iuh.fit.auditservice.presentation.response.log.LogResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LogConverter {
    LogResponse toResponse(LogResponseCommand log);
}
