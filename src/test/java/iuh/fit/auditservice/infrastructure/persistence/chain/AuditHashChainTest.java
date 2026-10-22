package iuh.fit.auditservice.infrastructure.persistence.chain;

import iuh.fit.auditservice.domain.AuditContext;
import iuh.fit.auditservice.infrastructure.persistence.entity.LogEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditHashChainTest {

    /** Nối chuỗi giống LogRepositoryImpl.appendToChain, giữ bản ghi đầu là mốc sau khi đã dọn theo hạn lưu giữ. */
    private static List<LogEntity> chain(int size, long firstSequence) {
        List<LogEntity> entries = new ArrayList<>();
        String previousHash = "f".repeat(64);
        for (int index = 0; index < size; index++) {
            LogEntity entry = LogEntity.builder()
                    .sequence(firstSequence + index)
                    .previousHash(previousHash)
                    .userId("admin-" + index)
                    .action("RECORD_TOURNAMENT_RESULT")
                    .description("Ghi tỷ số 3-1 cho trận #" + index)
                    .statusCode(200)
                    .timestamp(LocalDateTime.of(2026, 10, 22, 10, 0).plusSeconds(index))
                    .context(AuditContext.builder().ipAddress("10.0.0.1").httpMethod("PUT").build())
                    .build();
            entry.setRecordHash(AuditHashChain.hash(entry));
            previousHash = entry.getRecordHash();
            entries.add(entry);
        }
        return entries;
    }

    @Test
    void untouchedChainIsIntactEvenWhenOlderEntriesWereRetired() {
        AuditHashChain.Verification result = AuditHashChain.verify(chain(4, 120));

        assertThat(result.intact()).isTrue();
        assertThat(result.checked()).isEqualTo(4);
    }

    @Test
    void editingAStoredFieldIsDetectedAtThatEntry() {
        List<LogEntity> entries = chain(4, 1);
        entries.get(1).setDescription("Ghi tỷ số 0-5 cho trận #1");

        AuditHashChain.Verification result = AuditHashChain.verify(entries);

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtSequence()).isEqualTo(2);
        assertThat(result.problem()).contains("đã bị sửa");
    }

    @Test
    void recomputingTheEditedEntryHashStillBreaksTheNextLink() {
        List<LogEntity> entries = chain(4, 1);
        LogEntity edited = entries.get(1);
        edited.setUserId("someone-else");
        edited.setRecordHash(AuditHashChain.hash(edited));

        AuditHashChain.Verification result = AuditHashChain.verify(entries);

        assertThat(result.intact()).isFalse();
        assertThat(result.brokenAtSequence()).isEqualTo(3);
    }

    @Test
    void deletingAnEntryInTheMiddleIsDetected() {
        List<LogEntity> entries = chain(4, 1);
        entries.remove(2);

        AuditHashChain.Verification result = AuditHashChain.verify(entries);

        assertThat(result.intact()).isFalse();
        assertThat(result.problem()).contains("Thiếu bản ghi giữa #2 và #4");
    }

    @Test
    void separatorsCannotBeShiftedBetweenFields() {
        LogEntity left = chain(1, 1).getFirst();
        LogEntity right = chain(1, 1).getFirst();
        left.setUserId("ab");
        left.setAction("c");
        right.setUserId("a");
        right.setAction("bc");

        assertThat(AuditHashChain.hash(left)).isNotEqualTo(AuditHashChain.hash(right));
    }
}
