package iuh.fit.auditservice.presentation;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.dto.response.log.LogResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;
import iuh.fit.auditservice.application.mapper.LogMapper;
import iuh.fit.auditservice.application.service.LogService;
import iuh.fit.auditservice.application.dto.response.base.BaseListResponse;
import iuh.fit.auditservice.presentation.converter.LogConverter;
import iuh.fit.auditservice.presentation.response.log.LogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;
    private final LogMapper logMapper;
    private final LogConverter logConverter;

    @GetMapping
    @PreAuthorize("hasAuthority('GET_LOGS')")
    public ResponseEntity<BaseListResponse> getAllLogs(
            @RequestParam(value = "filter", required = false) String filter,
            Pageable pageable
    ) throws ServiceException
    {
        String decodedFilter = filter;
        if (filter != null) {
            try {
                decodedFilter = URLDecoder.decode(filter, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new ServiceException("Bộ lọc không hợp lệ: " + filter);
            }
        }

        PageFilter query = new PageFilter(decodedFilter, pageable.getPageNumber() + 1, pageable.getPageSize());
        PageResult<LogResponseCommand> result = logMapper.toLogResponseCommand(logService.getAllLogs(query));

        BaseListResponse.Meta meta = new BaseListResponse.Meta();
        meta.setPage(result.page());
        meta.setPageSize(result.pageSize());
        meta.setPages(result.totalPages());
        meta.setTotal(result.total());

        List<LogResponse> logResponses = result.items().stream()
                .map(logConverter::toResponse)
                .toList();

        return ResponseEntity.status(HttpStatus.OK).body(new BaseListResponse(meta, logResponses));
    }
}
