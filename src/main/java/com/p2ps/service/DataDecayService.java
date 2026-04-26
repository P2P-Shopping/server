package com.p2ps.service;

import com.p2ps.repository.StoreInventoryMapRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class DataDecayService {

    private final StoreInventoryMapRepository repository;

    @Value("${data-decay.enabled:true}")
    private boolean dataDecayEnabled = true;

    @Value("${data-decay.penalty:0.02}")
    private double penalty = 0.02d;

    @Value("${data-decay.cutoff-days:14}")
    private long cutoffDays = 14L;

    @Value("${data-decay.min-confidence-floor:0.15}")
    private double minConfidenceFloor = 0.15d;

    public DataDecayService(StoreInventoryMapRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${data-decay.cron:0 0 3 * * ?}", zone = "${data-decay.zone:UTC}")
    public void executeDataDecay() {
        if (!dataDecayEnabled) {
            log.debug("Data decay is disabled by configuration.");
            return;
        }

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(cutoffDays);
        int updatedRecords = repository.applyDecayToOldRecords(penalty, cutoffDate, minConfidenceFloor);

        if (log.isInfoEnabled()) {
            log.info(">>> DATA DECAY COMPLETE: {} records updated (penalty={}, cutoffDays={}, floor={}). <<<",
                    updatedRecords,
                    penalty,
                    cutoffDays,
                    minConfidenceFloor);
        }
    }
}