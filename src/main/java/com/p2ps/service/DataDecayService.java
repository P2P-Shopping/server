package com.p2ps.service;

import com.p2ps.repository.StoreInventoryMapRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class DataDecayService {

    private final StoreInventoryMapRepository repository;

    public DataDecayService(StoreInventoryMapRepository repository) {
        this.repository = repository;
    }

    // Cron-ul ăsta înseamnă: "Rulează în fiecare noapte la ora 03:00 AM"
    @Scheduled(cron = "0 0 3 * * ?")
    public void executeDataDecay() {
        System.out.println(">>> STARTING DATA DECAY CRON JOB <<<");

        // Definim logica: penalizăm cu 0.1 puncte tot ce e mai vechi de 7 zile
        Double penalty = 0.1;
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);

        int updatedRecords = repository.applyDecayToOldRecords(penalty, cutoffDate);

        System.out.println(">>> DATA DECAY COMPLETE: " + updatedRecords + " records updated. <<<");
    }
}