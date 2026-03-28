package p2ps.controller;

public class RoutePoint {
    private String itemId;
    private String name;
    private double lat;
    private double lng;

    public RoutePoint(String itemId, String name, double lat, double lng) {
        this.itemId = itemId;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    //getters
    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public double getLat() { return lat; }
    public double getLng() { return lng; }

    //setters
    public void setLat(double lat) { this.lat = lat; }
    public void setLng(double lng) { this.lng = lng; }
    public void setName(String name) { this.name = name; }
    public void setItemId(String itemId) { this.itemId = itemId; }
}
