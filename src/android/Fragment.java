package cordova.plugin.stepdist;

public class Fragment {

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
    orders fragmentType;

    public Fragment() {
        this.heightMax = 0.0;
        this.heightMin = 0.0;
        this.amplitude = 0.0;
        this.lengthFirst = 0;
        this.lengthSecond = 0;
        this.lengthTotal = 0;
        this.axis = 0;
        this.fragmentType = orders.none;
    }

    public Fragment(Double heightMax, Double heightMin, Integer lengthFirst, Integer lengthSecond, Integer axis, orders fragmentType) {
        this.heightMax = heightMax;
        this.heightMin = heightMin;
        this.amplitude = heightMax - heightMin;
        this.lengthFirst = lengthFirst;
        this.lengthSecond = lengthSecond;
        this.lengthTotal = lengthFirst + lengthSecond;
        this.axis = axis;
        this.fragmentType = fragmentType;
    }

    public Fragment(Double amplitude, Integer lengthTotal, Integer axis, orders fragmentType) {
        this.heightMax = 0.0;
        this.heightMin = 0.0;
        this.amplitude = amplitude;
        this.lengthFirst = 0;
        this.lengthSecond = 0;
        this.lengthTotal = lengthTotal;
        this.axis = axis;
        this.fragmentType = fragmentType;
    }
}
