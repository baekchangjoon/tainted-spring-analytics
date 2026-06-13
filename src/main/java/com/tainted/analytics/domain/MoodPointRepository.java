package com.tainted.analytics.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MoodPointRepository extends JpaRepository<MoodPoint, String> {

    List<MoodPoint> findByUserIdOrderByOccurredAtAsc(String userId);

    @Query("SELECT COUNT(m) FROM MoodPoint m WHERE m.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT COALESCE(AVG(m.score), 0) FROM MoodPoint m WHERE m.userId = :userId")
    double avgScoreByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(m) FROM MoodPoint m")
    long countAll();

    @Query("SELECT m.source, COUNT(m) FROM MoodPoint m GROUP BY m.source")
    List<Object[]> countGroupedBySource();
}
