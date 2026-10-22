package iuh.fit.auditservice.infrastructure.persistence.repository;

import com.turkraft.springfilter.parser.FilterParser;
import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.dto.response.log.ChainVerificationResponseCommand;
import iuh.fit.auditservice.application.dto.response.log.FailedLoginBurstResponseCommand;
import iuh.fit.auditservice.application.dto.response.log.LogStatsResponseCommand;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.domain.Log;
import iuh.fit.auditservice.infrastructure.persistence.chain.AuditHashChain;
import iuh.fit.auditservice.infrastructure.persistence.entity.LogEntity;
import iuh.fit.auditservice.infrastructure.persistence.mapper.LogEntityMapper;
import iuh.fit.auditservice.infrastructure.persistence.repository.converter.MongoCriteriaConverter;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class LogRepositoryImpl implements LogRepository {
    private final MongoTemplate mongoTemplate;
    private final LogEntityMapper logEntityMapper;
    private final FilterParser filterParser;
    private final MongoCriteriaConverter mongoCriteriaConverter;

    @Override
    public void save(Log log) {
        appendToChain(logEntityMapper.createEntity(log));
    }

    @Override
    public boolean saveIfAbsent(Log log) {
        try {
            // Dựa vào unique index eventId thay vì check-then-insert để không hở race giữa hai consumer.
            appendToChain(logEntityMapper.createEntity(log));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public ChainVerificationResponseCommand verifyChain() {
        Query chained = new Query(Criteria.where("sequence").exists(true)).with(Sort.by(Sort.Direction.ASC, "sequence"));
        AuditHashChain.Verification result;
        try (Stream<LogEntity> entries = mongoTemplate.stream(chained, LogEntity.class)) {
            result = AuditHashChain.verify(entries::iterator);
        }
        long unchained = mongoTemplate.count(new Query(Criteria.where("sequence").exists(false)), LogEntity.class);
        return new ChainVerificationResponseCommand(result.checked(), unchained, result.intact(),
                result.brokenAtSequence(), result.problem());
    }

    /**
     * Mọi bản ghi mới đều nối vào chuỗi hash ở đây. Hai luồng ghi cùng lúc tính ra cùng sequence thì unique index
     * chặn một bên; bên đó đọc lại đầu chuỗi rồi thử lại. Trùng eventId thì ném ra cho {@link #saveIfAbsent}.
     */
    // ponytail: mỗi lần ghi thêm một truy vấn đọc đầu chuỗi và cạnh tranh trên một chuỗi chung; đủ cho lưu lượng nhật ký
    // hiện tại, cần nhiều hơn thì tách chuỗi theo partition Kafka.
    private void appendToChain(LogEntity entity) {
        if (entity.getTimestamp() != null) {
            // Mongo chỉ lưu tới mili giây: cắt trước khi hash để đọc lại vẫn khớp.
            entity.setTimestamp(entity.getTimestamp().truncatedTo(ChronoUnit.MILLIS));
        }
        for (int attempt = 1; ; attempt++) {
            LogEntity tip = mongoTemplate.findOne(new Query(Criteria.where("sequence").exists(true))
                    .with(Sort.by(Sort.Direction.DESC, "sequence")).limit(1), LogEntity.class);
            entity.setLogId(null);
            entity.setSequence(tip == null ? 1L : tip.getSequence() + 1);
            entity.setPreviousHash(tip == null ? AuditHashChain.GENESIS : tip.getRecordHash());
            entity.setRecordHash(AuditHashChain.hash(entity));
            try {
                mongoTemplate.insert(entity);
                return;
            } catch (DuplicateKeyException duplicate) {
                boolean sequenceTaken = String.valueOf(duplicate.getMessage()).contains("chain_sequence_idx");
                if (!sequenceTaken || attempt >= 50) throw duplicate;
            }
        }
    }

    @Override
    public PageResult<Log> findAll(PageFilter filter) {
        int pageNum = Math.max(0, filter.page() - 1);
        int pageSize = filter.size() > 0 ? filter.size() : 10;
        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "timestamp"));

        Query query;

        if (StringUtils.hasText(filter.filter())) {
            query = mongoCriteriaConverter.convert(filterParser.parse(filter.filter()));
        } else {
            query = new Query();
        }

        long total = mongoTemplate.count(query, LogEntity.class);

        query.with(pageable);

        List<Log> items = mongoTemplate.find(query, LogEntity.class)
                .stream()
                .map(logEntityMapper::toDomain)
                .toList();

        return new PageResult<>(
                items,
                total,
                filter.page(),
                filter.size(),
                (int) Math.ceil((double) total / pageSize)
        );
    }

    // ponytail: 6 + (so gio) lan count/distinct tren index timestamp. Du cho trang admin xem 1 ngay (~30 truy van);
    // can khoang dai hon thi chuyen sang mot aggregation $group theo gio.
    @Override
    public LogStatsResponseCommand stats(LocalDateTime from, LocalDateTime to, int hours) {
        Criteria range = Criteria.where("timestamp").gte(from).lt(to);
        long total = mongoTemplate.count(new Query(range), LogEntity.class);
        long s2xx = countStatus(from, to, 200, 300);
        long s3xx = countStatus(from, to, 300, 400);
        long s4xx = countStatus(from, to, 400, 500);
        long s5xx = countStatus(from, to, 500, 1000);
        List<String> actions = mongoTemplate.findDistinct(new Query(range), "action", LogEntity.class, String.class).stream()
                .filter(StringUtils::hasText).sorted().toList();
        long users = mongoTemplate.findDistinct(new Query(range), "userId", LogEntity.class, String.class).stream()
                .filter(id -> StringUtils.hasText(id) && !"anonymous".equalsIgnoreCase(id)).count();
        List<Long> hourly = new ArrayList<>(hours);
        for (int hour = 0; hour < hours; hour++) {
            LocalDateTime start = from.plusHours(hour);
            LocalDateTime end = start.plusHours(1).isBefore(to) ? start.plusHours(1) : to;
            hourly.add(mongoTemplate.count(new Query(Criteria.where("timestamp").gte(start).lt(end)), LogEntity.class));
        }
        return new LogStatsResponseCommand(from, to, total, s4xx + s5xx, s2xx, s3xx, s4xx, s5xx, users, actions, hourly);
    }

    @Override
    public long deleteOlderThan(LocalDateTime cutoff) {
        // Chỉ cắt phần đầu liền mạch của chuỗi hash: event đến trễ có timestamp cũ nhưng sequence mới, xóa nó sẽ để lại
        // lỗ giữa chuỗi và bị báo nhầm là mất bản ghi. Bản đó được dọn ở lần sau, khi đầu chuỗi đã qua nó.
        // Hết hạn cả chuỗi thì vẫn giữ bản cuối làm đầu chuỗi, để sequence và hash nối tiếp chứ không bắt lại từ 1.
        LogEntity firstKept = mongoTemplate.findOne(new Query(Criteria.where("sequence").exists(true)
                .and("timestamp").gte(cutoff)).with(Sort.by(Sort.Direction.ASC, "sequence")).limit(1), LogEntity.class);
        if (firstKept == null) {
            firstKept = mongoTemplate.findOne(new Query(Criteria.where("sequence").exists(true))
                    .with(Sort.by(Sort.Direction.DESC, "sequence")).limit(1), LogEntity.class);
        }
        Criteria outsideChain = Criteria.where("sequence").exists(false);
        Criteria chainPrefix = firstKept == null
                ? outsideChain
                : Criteria.where("sequence").lt(firstKept.getSequence());
        Query expired = new Query(new Criteria().andOperator(
                Criteria.where("timestamp").lt(cutoff), new Criteria().orOperator(outsideChain, chainPrefix)));
        return mongoTemplate.remove(expired, LogEntity.class).getDeletedCount();
    }

    /** Một document mỗi tài khoản (_id = tài khoản): lần xử lý gần nhất. */
    private static final String RESOLUTIONS = "failed_login_resolutions";

    @Override
    public void resolveFailedLogins(String account, LocalDateTime at, String resolvedBy) {
        mongoTemplate.upsert(new Query(Criteria.where("_id").is(account)),
                new Update().set("resolvedAt", at).set("resolvedBy", resolvedBy),
                RESOLUTIONS);
    }

    @Override
    public Map<String, LocalDateTime> failedLoginResolutions(Collection<String> accounts) {
        Map<String, LocalDateTime> result = new HashMap<>();
        mongoTemplate.find(new Query(Criteria.where("_id").in(accounts)), Document.class, RESOLUTIONS)
                .forEach(row -> result.put(row.getString("_id"), toLocal(row.get("resolvedAt"))));
        result.values().removeIf(Objects::isNull);
        return result;
    }

    @Override
    public List<FailedLoginBurstResponseCommand> failedLoginBursts(LocalDateTime since, int threshold) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("action").is("LOGIN").and("statusCode").gte(400).and("timestamp").gte(since)),
                Aggregation.sort(Sort.Direction.ASC, "timestamp"),
                Aggregation.group("userId").count().as("attempts").first("timestamp").as("firstAt")
                        .last("timestamp").as("lastAt").last("ipAddress").as("lastIp"),
                Aggregation.match(Criteria.where("attempts").gte(threshold)),
                Aggregation.sort(Sort.Direction.DESC, "attempts"),
                Aggregation.limit(50));
        return mongoTemplate.aggregate(aggregation, LogEntity.class, Document.class).getMappedResults().stream()
                .map(row -> new FailedLoginBurstResponseCommand(
                        String.valueOf(row.get("_id")),
                        ((Number) row.get("attempts")).longValue(),
                        toLocal(row.get("firstAt")),
                        toLocal(row.get("lastAt")),
                        row.getString("lastIp")))
                .toList();
    }

    /** Mongo trả Date (UTC) cho trường LocalDateTime; đổi lại giờ máy chủ như lúc ghi. */
    private static LocalDateTime toLocal(Object value) {
        return value instanceof Date date
                ? LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()) : null;
    }

    private long countStatus(LocalDateTime from, LocalDateTime to, int min, int maxExclusive) {
        return mongoTemplate.count(new Query(Criteria.where("timestamp").gte(from).lt(to)
                .and("statusCode").gte(min).lt(maxExclusive)), LogEntity.class);
    }
}
