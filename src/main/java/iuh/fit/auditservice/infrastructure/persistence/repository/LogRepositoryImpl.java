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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
}
