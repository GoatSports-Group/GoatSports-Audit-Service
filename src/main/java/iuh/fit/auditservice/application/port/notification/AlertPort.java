package iuh.fit.auditservice.application.port.notification;

import java.util.UUID;

/** Gửi cảnh báo vận hành tới admin nền tảng (chuông thông báo). Cùng eventId chỉ tạo một thông báo. */
public interface AlertPort {
    /** {@code referenceType}: SYSTEM_ALERT (mở trang Giám sát) hoặc SECURITY_ALERT (mở trang Nhật ký). */
    void notifyAdmin(UUID eventId, String title, String content, String referenceType, UUID referenceId);
}
