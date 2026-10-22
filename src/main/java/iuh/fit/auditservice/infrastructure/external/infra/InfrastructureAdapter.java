package iuh.fit.auditservice.infrastructure.external.infra;

import iuh.fit.auditservice.application.dto.response.system.InfrastructureResponseCommand.Check;
import iuh.fit.auditservice.application.dto.response.system.InfrastructureResponseCommand.ConsumerLag;
import iuh.fit.auditservice.application.port.monitoring.InfrastructurePort;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Kiểm tra trực tiếp từng hạ tầng bằng giao thức của chính nó, không chỉ mở cổng:
 * Postgres trả lời SSLRequest, Redis trả lời PING, MongoDB trả lời ping, Kafka trả danh sách broker.
 * Chưa có exporter Prometheus cho các thành phần này nên audit-service tự hỏi khi trang Giám sát mở.
 */
@Slf4j
public class InfrastructureAdapter implements InfrastructurePort {
    private static final int TIMEOUT_MS = 2_000;

    private final String postgresAddress;
    private final String redisUrl;
    private final String kafkaBootstrap;
    private final MongoTemplate mongoTemplate;
    /** Một Admin client dùng lại: tạo mới mất vài giây (nạp metadata), tạo lại sau khi lỗi. */
    private Admin admin;

    public InfrastructureAdapter(String postgresAddress, String redisUrl, String kafkaBootstrap, MongoTemplate mongoTemplate) {
        this.postgresAddress = postgresAddress;
        this.redisUrl = redisUrl;
        this.kafkaBootstrap = kafkaBootstrap;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Check> check() {
        return List.of(postgres(), kafka(), redis(), mongo());
    }

    Check postgres() {
        long started = System.nanoTime();
        String[] hostPort = postgresAddress.split(":");
        try (Socket socket = connect(hostPort[0], Integer.parseInt(hostPort[1]))) {
            // SSLRequest (độ dài 8, mã 80877103): Postgres thật luôn đáp một byte 'S' hoặc 'N'.
            socket.getOutputStream().write(new byte[]{0, 0, 0, 8, 0x04, (byte) 0xD2, 0x16, 0x2F});
            int reply = socket.getInputStream().read();
            if (reply == 'S' || reply == 'N') return up("PostgreSQL", started, postgresAddress);
            return down("PostgreSQL", "Cổng " + postgresAddress + " không phải PostgreSQL");
        } catch (Exception exception) {
            return down("PostgreSQL", "Không kết nối được " + postgresAddress + ": " + exception.getMessage());
        }
    }

    Check redis() {
        long started = System.nanoTime();
        URI uri = URI.create(redisUrl);
        int port = uri.getPort() > 0 ? uri.getPort() : 6379;
        String password = uri.getUserInfo() == null ? null : uri.getUserInfo().substring(uri.getUserInfo().indexOf(':') + 1);
        try (Socket socket = connect(uri.getHost(), port)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            if (password != null && !password.isEmpty()) {
                out.write(resp("AUTH", password));
                if (!readLine(in).startsWith("+")) return down("Redis", "Sai mật khẩu Redis");
            }
            out.write(resp("PING"));
            String reply = readLine(in);
            if (reply.startsWith("+PONG")) return up("Redis", started, uri.getHost() + ":" + port);
            return down("Redis", reply.startsWith("-NOAUTH") ? "Redis yêu cầu mật khẩu nhưng chưa cấu hình" : "Phản hồi lạ: " + reply);
        } catch (Exception exception) {
            return down("Redis", "Không kết nối được " + uri.getHost() + ":" + port + ": " + exception.getMessage());
        }
    }

    Check mongo() {
        long started = System.nanoTime();
        try {
            Document reply = mongoTemplate.executeCommand(new Document("ping", 1));
            return reply.get("ok") != null ? up("MongoDB", started, mongoTemplate.getDb().getName()) : down("MongoDB", "Ping không thành công");
        } catch (Exception exception) {
            return down("MongoDB", exception.getMessage());
        }
    }

    Check kafka() {
        long started = System.nanoTime();
        try {
            int brokers = admin().describeCluster().nodes().get(TIMEOUT_MS * 2L, TimeUnit.MILLISECONDS).size();
            return up("Kafka", started, brokers + " broker · " + kafkaBootstrap);
        } catch (Exception exception) {
            resetAdmin();
            return down("Kafka", "Không gọi được broker " + kafkaBootstrap + ": " + rootMessage(exception));
        }
    }

    @Override
    public List<ConsumerLag> consumerLag() {
        try {
            Admin admin = admin();
            Collection<ConsumerGroupListing> groups = admin.listConsumerGroups().all().get(TIMEOUT_MS * 2L, TimeUnit.MILLISECONDS);
            List<String> groupIds = groups.stream().map(ConsumerGroupListing::groupId).sorted().toList();
            if (groupIds.isEmpty()) return List.of();
            Map<String, ConsumerGroupDescription> descriptions = admin.describeConsumerGroups(groupIds).all()
                    .get(TIMEOUT_MS * 2L, TimeUnit.MILLISECONDS);

            // Offset đã xử lý của mọi group trong một lượt gọi.
            Map<String, ListConsumerGroupOffsetsSpec> specs = new HashMap<>();
            groupIds.forEach(groupId -> specs.put(groupId, new ListConsumerGroupOffsetsSpec()));
            Map<String, Map<TopicPartition, OffsetAndMetadata>> committed = admin.listConsumerGroupOffsets(specs).all()
                    .get(TIMEOUT_MS * 2L, TimeUnit.MILLISECONDS);
            Map<TopicPartition, OffsetSpec> latestRequest = new HashMap<>();
            committed.values().forEach(offsets -> offsets.keySet().forEach(partition -> latestRequest.put(partition, OffsetSpec.latest())));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest = latestRequest.isEmpty() ? Map.of()
                    : admin.listOffsets(latestRequest).all().get(TIMEOUT_MS * 2L, TimeUnit.MILLISECONDS);

            List<ConsumerLag> result = new ArrayList<>();
            for (String groupId : groupIds) {
                long lag = 0;
                List<String> topics = new ArrayList<>();
                for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committed.getOrDefault(groupId, Map.of()).entrySet()) {
                    if (entry.getValue() == null) continue;
                    if (!topics.contains(entry.getKey().topic())) topics.add(entry.getKey().topic());
                    ListOffsetsResult.ListOffsetsResultInfo end = latest.get(entry.getKey());
                    if (end != null) lag += Math.max(0, end.offset() - entry.getValue().offset());
                }
                ConsumerGroupDescription description = descriptions.get(groupId);
                result.add(new ConsumerLag(groupId, description == null ? "UNKNOWN" : String.valueOf(description.groupState()),
                        description == null ? 0 : description.members().size(), topics.stream().sorted().toList(), lag));
            }
            result.sort((a, b) -> Long.compare(b.lag(), a.lag()));
            return result;
        } catch (Exception exception) {
            resetAdmin();
            log.warn("Không đọc được consumer lag từ Kafka: {}", rootMessage(exception));
            return List.of();
        }
    }

    private synchronized Admin admin() {
        if (admin != null) return admin;
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap);
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_MS * 2);
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, TIMEOUT_MS * 3);
        properties.put(AdminClientConfig.SOCKET_CONNECTION_SETUP_TIMEOUT_MAX_MS_CONFIG, TIMEOUT_MS);
        admin = Admin.create(properties);
        return admin;
    }

    private synchronized void resetAdmin() {
        if (admin == null) return;
        admin.close(Duration.ofSeconds(1));
        admin = null;
    }

    private static Socket connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        return socket;
    }

    /** Lệnh Redis dạng RESP: *n, rồi từng tham số $len. */
    static byte[] resp(String... parts) {
        StringBuilder command = new StringBuilder("*").append(parts.length).append("\r\n");
        for (String part : parts) {
            command.append('$').append(part.getBytes(StandardCharsets.UTF_8).length).append("\r\n").append(part).append("\r\n");
        }
        return command.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int next;
        while ((next = in.read()) != -1 && next != '\n') {
            if (next != '\r') line.append((char) next);
        }
        return line.toString();
    }

    private static Check up(String name, long startedNanos, String detail) {
        return new Check(name, "UP", Math.max(1, (System.nanoTime() - startedNanos) / 1_000_000), detail);
    }

    private static Check down(String name, String detail) {
        return new Check(name, "DOWN", null, detail);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
