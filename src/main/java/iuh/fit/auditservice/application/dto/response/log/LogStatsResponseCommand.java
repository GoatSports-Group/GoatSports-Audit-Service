package iuh.fit.auditservice.application.dto.response.log;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Thong ke nhat ky trong [from, to) dem truc tiep o MongoDB (trang Nhat ky cua admin).
 *
 * @param hourly    so request theo tung gio lien tiep bat dau tu `from` (toi da 168 gio)
 * @param actions   cac nghiep vu (action) khac nhau trong khoang, de do bo loc
 * @param activeUsers so nguoi dung khac nhau (bo anonymous / rong)
 */
public record LogStatsResponseCommand(
        LocalDateTime from,
        LocalDateTime to,
        long totalRequests,
        long errorRequests,
        long status2xx,
        long status3xx,
        long status4xx,
        long status5xx,
        long activeUsers,
        List<String> actions,
        List<Long> hourly
) {
}
