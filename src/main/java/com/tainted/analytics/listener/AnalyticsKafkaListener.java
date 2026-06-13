package com.tainted.analytics.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tainted.analytics.event.DiaryCreatedEvent;
import com.tainted.analytics.event.MoodLoggedEvent;
import com.tainted.analytics.event.PostCreatedEvent;
import com.tainted.analytics.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsKafkaListener.class);

    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    public AnalyticsKafkaListener(AnalyticsService analyticsService, ObjectMapper kafkaObjectMapper) {
        this.analyticsService = analyticsService;
        this.objectMapper = kafkaObjectMapper;
    }

    @KafkaListener(topics = "diary.created", groupId = "${spring.kafka.consumer.group-id}")
    public void onDiaryCreated(String payload) {
        try {
            DiaryCreatedEvent event = objectMapper.readValue(payload, DiaryCreatedEvent.class);
            analyticsService.processDiaryCreated(event);
        } catch (Exception e) {
            log.error("Failed to process diary.created payload: {}", payload, e);
        }
    }

    @KafkaListener(topics = "mood.logged", groupId = "${spring.kafka.consumer.group-id}")
    public void onMoodLogged(String payload) {
        try {
            MoodLoggedEvent event = objectMapper.readValue(payload, MoodLoggedEvent.class);
            analyticsService.processMoodLogged(event);
        } catch (Exception e) {
            log.error("Failed to process mood.logged payload: {}", payload, e);
        }
    }

    @KafkaListener(topics = "post.created", groupId = "${spring.kafka.consumer.group-id}")
    public void onPostCreated(String payload) {
        try {
            PostCreatedEvent event = objectMapper.readValue(payload, PostCreatedEvent.class);
            analyticsService.processPostCreated(event);
        } catch (Exception e) {
            log.error("Failed to process post.created payload: {}", payload, e);
        }
    }
}
