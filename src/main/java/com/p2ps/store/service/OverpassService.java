package com.p2ps.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class OverpassService {

    private static final Logger logger = LoggerFactory.getLogger(OverpassService.class);
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";
    private static final int SEARCH_RADIUS_METERS = 150;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OverpassService(@Qualifier("restTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public Polygon fetchBuildingPolygon(double latitude, double longitude, String storeName, String storeAddress) {
        try {
            String query = String.format(
                    "[out:json];way(around:%d, %.6f, %.6f)[building];out geom;",
                    SEARCH_RADIUS_METERS, latitude, longitude
            );
            URI uri = UriComponentsBuilder.fromUriString(OVERPASS_URL)
                    .queryParam("data", query)
                    .build()
                    .toUri();

            String response = restTemplate.getForObject(uri, String.class);
            if (response == null) {
                logger.warn("Overpass API returned null response for [{}, {}]", latitude, longitude);
                return createFallbackPolygon(latitude, longitude);
            }

            JsonNode root = objectMapper.readTree(response);
            JsonNode elements = root.path("elements");

            if (!elements.isArray() || elements.isEmpty()) {
                logger.info("No building found near [{}, {}], using fallback polygon", latitude, longitude);
                return createFallbackPolygon(latitude, longitude);
            }

            JsonNode bestWay = selectBestBuilding(elements, latitude, longitude, storeName, storeAddress);
            JsonNode geometry = bestWay.path("geometry");

            if (!geometry.isArray() || geometry.size() < 3) {
                logger.info("Building geometry has fewer than 3 points near [{}, {}], using fallback", latitude, longitude);
                return createFallbackPolygon(latitude, longitude);
            }

            List<Coordinate> coordinates = new ArrayList<>();
            for (JsonNode point : geometry) {
                double lon = point.path("lon").asDouble();
                double lat = point.path("lat").asDouble();
                coordinates.add(new Coordinate(lon, lat));
            }

            if (!coordinates.isEmpty() && !coordinates.getFirst().equals2D(coordinates.getLast())) {
                coordinates.add(coordinates.getFirst());
            }

            Polygon polygon = GEOMETRY_FACTORY.createPolygon(coordinates.toArray(new Coordinate[0]));
            polygon.setSRID(4326);

            logger.info("Fetched OSM building polygon with {} vertices for [{}, {}] (store: {})",
                    coordinates.size() - 1, latitude, longitude, storeName);
            return polygon;

        } catch (Exception e) {
            logger.error("Failed to fetch Overpass building polygon for [{}, {}]", latitude, longitude, e);
            return createFallbackPolygon(latitude, longitude);
        }
    }

    private JsonNode selectBestBuilding(JsonNode elements, double latitude, double longitude,
                                         String storeName, String storeAddress) {
        JsonNode best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        String normalizedName = storeName != null ? storeName.trim().toLowerCase() : "";
        String normalizedAddress = storeAddress != null ? storeAddress.trim().toLowerCase() : "";

        for (JsonNode way : elements) {
            double score = scoreBuilding(way, latitude, longitude, normalizedName, normalizedAddress);
            if (score > bestScore) {
                bestScore = score;
                best = way;
            }
        }

        logger.info("Selected building with score {} from {} candidates", bestScore, elements.size());
        return best != null ? best : elements.get(0);
    }

    private double scoreBuilding(JsonNode way, double queryLat, double queryLng,
                                  String normalizedName, String normalizedAddress) {
        double score = 0.0;
        JsonNode tags = way.path("tags");

        // --- Name matching (highest priority) ---
        if (!normalizedName.isEmpty()) {
            String osmName = getTagLower(tags, "name");
            String osmBrand = getTagLower(tags, "brand");
            String osmOperator = getTagLower(tags, "operator");
            String osmAltName = getTagLower(tags, "alt_name");
            String osmShortName = getTagLower(tags, "short_name");

            if (!osmName.isEmpty() && (normalizedName.contains(osmName) || osmName.contains(normalizedName))) {
                score += 1000;
            } else if (!osmBrand.isEmpty() && (normalizedName.contains(osmBrand) || osmBrand.contains(normalizedName))) {
                score += 900;
            } else if (!osmOperator.isEmpty() && (normalizedName.contains(osmOperator) || osmOperator.contains(normalizedName))) {
                score += 800;
            } else if (!osmAltName.isEmpty() && (normalizedName.contains(osmAltName) || osmAltName.contains(normalizedName))) {
                score += 700;
            } else if (!osmShortName.isEmpty() && (normalizedName.contains(osmShortName) || osmShortName.contains(normalizedName))) {
                score += 600;
            }
        }

        // --- Address matching ---
        if (!normalizedAddress.isEmpty()) {
            String osmStreet = getTagLower(tags, "addr:street");
            String osmHousenumber = getTagLower(tags, "addr:housenumber");
            String osmFullAddress = getTagLower(tags, "addr:full");

            if (!osmHousenumber.isEmpty() && normalizedAddress.contains(osmHousenumber)) {
                score += 500;
            }
            if (!osmStreet.isEmpty() && normalizedAddress.contains(osmStreet)) {
                score += 300;
            }
            if (!osmFullAddress.isEmpty() && (normalizedAddress.contains(osmFullAddress) || osmFullAddress.contains(normalizedAddress))) {
                score += 400;
            }
        }

        // --- Proximity scoring (tiebreaker) ---
        JsonNode geometry = way.path("geometry");
        if (geometry.isArray() && !geometry.isEmpty()) {
            double centroidLat = 0, centroidLng = 0;
            int count = 0;
            for (JsonNode point : geometry) {
                centroidLat += point.path("lat").asDouble();
                centroidLng += point.path("lon").asDouble();
                count++;
            }
            centroidLat /= count;
            centroidLng /= count;

            double distance = haversineMeters(queryLat, queryLng, centroidLat, centroidLng);
            // Closer = higher score, max ~150 points at distance 0
            score += Math.max(0, 150 - distance);
        }

        return score;
    }

    private String getTagLower(JsonNode tags, String key) {
        String value = tags.path(key).asText("");
        return value.isEmpty() ? "" : value.toLowerCase();
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private Polygon createFallbackPolygon(double latitude, double longitude) {
        double offsetLat = 0.0005;
        double offsetLng = 0.0008;
        Coordinate[] coords = {
                new Coordinate(longitude - offsetLng, latitude + offsetLat),
                new Coordinate(longitude + offsetLng, latitude + offsetLat),
                new Coordinate(longitude + offsetLng, latitude - offsetLat),
                new Coordinate(longitude - offsetLng, latitude - offsetLat),
                new Coordinate(longitude - offsetLng, latitude + offsetLat),
        };
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(coords);
        polygon.setSRID(4326);
        logger.info("Created fallback rectangular polygon for [{}, {}]", latitude, longitude);
        return polygon;
    }
}
