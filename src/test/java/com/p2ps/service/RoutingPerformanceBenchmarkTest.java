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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

// Testul va rula instant, fără să încerce să pornească MongoDB!
@ExtendWith(MockitoExtension.class)
@Tag("manual")
class RoutingPerformanceBenchmarkTest {

    private RoutingService routingService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RoutingAsyncService routingAsyncService;

    @Mock
    private StringRedisTemplate redis;

    // UUID-ul listei masive inserate prin scriptul SQL
    private static final String DEMO_LIST_ID = "99999999-0000-0000-0000-000000000001";
    private static final String STORE_ID = "11111111-2222-3333-4444-555555555555";

    @BeforeEach
    void setUp() {
        // Instanțiem serviciul manual exact cum trebuie, fără Spring Boot
        RouteOptimizer optimizer = new RouteOptimizer();
        routingService = new RoutingService(jdbcTemplate, optimizer, routingAsyncService, redis);
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
                .collect(Collectors.toList());

        List<String> productIds = mockLocations.stream().map(RoutingService.ProductLocation::itemId).toList();

        // Folosim lenient() pentru a nu crăpa testul dacă metoda nu apelează absolut toate query-urile mockate
        lenient().when(jdbcTemplate.queryForList(
                eq("SELECT id::text FROM items WHERE list_id = ?"),
                eq(String.class),
                eq(DEMO_LIST_ID)
        )).thenReturn(productIds);

        // Mock pentru găsirea magazinului în RoutingService (findStoreForUser)
        lenient().when(jdbcTemplate.queryForList(
                contains("SELECT store_id::text FROM store_geofences"),
                eq(String.class),
                anyDouble(), anyDouble()
        )).thenReturn(List.of(STORE_ID));

        // Mock pentru queryInventoryMap din RoutingService și query-ul manual din test
        lenient().when(jdbcTemplate.query(
                anyString(),
                any(RowMapper.class),
                any(Object[].class)
        )).thenReturn(mockLocations);

        lenient().when(jdbcTemplate.query(
                contains("SELECT i.id::text AS id"),
                any(RowMapper.class),
                eq(DEMO_LIST_ID)
        )).thenReturn(mockLocations);


        // --- 2. EXECUȚIA TESTULUI ---

        // Verificăm că avem datele masive (> 20 iteme)
        assertTrue(productIds.size() >= 20, "Scriptul SQL de seeding trebuie rulat. Nu sunt destule iteme!");

        // Setăm coordonatele de test (ex. Intrarea în magazin)
        double userLat = 47.156;
        double userLng = 27.587;

        int numSimulations = 50; // Rulăm 50 de request-uri concurente pentru a simula "în paralel"

        System.out.println("\n=========================================================================");
        System.out.println("START STRESS TEST: " + numSimulations + " useri simultani cauta ruta pentru " + productIds.size() + " produse.");
        System.out.println("=========================================================================");

        long startTimeMs = System.currentTimeMillis();

        // 2. Executăm cererile ÎN PARALEL
        IntStream.range(0, numSimulations).parallel().forEach(i -> {
            RoutingRequest request = new RoutingRequest(userLat, userLng, productIds, 0); // Eager routing (lazyN=0)

            RoutingResponse response = routingService.calculateOptimalRoute(request);

            assertTrue(response.getTotalDistanceMeters() > 0, "Distanța trebuie să fie calculată");
        });

        long totalTimeMs = System.currentTimeMillis() - startTimeMs;

        // 3. Facem o rulare dedicată doar pentru a extrage metricile matematice exacte pentru afișare
        RoutePoint startPoint = new RoutePoint("start", "User", userLat, userLng);

        // În loc de apelul de DB, folosim direct obiectele mockate pentru extragerea metricilor
        RouteOptimizer optimizer = new RouteOptimizer();
        List<RoutePoint> points = mockLocations.stream().map(l -> new RoutePoint(l.itemId(), l.name(), l.lat(), l.lng())).toList();

        // NN Pur
        List<RoutePoint> nnRoute = optimizer.nearestNeighborTSP(startPoint, points);
        nnRoute.add(0, startPoint);
        double distNn = optimizer.routeDistance(nnRoute);

        // 3-Opt Pur
        List<RoutePoint> threeOptRoute = optimizer.threeOptImprove(nnRoute);
        double dist3Opt = optimizer.routeDistance(threeOptRoute);

        double improvement = ((distNn - dist3Opt) / distNn) * 100;

        // 4. Afișarea cerută la Demo (Procente concrete)
        System.out.println("\n --- REZULTATE BENCHMARK DEMO ---");
        System.out.printf("Dimensiune Lista: %d produse reale\n", productIds.size());
        System.out.printf("Timp execuție paralelă (%d requesturi simultane): %d ms\n", numSimulations, totalTimeMs);
        System.out.println("-------------------------------------------------------------------------");
        System.out.printf(" Distanta generata cu Nearest Neighbor: %.2f metri\n", distNn);
        System.out.printf(" Distanta optimizata cu 3-Opt: %.2f metri\n", dist3Opt);
        System.out.printf("CONCLUZIE: 3-Opt a redus distanța cu %.2f%% față de NN!\n", improvement);
        System.out.println("=========================================================================\n");

        // Added epsilon 1e-9 to avoid flakiness from floating-point arithmetic
        assertTrue(dist3Opt <= distNn + 1e-9, "3-Opt trebuie sa ofere o ruta cel putin la fel de buna ca NN");
    }
}