package com.p2ps.service;

import com.p2ps.repository.StoreInventoryMapRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Slf4j
@Service
public class DataDecayService {

    private final StoreInventoryMapRepository repository;

    public DataDecayService(StoreInventoryMapRepository repository) {
        this.repository = repository;
    }

    // Cron-ul ăsta înseamnă: "Rulează în fiecare noapte la ora 03:00 AM"
    @Scheduled(cron = "${data-decay.cron:0 0 3 * * ?}", zone = "${data-decay.zone:UTC}")
    public void executeDataDecay() {
        log.info(">>> DATA DECAY STARTED <<<");
        // Definim logica: penalizăm cu 0.1 puncte tot ce e mai vechi de 7 zile
        Double penalty = 0.1;
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);

        int updatedRecords = repository.applyDecayToOldRecords(penalty, cutoffDate);

        if (log.isInfoEnabled()) {
            log.info(new StringBuilder().append(">>> DATA DECAY COMPLETE: ").append(updatedRecords).append(" records updated. <<<").toString());
        }
    }
}