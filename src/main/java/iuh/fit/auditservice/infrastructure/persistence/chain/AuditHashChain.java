package iuh.fit.auditservice.infrastructure.persistence.chain;

import iuh.fit.auditservice.domain.AuditChange;
import iuh.fit.auditservice.domain.AuditContext;
import iuh.fit.auditservice.infrastructure.persistence.entity.LogEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Chuỗi hash của nhật ký kiểm toán: mỗi bản ghi có {@code sequence} liên tục, {@code previousHash} = recordHash của
 * bản trước, {@code recordHash} = SHA-256(sequence + previousHash + nội dung). Sửa một trường, xóa một bản giữa chuỗi
 * hay chèn bản giả đều làm lệch hash hoặc đứt sequence đúng tại chỗ đó.
 *
 * ponytail: SHA-256 không khóa — ai ghi được Mongo và tính lại toàn bộ hash phía sau thì vẫn qua mặt được. Cần chống cả
 * trường hợp đó thì đổi sang HMAC với khóa chỉ audit-service giữ, hoặc định kỳ neo hash đầu chuỗi ra ngoài hệ thống.
 */
public final class AuditHashChain {

    public static final String GENESIS = "0".repeat(64);

    private AuditHashChain() {}

    public record Verification(long checked, boolean intact, Long brokenAtSequence, String problem) {}

    public static String hash(LogEntity entry) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, entry.getSequence());
        append(canonical, entry.getPreviousHash());
        append(canonical, entry.getEventId());
        append(canonical, entry.getEventType());
        append(canonical, entry.getCorrelationId());
        append(canonical, entry.getSourceService());
        append(canonical, entry.getActorId());
        append(canonical, entry.getResourceType());
        append(canonical, entry.getResourceId());
        append(canonical, entry.getUserId());
        append(canonical, entry.getAction());
        append(canonical, entry.getDescription());
        append(canonical, entry.getIpAddress());
        append(canonical, entry.getStatusCode());
        append(canonical, epochMillis(entry.getTimestamp()));
        append(canonical, entry.getErrorMessage());
        AuditContext context = entry.getContext();
        append(canonical, context == null ? null : context.getIpAddress());
        append(canonical, context == null ? null : context.getUserAgent());
        append(canonical, context == null ? null : context.getRequestUri());
        append(canonical, context == null ? null : context.getHttpMethod());
        append(canonical, context == null ? null : context.getStatusCode());
        List<AuditChange> changes = entry.getChanges() == null ? List.of() : entry.getChanges();
        append(canonical, changes.size());
        for (AuditChange change : changes) {
            append(canonical, change.getField());
            append(canonical, change.getPreviousValue());
            append(canonical, change.getCurrentValue());
        }
        return sha256(canonical.toString());
    }

    /** {@code ordered}: các bản ghi đã nối chuỗi, sequence tăng dần. Dừng ở chỗ hỏng đầu tiên. */
    public static Verification verify(Iterable<LogEntity> ordered) {
        long checked = 0;
        LogEntity previous = null;
        for (LogEntity entry : ordered) {
            checked++;
            // Bản đầu tiên còn lại là mốc: các bản trước nó có thể đã bị xóa đúng hạn lưu giữ.
            if (previous != null && entry.getSequence() != previous.getSequence() + 1) {
                return broken(checked, entry, "Thiếu bản ghi giữa #" + previous.getSequence() + " và #" + entry.getSequence());
            }
            if (previous != null && !Objects.equals(entry.getPreviousHash(), previous.getRecordHash())) {
                return broken(checked, entry, "Liên kết hash bị đứt tại bản ghi #" + entry.getSequence());
            }
            if (!Objects.equals(entry.getRecordHash(), hash(entry))) {
                return broken(checked, entry, "Nội dung bản ghi #" + entry.getSequence() + " đã bị sửa");
            }
            previous = entry;
        }
        return new Verification(checked, true, null, null);
    }

    /** Cùng phép đổi Mongo dùng khi đọc / ghi LocalDateTime, nên đổi múi giờ máy chủ không làm lệch hash. */
    private static Long epochMillis(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static Verification broken(long checked, LogEntity entry, String problem) {
        return new Verification(checked, false, entry.getSequence(), problem);
    }

    /** Tiền tố độ dài để "ab"+"c" và "a"+"bc" không cho cùng chuỗi. */
    private static void append(StringBuilder canonical, Object value) {
        if (value == null) {
            canonical.append("~|");
            return;
        }
        String text = value.toString();
        canonical.append(text.length()).append(':').append(text).append('|');
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
