package com.p2ps.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;

/**
 * BE 3.2 — thin HTTP wrapper around the OSRM Route API.
 *
 * Profiles used:
 *   "foot" → walking estimate
 *   "car"  → driving estimate
 *
 * Returns distance, duration, and an Encoded Polyline string representing
 * the actual road geometry (all turns and curves), ready to be drawn on a map.
 *
 * OSRM route endpoint format:
 *   GET {baseUrl}/route/v1/{profile}/{fromLng},{fromLat};{toLng},{toLat}
 *       ?overview=full&geometries=polyline
 *
 * Note: OSRM uses longitude FIRST, then latitude — opposite of PostGIS convention.
 */
@Component
public class OsrmClient {

    private static final Logger logger = LoggerFactory.getLogger(OsrmClient.class);

    @Value("${osrm.base-url:http://router.project-osrm.org}")
    private String osrmBaseUrl;

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
     *                        Null if OSRM did not return geometry.
     *                        Frontend decodes this to draw the route on a map.
     */
    public record TransportEstimate(double distanceM, double durationSeconds, String polyline) {}

    /**
     * Calls OSRM and returns distance, duration, and polyline for the given profile.
     * Returns null if OSRM is unreachable or returns no route.
     *
     * @param profile "foot" or "car"
     */
    public TransportEstimate getEstimate(double fromLat, double fromLng,
                                         double toLat,   double toLng,
                                         String profile) {
        String coordinates = String.format(Locale.ROOT, "%f,%f;%f,%f", fromLng, fromLat, toLng, toLat);

        // overview=full returns the complete geometry; geometries=polyline uses Encoded Polyline format
        String url = osrmBaseUrl + "/route/v1/" + profile + "/" + coordinates
                + "?overview=full&geometries=polyline";

        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            String code = root.path("code").asText();
            if (!"Ok".equals(code)) {
                logger.warn("OSRM returned non-OK code '{}' for profile '{}'", code, profile);
                return null;
            }

            JsonNode route = root.path("routes").get(0);
            if (route == null || route.isMissingNode()) {
                logger.warn("OSRM returned no routes for profile '{}'", profile);
                return null;
            }

            double distance = route.path("distance").asDouble();
            double duration = route.path("duration").asDouble();
            String polyline = route.path("geometry").asText(null);

            logger.debug("OSRM [{}]: {}m in {}s polyline={}", profile,
                    Math.round(distance), Math.round(duration),
                    polyline != null ? polyline.length() + " chars" : "null");

            return new TransportEstimate(distance, duration, polyline);

        } catch (Exception e) {
            logger.warn("OSRM request failed for profile '{}': {}", profile, e.getMessage());
            return null;
        }
    }
}
