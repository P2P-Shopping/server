package p2ps.controller;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoutingControllerTest {

    @Test
    void testCalculateRoute() {
        RoutingController controller = new RoutingController();

        RoutingRequest request = new RoutingRequest();

        RoutingResponse response = controller.calculateRoute(request);

        assertEquals("success", response.getStatus(), "Statusul ar trebui sa fie success");
        assertNotNull(response.getRoute(), "Ruta nu ar trebui sa fie nula");
        assertEquals(4, response.getRoute().size(), "Ar trebui sa returneze fix 4 puncte mock");
    }
}