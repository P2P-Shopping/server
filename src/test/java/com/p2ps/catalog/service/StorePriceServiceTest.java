package com.p2ps.catalog.service;

import com.p2ps.catalog.model.ProductCatalog;
import com.p2ps.catalog.model.StorePrice;
import com.p2ps.catalog.repository.StorePriceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorePriceServiceTest {

    @Mock
    private StorePriceRepository storePriceRepository;

    @Test
    void recordStorePriceShouldCreateNewRecordWhenMissing() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-18T10:15:30Z"), ZoneOffset.UTC);
        StorePriceService service = new StorePriceService(storePriceRepository, fixedClock);
        ProductCatalog catalog = new ProductCatalog();
        catalog.setId(UUID.randomUUID());

        when(storePriceRepository.findByCatalogIdAndStoreNameIgnoreCase(catalog.getId(), "Mega")).thenReturn(Optional.empty());
        when(storePriceRepository.save(any(StorePrice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StorePrice result = service.recordStorePrice(catalog, "Mega", BigDecimal.valueOf(9.99));

        assertThat(result.getCatalogItem()).isEqualTo(catalog);
        assertThat(result.getStoreName()).isEqualTo("Mega");
        assertThat(result.getPrice()).isEqualByComparingTo("9.99");
        assertThat(result.getLastUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 18, 10, 15, 30));
    }

    @Test
    void recordStorePriceShouldUpdateExistingRecord() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-18T12:00:00Z"), ZoneOffset.UTC);
        StorePriceService service = new StorePriceService(storePriceRepository, fixedClock);
        ProductCatalog catalog = new ProductCatalog();
        catalog.setId(UUID.randomUUID());
        StorePrice existing = new StorePrice();
        existing.setId(UUID.randomUUID());
        existing.setStoreName("Mega");
        existing.setPrice(BigDecimal.ONE);

        when(storePriceRepository.findByCatalogIdAndStoreNameIgnoreCase(catalog.getId(), "Mega")).thenReturn(Optional.of(existing));
        when(storePriceRepository.save(existing)).thenReturn(existing);

        StorePrice result = service.recordStorePrice(catalog, "Mega", BigDecimal.valueOf(15.50));

        assertThat(result).isSameAs(existing);
        assertThat(existing.getCatalogItem()).isEqualTo(catalog);
        assertThat(existing.getPrice()).isEqualByComparingTo("15.50");
        assertThat(existing.getLastUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 18, 12, 0));
    }

    @Test
    void recordStorePriceShouldSkipIncompleteInput() {
        StorePriceService service = new StorePriceService(storePriceRepository, Clock.systemUTC());

        assertThat(service.recordStorePrice(null, "Mega", BigDecimal.ONE)).isNull();
        assertThat(service.recordStorePrice(new ProductCatalog(), "Mega", BigDecimal.ONE)).isNull();
        assertThat(service.recordStorePrice(new ProductCatalog(), " ", BigDecimal.ONE)).isNull();
        assertThat(service.recordStorePrice(new ProductCatalog(), "Mega", null)).isNull();

        verify(storePriceRepository, never()).save(any());
    }

    @Test
    void deletePricesOlderThanDaysShouldUseCalculatedCutoff() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-18T00:00:00Z"), ZoneOffset.UTC);
        StorePriceService service = new StorePriceService(storePriceRepository, fixedClock);

        service.deletePricesOlderThanDays(30);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(storePriceRepository).deleteByLastUpdatedAtBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 4, 18, 0, 0));
    }
}
