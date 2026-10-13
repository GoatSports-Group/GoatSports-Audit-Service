package iuh.fit.auditservice.application.dto.response.log;

import java.time.LocalDateTime;

/** Một tài khoản đăng nhập thất bại nhiều lần trong cửa sổ theo dõi (dấu hiệu dò mật khẩu). */
public record FailedLoginBurstResponseCommand(
        String account,
        long attempts,
        LocalDateTime firstAt,
        LocalDateTime lastAt,
        String lastIp
) {
}
