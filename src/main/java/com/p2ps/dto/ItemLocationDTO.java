package com.p2ps.dto;

public class ItemLocationDTO {
    private double lat;
    private double lon;
    private boolean lowConfidenceWarning;
    private double confidenceScore;

    public ItemLocationDTO(double lat, double lon, boolean lowConfidenceWarning, double confidenceScore) {
        this.lat = lat;
        this.lon = lon;
        this.lowConfidenceWarning = lowConfidenceWarning;
        this.confidenceScore = confidenceScore;
    }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }
    public boolean isLowConfidenceWarning() { return lowConfidenceWarning; }
    public void setLowConfidenceWarning(boolean lowConfidenceWarning) { this.lowConfidenceWarning = lowConfidenceWarning; }
    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
}
