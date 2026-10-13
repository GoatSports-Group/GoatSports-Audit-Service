package iuh.fit.auditservice.infrastructure.persistence.repository;

import com.turkraft.springfilter.parser.FilterParser;
import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.dto.response.log.FailedLoginBurstResponseCommand;
import iuh.fit.auditservice.application.dto.response.log.LogStatsResponseCommand;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.domain.Log;
import iuh.fit.auditservice.infrastructure.persistence.entity.LogEntity;
import iuh.fit.auditservice.infrastructure.persistence.mapper.LogEntityMapper;
import iuh.fit.auditservice.infrastructure.persistence.repository.converter.MongoCriteriaConverter;
import iuh.fit.auditservice.infrastructure.persistence.repository.jpa.JpaLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LogRepositoryImpl implements LogRepository {
    private final MongoTemplate mongoTemplate;
    private final JpaLogRepository jpaLogRepository;
    private final LogEntityMapper logEntityMapper;
    private final FilterParser filterParser;
    private final MongoCriteriaConverter mongoCriteriaConverter;

    @Override
    public void save(Log log) {
        LogEntity logEntity = logEntityMapper.createEntity(log);
        jpaLogRepository.save(logEntity);
    }

    @Override
    public boolean saveIfAbsent(Log log) {
        LogEntity logEntity = logEntityMapper.createEntity(log);
        try {
            // Dựa vào unique index eventId thay vì check-then-insert để không hở race giữa hai consumer.
            mongoTemplate.insert(logEntity);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
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
        List<Long> hourly = new java.util.ArrayList<>(hours);
        for (int hour = 0; hour < hours; hour++) {
            LocalDateTime start = from.plusHours(hour);
            LocalDateTime end = start.plusHours(1).isBefore(to) ? start.plusHours(1) : to;
            hourly.add(mongoTemplate.count(new Query(Criteria.where("timestamp").gte(start).lt(end)), LogEntity.class));
        }
        return new LogStatsResponseCommand(from, to, total, s4xx + s5xx, s2xx, s3xx, s4xx, s5xx, users, actions, hourly);
    }

    @Override
    public long deleteOlderThan(LocalDateTime cutoff) {
        return mongoTemplate.remove(new Query(Criteria.where("timestamp").lt(cutoff)), LogEntity.class).getDeletedCount();
    }

    /** Một document mỗi tài khoản (_id = tài khoản): lần xử lý gần nhất. */
    private static final String RESOLUTIONS = "failed_login_resolutions";

    @Override
    public void resolveFailedLogins(String account, LocalDateTime at, String resolvedBy) {
        mongoTemplate.upsert(new Query(Criteria.where("_id").is(account)),
                new org.springframework.data.mongodb.core.query.Update().set("resolvedAt", at).set("resolvedBy", resolvedBy),
                RESOLUTIONS);
    }

    @Override
    public java.util.Map<String, LocalDateTime> failedLoginResolutions(java.util.Collection<String> accounts) {
        java.util.Map<String, LocalDateTime> result = new java.util.HashMap<>();
        mongoTemplate.find(new Query(Criteria.where("_id").in(accounts)), org.bson.Document.class, RESOLUTIONS)
                .forEach(row -> result.put(row.getString("_id"), toLocal(row.get("resolvedAt"))));
        result.values().removeIf(java.util.Objects::isNull);
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
        return mongoTemplate.aggregate(aggregation, LogEntity.class, org.bson.Document.class).getMappedResults().stream()
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
        return value instanceof java.util.Date date
                ? LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault()) : null;
    }

    private long countStatus(LocalDateTime from, LocalDateTime to, int min, int maxExclusive) {
        return mongoTemplate.count(new Query(Criteria.where("timestamp").gte(from).lt(to)
                .and("statusCode").gte(min).lt(maxExclusive)), LogEntity.class);
    }
}
