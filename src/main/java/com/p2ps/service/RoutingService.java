package com.p2ps.service;

import com.p2ps.controller.RoutingRequest;
import com.p2ps.controller.RoutingResponse;
import com.p2ps.controller.RoutePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RoutingService {

    private static final Logger logger = LoggerFactory.getLogger(RoutingService.class);

    @Cacheable(value = "routes",  key = "#request != null ? #request.hashCode() : 0")
    public RoutingResponse calculateOptimalRoute(RoutingRequest request) {
        // Dev 1 will put their TSP algorithm logic inside here instead of the mock data.
        logger.info(">>> CALCULEZ RUTA ACUM! A DURAT MULT... <<<");
        double userLat = 47.151726; // sensible default used in tests/fixtures
        double userLng = 27.587914;

        if (request != null) {
            userLat = request.getUserLat();
            userLng = request.getUserLng();
        }

        List<RoutePoint> mockRoute = new ArrayList<>();
        mockRoute.add(new RoutePoint("user_loc", "Punctul Albastru (Tu)", userLat, userLng));
        mockRoute.add(new RoutePoint("item_101", "Lapte", 47.151800, 27.588000));
        mockRoute.add(new RoutePoint("item_102", "Pâine", 47.151850, 27.588150));
        mockRoute.add(new RoutePoint("item_103", "Mere", 47.151900, 27.587950));

        return new RoutingResponse("success", mockRoute);
    }
}
