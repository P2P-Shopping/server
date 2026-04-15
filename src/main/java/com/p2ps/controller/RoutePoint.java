package com.p2ps.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class RoutePoint implements Serializable {
    private String itemId;
    private String name;
    private double lat;
    private double lng;

}
