package com.p2ps.service;

import com.p2ps.controller.RoutePoint;
import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Tag("manual")
class RoutingPerformanceBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(RoutingPerformanceBenchmarkTest.class);

    @Autowired
    private RoutingService routingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // UUID-ul listei masive inserate prin scriptul SQL
    private static final String DEMO_LIST_ID = "99999999-0000-0000-0000-000000000001";

    @Test
    void benchmark_ParallelExecution_NearestNeighbor_vs_3Opt() {
        // 1. Extragem itemele reale din baza de date pentru lista de Demo
        List<String> productIds = jdbcTemplate.queryForList(
                "SELECT id::text FROM items WHERE list_id = ?",
                String.class,
                DEMO_LIST_ID
        );

        // Verificăm că scriptul SQL a rulat cu succes și avem datele masive (> 20 iteme)
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
        List<RoutingService.ProductLocation> locations = jdbcTemplate.query(
                "SELECT i.id::text AS id, i.name, ST_Y(sim.estimated_loc_point) AS lat, ST_X(sim.estimated_loc_point) AS lng, 1.0 AS confidence_score " +
                        "FROM items i JOIN store_inventory_map sim ON i.id = sim.item_id WHERE i.list_id = ?",
                (rs, rowNum) -> new RoutingService.ProductLocation(rs.getString("id"), rs.getString("name"), rs.getDouble("lat"), rs.getDouble("lng"), 1.0),
                DEMO_LIST_ID
        );

        RouteOptimizer optimizer = new RouteOptimizer();
        List<RoutePoint> points = locations.stream().map(l -> new RoutePoint(l.itemId(), l.name(), l.lat(), l.lng())).toList();

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