package com.p2ps.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StoreMatchingEngine {

    private static final Logger logger = LoggerFactory.getLogger(StoreMatchingEngine.class);
    
    private final JdbcTemplate jdbcTemplate;

    public StoreMatchingEngine(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Interogheaza baza de date principala pentru a intersecta lista de cumparaturi
     * cu inventarul magazinelor din proximitate.
     * Returneaza magazinul optim bazat pe stoc maxim (matched items) si distanta minima.
     * 
     * @param userLat latitudinea utilizatorului
     * @param userLng longitudinea utilizatorului
     * @param radiusInMeters raza in metri pentru cautare
     * @param itemIds lista de ID-uri ale produselor (sub forma de String)
     * @return StoreMatchResult reprezentand magazinul optim sau null daca nu s-a gasit un rezultat
     */
    public StoreMatchResult findOptimalStore(double userLat, double userLng, double radiusInMeters, List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            logger.warn("Lista de produse este goala. Nu se poate calcula magazinul optim.");
            return null;
        }

        String itemIdsPlaceholders = itemIds.stream().map(ignoredId -> "?").collect(Collectors.joining(", "));

        // SQL pentru gasirea magazinului optim:
        // 1. ST_Distance calculeaza distanta exacta pana la poligonul magazinului
        // 2. ST_DWithin filtreaza magazinele din proximitate (raza in metri)
        // 3. LEFT JOIN cu store_inventory_map numara cate produse din lista sunt disponibile in magazin
        // 4. ORDER BY ordoneaza descrescator dupa stoc si crescator dupa distanta
        String sql = """
            SELECT
                sg.store_id::text AS store_id,
                sg.name,
                ST_Distance(sg.boundary_polygon::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) AS distance_m,
                COUNT(sim.item_id) AS matched_items
            FROM store_geofences sg
            LEFT JOIN store_inventory_map sim
                ON sg.store_id = sim.store_id AND sim.item_id::text IN (%s)
            WHERE ST_DWithin(sg.boundary_polygon::geography, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)
            GROUP BY sg.store_id, sg.name, sg.boundary_polygon
            HAVING COUNT(sim.item_id) > 0
            ORDER BY matched_items DESC, distance_m ASC
            LIMIT 1
        """.formatted(itemIdsPlaceholders);

        List<Object> params = new ArrayList<>();
        // Parametrii pentru prima aparitie ST_MakePoint (lng, lat)
        params.add(userLng);
        params.add(userLat);
        
        // Parametrii pentru IN (...)
        params.addAll(itemIds);
        
        // Parametrii pentru a doua aparitie ST_MakePoint (lng, lat)
        params.add(userLng);
        params.add(userLat);
        params.add(radiusInMeters);

        logger.info("Caut magazin optim pentru {} produse, la coordonatele ({}, {}) in raza de {} metri", 
                    itemIds.size(), userLat, userLng, radiusInMeters);

        List<StoreMatchResult> results = jdbcTemplate.query(
            sql,
            (rs, ignoredRowNum) -> new StoreMatchResult(
                rs.getString("store_id"),
                rs.getString("name"),
                rs.getInt("matched_items"),
                rs.getDouble("distance_m")
            ),
            params.toArray(new Object[0])
        );

        if (results.isEmpty()) {
            logger.info("Nu a fost gasit niciun magazin in raza specificata care sa contina produsele dorite.");
            return null;
        }

        StoreMatchResult bestStore = results.getFirst();
        logger.info("Gasit magazin optim: {} (ID: {}) - Produse gasite: {}/{}, Distanta: {}m", 
                    bestStore.storeName(), bestStore.storeId(), bestStore.matchedItems(), itemIds.size(), Math.round(bestStore.distanceMeters()));
        
        return bestStore;
    }

    public record StoreMatchResult(String storeId, String storeName, int matchedItems, double distanceMeters) {}
}
