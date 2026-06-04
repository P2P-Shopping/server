package com.p2ps.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;

/**
 * Thin HTTP wrapper around the OpenRouteService Directions API.
 *
 * Profiles used:
 *   "foot-walking"  → walking estimate
 *   "driving-car"   → driving estimate
 *
 * Returns distance, duration, and an Encoded Polyline string representing
 * the actual road geometry (all turns and curves), ready to be drawn on a map.
 *
 * ORS directions endpoint format:
 *   POST {baseUrl}/v2/directions/{profile}
 *   Body: { "coordinates": [[fromLng,fromLat],[toLng,toLat]] }
 *   Header: Authorization: {apiKey}
 */
@Component
public class OsrmClient {

    private static final Logger logger = LoggerFactory.getLogger(OsrmClient.class);

    @Value("${routing.api.base-url:https://api.openrouteservice.org}")
    private String routingBaseUrl;

    @Value("${routing.api.key:}")
    private String routingApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OsrmClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @param distanceM       straight-road distance in metres
     * @param durationSeconds estimated travel time in seconds
     * @param polyline        Encoded Polyline string — contains all turns/curves of the route.
     *                        Null if the routing service did not return geometry.
     *                        Frontend decodes this to draw the route on a map.
     */
    public record TransportEstimate(double distanceM, double durationSeconds, String polyline) {}

    /**
     * Calls the routing API and returns distance, duration, and polyline for the given profile.
     * Returns null if the service is unreachable or returns no route.
     *
     * @param profile "driving", "walking", "foot", "car", etc.
     */
    private static final String OSRM_BASE_URL = "https://router.project-osrm.org";

    public TransportEstimate getEstimate(double fromLat, double fromLng,
                                         double toLat,   double toLng,
                                         String profile) {
        if (routingApiKey == null || routingApiKey.isBlank()) {
            return getEstimateOsrm(fromLat, fromLng, toLat, toLng, profile);
        }
        String orsProfile = mapProfile(profile);
        return getEstimateOrs(fromLat, fromLng, toLat, toLng, orsProfile);
    }

    private TransportEstimate getEstimateOrs(double fromLat, double fromLng,
                                              double toLat,   double toLng,
                                              String profile) {
        String url = routingBaseUrl + "/v2/directions/" + profile;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", routingApiKey);

            String body = String.format(Locale.ROOT,
                    "{\"coordinates\":[[%.6f,%.6f],[%.6f,%.6f]]}",
                    fromLng, fromLat, toLng, toLat);

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            String response = restTemplate.postForObject(url, request, String.class);
            JsonNode root = objectMapper.readTree(response);

            JsonNode routes = root.path("routes");
            if (routes.isMissingNode() || !routes.isArray() || routes.isEmpty()) {
                logger.warn("ORS returned no routes for profile '{}'", profile);
                return null;
            }

            JsonNode route = routes.get(0);
            JsonNode summary = route.path("summary");
            double distance = summary.path("distance").asDouble();
            double duration = summary.path("duration").asDouble();
            String polyline = route.path("geometry").asText(null);

            return new TransportEstimate(distance, duration, polyline);
        } catch (Exception e) {
            logger.warn("ORS request failed for profile '{}': {}", profile, e.getMessage());
            return null;
        }
    }

    private TransportEstimate getEstimateOsrm(double fromLat, double fromLng,
                                               double toLat,   double toLng,
                                               String profile) {
        String osrmProfile = profile.toLowerCase().contains("walk") || profile.toLowerCase().contains("foot")
                ? "foot" : "driving";
        String url = String.format(Locale.ROOT,
                "%s/route/v1/%s/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline",
                OSRM_BASE_URL, osrmProfile, fromLng, fromLat, toLng, toLat);
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (!"Ok".equals(root.path("code").asText())) {
                logger.warn("OSRM returned non-Ok code for profile '{}'", osrmProfile);
                return null;
            }

            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) return null;

            JsonNode route = routes.get(0);
            double distance = route.path("distance").asDouble();
            double duration = route.path("duration").asDouble();
            String polyline = route.path("geometry").asText(null);

            logger.debug("OSRM [{}]: {}m in {}s", osrmProfile, Math.round(distance), Math.round(duration));
            return new TransportEstimate(distance, duration, polyline);
        } catch (Exception e) {
            logger.warn("OSRM request failed for profile '{}': {}", osrmProfile, e.getMessage());
            return null;
        }
    }

    /**
     * Maps generic profile names to OpenRouteService profile identifiers.
     */
    private String mapProfile(String profile) {
        return switch (profile.toLowerCase()) {
            case "walking", "foot" -> "foot-walking";
            case "driving", "car" -> "driving-car";
            default -> {
                logger.warn("Unknown profile '{}', defaulting to driving-car", profile);
                yield "driving-car";
            }
        };
    }
}
