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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    public Polygon fetchBuildingPolygon(double latitude, double longitude) {
        try {
            String query = String.format(
                    "[out:json];way(around:%d, %.6f, %.6f)[building];out geom;",
                    SEARCH_RADIUS_METERS, latitude, longitude
            );
            String url = OVERPASS_URL + "?data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

            String response = restTemplate.getForObject(url, String.class);
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

            JsonNode way = elements.get(0);
            JsonNode geometry = way.path("geometry");

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

            logger.info("Fetched OSM building polygon with {} vertices for [{}, {}]",
                    coordinates.size() - 1, latitude, longitude);
            return polygon;

        } catch (Exception e) {
            logger.error("Failed to fetch Overpass building polygon for [{}, {}]", latitude, longitude, e);
            return createFallbackPolygon(latitude, longitude);
        }
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
