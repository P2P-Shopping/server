package com.p2ps.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacroRoutingResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private TransportEstimate walking;
    private TransportEstimate driving;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransportEstimate implements Serializable {

        private static final long serialVersionUID = 1L;

        private double distanceM;
        private double durationSeconds;
        private String polyline;
    }
}
