package cordova.plugin.stepdist.primitivelists;

public class DoubleList {
    public final static int DEFAULT_INITIAL_CAPACITY = 16;
    protected transient double a[];
    protected int size;

    public void DoubleList() {
        a = new double[DEFAULT_INITIAL_CAPACITY];
    }

    public void DoubleList( final int capacity ) {
        a = new double[capacity];
    }

}