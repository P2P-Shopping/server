package com.p2ps.service;

import com.p2ps.repository.StoreInventoryMapRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataDecayServiceTest {

    // Simulăm (Mock) baza de date ca să nu o apelăm pe bune
    @Mock
    private StoreInventoryMapRepository repository;

    // Injectăm mock-ul în serviciul tău real
    @InjectMocks
    private DataDecayService dataDecayService;

    @Test
    void shouldExecuteDataDecayAndCallRepositoryWithCorrectPenalty() {
        Double expectedPenalty = 0.1;

        // Când serviciul va apela repository-ul, îi spunem mock-ului să pretindă că a modificat 5 rânduri
        when(repository.applyDecayToOldRecords(eq(expectedPenalty), any(LocalDateTime.class)))
                .thenReturn(5);

        // Aici simulăm că e ora 03:00 dimineața și se declanșează Cron Job-ul
        dataDecayService.executeDataDecay();

        // Verificăm dacă serviciul a calculat penalizarea corectă și a trimis-o către baza de date exact o singură dată
        verify(repository).applyDecayToOldRecords(eq(expectedPenalty), any(LocalDateTime.class));
    }
}