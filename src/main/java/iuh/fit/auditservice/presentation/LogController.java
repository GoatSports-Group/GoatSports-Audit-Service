package iuh.fit.auditservice.presentation;

import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.BaseListResponse;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.dto.response.log.ChainVerificationResponseCommand;
import iuh.fit.auditservice.application.dto.response.log.FailedLoginBurstResponseCommand;
import iuh.fit.auditservice.application.dto.response.log.LogResponseCommand;
import iuh.fit.auditservice.application.dto.response.log.LogStatsResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;
import iuh.fit.auditservice.application.mapper.LogMapper;
import iuh.fit.auditservice.application.port.security.CurrentUserProvider;
import iuh.fit.auditservice.application.service.LogService;
import iuh.fit.auditservice.presentation.converter.LogConverter;
import iuh.fit.auditservice.presentation.response.log.LogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;
    private final LogMapper logMapper;
    private final LogConverter logConverter;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Thong ke nhat ky trong [from, to) (gio may chu, nhu `timestamp` da luu bang LocalDateTime), dem o DB thay vi tai vai tram log
     * ve client. Trang Nhat ky goi cho "hom nay" theo gio may nguoi xem.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('GET_LOGS')")
    public ResponseEntity<LogStatsResponseCommand> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) throws ServiceException {
        return ResponseEntity.ok(logService.getStats(from, to));
    }

    /** Đi lại chuỗi hash nhật ký; {@code intact = false} kèm vị trí và lý do chỗ hỏng đầu tiên. */
    @GetMapping("/integrity")
    @PreAuthorize("hasAuthority('GET_LOGS')")
    public ChainVerificationResponseCommand verifyIntegrity() {
        return logService.verifyChain();
    }

    /** Tài khoản đăng nhập thất bại liên tục trong cửa sổ theo dõi (mặc định 5 lần / 15 phút). */
    @GetMapping("/alerts/failed-logins")
    @PreAuthorize("hasAuthority('GET_LOGS')")
    public List<FailedLoginBurstResponseCommand> failedLogins() {
        return logService.getFailedLoginBursts();
    }

    /** Đánh dấu đã xử lý cảnh báo của một tài khoản; có lần sai mới thì cảnh báo hiện lại. */
    @PostMapping("/alerts/failed-logins/resolve")
    @PreAuthorize("hasAuthority('RESOLVE_SECURITY_ALERT')")
    public ResponseEntity<Void> resolveFailedLogins(@RequestBody ResolveFailedLoginRequest request) throws ServiceException {
        logService.resolveFailedLogins(request.account(), currentUserProvider.getCurrentUserId()
                .orElseThrow(() -> new ServiceException("Phiên đăng nhập đã hết hạn")));
        return ResponseEntity.noContent().build();
    }

    public record ResolveFailedLoginRequest(String account) {
    }

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
