package iuh.fit.auditservice.application.service.impl;

import iuh.fit.auditservice.application.dto.response.log.LogStatsResponseCommand;
import iuh.fit.auditservice.application.exception.ServiceException;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogServiceImplTest {
    private final LogRepository repository = mock(LogRepository.class);
    private final LogServiceImpl service = new LogServiceImpl(repository, (eventId, title, content, type, ref) -> { },
            Clock.systemDefaultZone(), 180, 15, 5);
    private final LocalDateTime from = LocalDateTime.of(2026, 10, 2, 17, 0);

    @Test
    void oneDayAsksTheRepositoryFor24HourlyBuckets() throws ServiceException {
        LogStatsResponseCommand stats = new LogStatsResponseCommand(from, from.plusDays(1), 3, 1, 2, 0, 1, 0, 2,
                List.of("LOGIN"), List.of(1L, 2L));
        when(repository.stats(from, from.plusDays(1), 24)).thenReturn(stats);

        assertThat(service.getStats(from, from.plusDays(1))).isSameAs(stats);
    }

    @Test
    void rejectsEmptyReversedOrTooLongRanges() {
        assertThatThrownBy(() -> service.getStats(from, from)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.getStats(from, from.minusHours(1))).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.getStats(from, from.plusDays(8))).isInstanceOf(ServiceException.class)
                .hasMessageContaining("7 ngày");
        verify(repository, never()).stats(any(), any(), anyInt());
    }
}
