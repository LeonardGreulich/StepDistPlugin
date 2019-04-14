package cordova.plugin.stepdist;

public class Stride {

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
