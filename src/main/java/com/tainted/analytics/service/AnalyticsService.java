package com.tainted.analytics.service;

import com.tainted.analytics.domain.MoodPoint;
import com.tainted.analytics.domain.MoodPointRepository;
import com.tainted.analytics.event.DiaryCreatedEvent;
import com.tainted.analytics.event.MoodLoggedEvent;
import com.tainted.analytics.event.PostCreatedEvent;
import com.tainted.analytics.web.dto.GlobalAggregateResponse;
import com.tainted.analytics.web.dto.MoodPointResponse;
import com.tainted.analytics.web.dto.UserMoodResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private final MoodPointRepository repository;

    public AnalyticsService(MoodPointRepository repository) {
        this.repository = repository;
    }

    // ── Kafka 이벤트 처리 (idempotent: 같은 eventId 는 PK 충돌로 무시) ──

    @Transactional
    public void processDiaryCreated(DiaryCreatedEvent event) {
        saveIgnoreDuplicate(MoodPoint.fromDiaryCreated(event));
    }

    @Transactional
    public void processMoodLogged(MoodLoggedEvent event) {
        saveIgnoreDuplicate(MoodPoint.fromMoodLogged(event));
    }

    @Transactional
    public void processPostCreated(PostCreatedEvent event) {
        saveIgnoreDuplicate(MoodPoint.fromPostCreated(event));
    }

    /**
     * eventId == PK 이므로 동일 이벤트 재전달 시 PK 충돌이 발생한다.
     * DataIntegrityViolationException 을 정상 처리(중복 무시)하여 idempotency 보장.
     */
    private void saveIgnoreDuplicate(MoodPoint point) {
        if (!repository.existsById(point.getId())) {
            try {
                repository.save(point);
            } catch (DataIntegrityViolationException ignored) {
                // 동시 소비 또는 재전달로 인한 PK 중복 — 무시
            }
        }
    }

    // ── 조회 ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UserMoodResponse getUserMood(String userId) {
        List<MoodPoint> points = repository.findByUserIdOrderByOccurredAtAsc(userId);
        List<MoodPointResponse> dtos = points.stream()
                .map(p -> new MoodPointResponse(p.getId(), p.getSource(),
                        p.getLabel(), p.getScore(), p.getOccurredAt()))
                .toList();
        double avg = points.stream().mapToInt(MoodPoint::getScore).average().orElse(0.0);
        return new UserMoodResponse(userId, dtos, avg, points.size());
    }

    @Transactional(readOnly = true)
    public GlobalAggregateResponse getGlobal() {
        long total = repository.countAll();
        List<Object[]> rows = repository.countGroupedBySource();
        Map<String, Long> countBySource = new LinkedHashMap<>();
        for (Object[] row : rows) {
            countBySource.put((String) row[0], (Long) row[1]);
        }
        return new GlobalAggregateResponse(total, countBySource);
    }
}
