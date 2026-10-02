package iuh.fit.auditservice.infrastructure.persistence.repository;

import com.turkraft.springfilter.parser.FilterParser;
import iuh.fit.auditservice.application.dto.PageFilter;
import iuh.fit.auditservice.application.dto.response.base.PageResult;
import iuh.fit.auditservice.application.port.persistence.LogRepository;
import iuh.fit.auditservice.domain.Log;
import iuh.fit.auditservice.infrastructure.persistence.entity.LogEntity;
import iuh.fit.auditservice.infrastructure.persistence.mapper.LogEntityMapper;
import iuh.fit.auditservice.infrastructure.persistence.repository.converter.MongoCriteriaConverter;
import iuh.fit.auditservice.infrastructure.persistence.repository.jpa.JpaLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import iuh.fit.auditservice.application.dto.response.log.LogStatsResponseCommand;

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

    private long countStatus(LocalDateTime from, LocalDateTime to, int min, int maxExclusive) {
        return mongoTemplate.count(new Query(Criteria.where("timestamp").gte(from).lt(to)
                .and("statusCode").gte(min).lt(maxExclusive)), LogEntity.class);
    }
}
