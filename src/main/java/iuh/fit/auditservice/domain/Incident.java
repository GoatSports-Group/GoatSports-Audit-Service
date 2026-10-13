package iuh.fit.auditservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Một sự cố của dịch vụ: mở khi dịch vụ ngừng / suy giảm, đóng khi hoạt động lại. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {
    private String id;
    private String service;
    /** DOWN hoặc DEGRADED; giữ mức nặng nhất từng gặp trong sự cố. */
    private String status;
    private Instant startedAt;
    private Instant endedAt;
    /** Đã gửi cảnh báo "ngừng quá 2 phút" cho admin. */
    private Instant alertedAt;
}
