package com.example.coffee_hrm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrainingClassEndScheduler {

    private final TrainingService trainingService;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Ho_Chi_Minh")
    public void notifyEndedClasses() {
        trainingService.notifyEndedClasses();
    }
}
