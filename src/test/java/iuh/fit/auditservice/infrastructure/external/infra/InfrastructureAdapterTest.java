package iuh.fit.auditservice.infrastructure.external.infra;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InfrastructureAdapterTest {

    @Test
    void encodesRedisCommandsAsRespCountingUtf8Bytes() {
        assertThat(new String(InfrastructureAdapter.resp("AUTH", "mật"), StandardCharsets.UTF_8))
                .isEqualTo("*2\r\n$4\r\nAUTH\r\n$5\r\nmật\r\n");
    }

    @Test
    void reportsUnreachableInfrastructureAsDownInsteadOfThrowing() {
        InfrastructureAdapter adapter = new InfrastructureAdapter("127.0.0.1:1", "redis://127.0.0.1:1", "127.0.0.1:1", null);

        assertThat(adapter.postgres().status()).isEqualTo("DOWN");
        assertThat(adapter.redis().status()).isEqualTo("DOWN");
    }
}
