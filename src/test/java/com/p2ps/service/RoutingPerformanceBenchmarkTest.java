package com.p2ps.service;

import com.p2ps.controller.RoutePoint;
import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

// 1. Am scos @SpringBootTest și folosim DOAR Mockito!
@ExtendWith(MockitoExtension.class)
@Tag("manual")
class RoutingPerformanceBenchmarkTest {

    // 2. Mock-uim doar dependințele, fără Spring Context
    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RoutingAsyncService asyncService;

    @Mock
    private StringRedisTemplate redis;

    private RoutingService routingService;

    private static final String STORE_ID = "11111111-2222-3333-4444-555555555555";

    @BeforeEach
    void setUp() {
        // 3. Instanțiem serviciul manual, trecându-i mock-urile (Injectare Manuală)
        RouteOptimizer optimizer = new RouteOptimizer();
        routingService = new RoutingService(jdbcTemplate, optimizer, asyncService, redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void benchmark_ParallelExecution_NearestNeighbor_vs_3Opt() {
        // --- 1. MOCKING DATABASE CALLS ---

        // Generăm 25 de produse mockate
        List<RoutingService.ProductLocation> mockLocations = IntStream.range(0, 25)
                .mapToObj(i -> new RoutingService.ProductLocation(
                        "item_" + i,
                        "Produs " + i,
                        47.150 + (i * 0.0001),
                        27.580 + (i * 0.0001),
                        1.0))
                .toList();

        List<String> productIds = mockLocations.stream()
                .map(RoutingService.ProductLocation::itemId)
                .toList();

        // Mock pentru găsirea magazinului în RoutingService (findStoreForUser)
        when(jdbcTemplate.queryForList(
                anyString(),
                eq(String.class),
                anyDouble(), anyDouble()
        )).thenReturn(List.of(STORE_ID));

        // Mock pentru queryInventoryMap din RoutingService
        when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
        )).thenReturn(mockLocations);

        // --- 2. EXECUȚIA TESTULUI ---

        // Verificăm că avem datele masive (> 20 iteme)
        assertTrue(productIds.size() >= 20, "Nu sunt destule iteme mockate!");

        // Setăm coordonatele de test (ex. Intrarea în magazin)
        double userLat = 47.156;
        double userLng = 27.587;

        int numSimulations = 50; // Rulăm 50 de request-uri concurente pentru a simula "în paralel"

        System.out.println("\n=========================================================================");
        System.out.println("START STRESS TEST: " + numSimulations + " useri simultani cauta ruta pentru " + productIds.size() + " produse.");
        System.out.println("=========================================================================");

        long startTimeMs = System.currentTimeMillis();

        // Executăm cererile ÎN PARALEL (Multithreading)
        IntStream.range(0, numSimulations).parallel().forEach(ignored -> {
            RoutingRequest request = new RoutingRequest(userLat, userLng, productIds, null, 0); // Eager routing (lazyN=0) // wait
            RoutingResponse response = routingService.calculateOptimalRoute(request);
            assertTrue(response.getTotalDistanceMeters() > 0, "Distanța trebuie să fie calculată");
        });

        long totalTimeMs = System.currentTimeMillis() - startTimeMs;

        // --- 3. EXTRAGEM METRICILE PENTRU AFIȘARE ---
        RoutePoint startPoint = new RoutePoint("start", "User", userLat, userLng);
        RouteOptimizer optimizer = new RouteOptimizer();
        List<RoutePoint> points = mockLocations.stream()
                .map(l -> new RoutePoint(l.itemId(), l.name(), l.lat(), l.lng()))
                .toList();

        // NN Pur
        List<RoutePoint> nnRoute = optimizer.nearestNeighborTSP(startPoint, points);
        nnRoute.addFirst(startPoint);
        double distNn = optimizer.routeDistance(nnRoute);

        // 3-Opt Pur
        List<RoutePoint> threeOptRoute = optimizer.threeOptImprove(nnRoute);
        double dist3Opt = optimizer.routeDistance(threeOptRoute);

        double improvement = distNn > 0 ? ((distNn - dist3Opt) / distNn) * 100 : 0.0;

        // --- 4. AFIȘARE CONSOLĂ ---
        System.out.println("\n --- REZULTATE BENCHMARK DEMO ---");
        System.out.printf("Dimensiune Lista: %d produse reale%n", productIds.size());
        System.out.printf("Timp execuție paralelă (%d requesturi simultane): %d ms%n", numSimulations, totalTimeMs);
        System.out.println("-------------------------------------------------------------------------");
        System.out.printf(" Distanta generata cu Nearest Neighbor: %.2f metri%n", distNn);
        System.out.printf(" Distanta optimizata cu 3-Opt: %.2f metri%n", dist3Opt);
        System.out.printf("CONCLUZIE: 3-Opt a redus distanța cu %.2f%% față de NN!%n", improvement);
        System.out.println("=========================================================================\n");

        // Asertare matematică finală
        assertTrue(dist3Opt <= distNn + 1e-9, "3-Opt trebuie sa ofere o ruta cel putin la fel de buna ca NN");
    }
}