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
import static org.mockito.Mockito.never;
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
        Double expectedPenalty = 0.02;
        Double expectedMinConfidenceFloor = 0.15;

        when(repository.applyDecayToOldRecords(eq(expectedPenalty), any(LocalDateTime.class), eq(expectedMinConfidenceFloor)))
                .thenReturn(5);

        dataDecayService.executeDataDecay();

        verify(repository).applyDecayToOldRecords(eq(expectedPenalty), any(LocalDateTime.class), eq(expectedMinConfidenceFloor));
    }

    @Test
    void shouldSkipDecayWhenFeatureIsDisabled() throws Exception {
        java.lang.reflect.Field enabledField = DataDecayService.class.getDeclaredField("dataDecayEnabled");
        enabledField.setAccessible(true);
        enabledField.set(dataDecayService, false);

        dataDecayService.executeDataDecay();

        verify(repository, never()).applyDecayToOldRecords(any(Double.class), any(LocalDateTime.class), any(Double.class));
    }
}