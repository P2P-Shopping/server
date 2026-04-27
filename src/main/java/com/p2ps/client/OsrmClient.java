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
 * Default base URL points to the public OSRM demo server (no key required).
 * For self-hosted OSRM in Docker, set osrm.base-url=http://osrm:5000 in application.properties.
 *
 * OSRM route endpoint format:
 *   GET {baseUrl}/route/v1/{profile}/{fromLng},{fromLat};{toLng},{toLat}?overview=false
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

    public record TransportEstimate(double distanceM, double durationSeconds) {}

    /**
     * Calls OSRM and returns distance + duration for the given profile.
     * Returns null if OSRM is unreachable or returns no route.
     *
     * @param profile "foot" or "car"
     */
    public TransportEstimate getEstimate(double fromLat, double fromLng,
                                         double toLat,   double toLng,
                                         String profile) {
        // OSRM: coordinates are lng,lat (not lat,lng)
        String coordinates = String.format(Locale.ROOT, "%f,%f;%f,%f", fromLng, fromLat, toLng, toLat);

        String url = osrmBaseUrl + "/route/v1/" + profile + "/" + coordinates + "?overview=false";

        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            String code = root.path("code").asText();
            if (!"Ok".equals(code)) {
                logger.warn("OSRM returned non-OK code '{}' for profile '{}': {}", code, profile, url);
                return null;
            }

            JsonNode route = root.path("routes").get(0);
            if (route == null || route.isMissingNode()) {
                logger.warn("OSRM returned no routes for profile '{}': {}", profile, url);
                return null;
            }

            double distance = route.path("distance").asDouble();
            double duration = route.path("duration").asDouble();
            logger.debug("OSRM [{}]: {}m in {}s", profile, Math.round(distance), Math.round(duration));
            return new TransportEstimate(distance, duration);

        } catch (Exception e) {
            logger.warn("OSRM request failed for profile '{}': {}", profile, e.getMessage());
            return null;
        }
    }
}