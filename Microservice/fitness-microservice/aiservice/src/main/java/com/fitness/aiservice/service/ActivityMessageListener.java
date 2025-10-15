package com.fitness.aiservice.service;

import com.fitness.activityservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityMessageListener {

//    @Value("{kafka.topic.name}")
//    private String topicName;

    @KafkaListener(topics= "{kafka.topic.name}" , groupId = "activity-processor-group")
    public void processActivity(Activity activity){
        log.info("Received activity for processing: {}", activity.getUserId());
    }
}
