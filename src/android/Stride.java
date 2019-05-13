//
//  Stride.swift
//
//  Created by Leo on 3/6/19.
//  Copyright © 2019 Leonard Greulich. All rights reserved.
//
// Contains the Stride model that is detected in gravity data and used for step counting.
//

package cordova.plugin.stepdist;

public class Stride {

    // Indicates whether the identified stride pattern starts with a maximum or a minimum within the gravity sensor data.
    // As stated in the thesis, every stride is modeled by three extrema (max-min-max or min-max-min).
    enum orders {
        MaxMinMax, MinMaxMin, none
    }

    Double heightMax;
    Double heightMin;
    Double amplitude;
    Integer lengthFirst;
    Integer lengthSecond;
    Integer lengthTotal;
    Integer axis;
    orders strideType;

    public Stride() {
        this.heightMax = 0.0;
        this.heightMin = 0.0;
        this.amplitude = 0.0;
        this.lengthFirst = 0;
        this.lengthSecond = 0;
        this.lengthTotal = 0;
        this.axis = 0;
        this.strideType = orders.none;
    }

    public Stride(Double heightMax, Double heightMin, Integer lengthFirst, Integer lengthSecond, Integer axis, orders strideType) {
        this.heightMax = heightMax;
        this.heightMin = heightMin;
        this.amplitude = heightMax - heightMin;
        this.lengthFirst = lengthFirst;
        this.lengthSecond = lengthSecond;
        this.lengthTotal = lengthFirst + lengthSecond;
        this.axis = axis;
        this.strideType = strideType;
    }

    public Stride(Double amplitude, Integer lengthTotal, Integer axis, orders strideType) {
        this.heightMax = 0.0;
        this.heightMin = 0.0;
        this.amplitude = amplitude;
        this.lengthFirst = 0;
        this.lengthSecond = 0;
        this.lengthTotal = lengthTotal;
        this.axis = axis;
        this.strideType = strideType;
    }
}
