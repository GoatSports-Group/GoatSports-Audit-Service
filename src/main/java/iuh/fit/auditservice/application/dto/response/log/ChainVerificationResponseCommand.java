package iuh.fit.auditservice.application.dto.response.log;

/**
 * Kết quả kiểm tra chuỗi hash nhật ký. {@code brokenAtSequence} / {@code problem} chỉ có khi {@code intact = false}.
 * {@code unchained}: bản ghi cũ ghi trước khi có chuỗi hash, không kiểm được.
 */
public record ChainVerificationResponseCommand(
        long checked,
        long unchained,
        boolean intact,
        Long brokenAtSequence,
        String problem
) {}
