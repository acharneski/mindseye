package com.simiacryptus.mindseye;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jblas.DoubleMatrix;

public class LogNDArray {
  
  public static class LogNumber extends Number implements Comparable<LogNumber> {
    public double logValue;
    private boolean realNeg;
    
    private LogNumber(boolean realNeg, double logValue) {
      super();
      this.realNeg = realNeg;
      this.logValue = logValue;
    }
    
    public static LogNumber log(double v) {
      return new LogNumber(v < 0, Math.log(Math.abs(v)));
    }
    
    @Override
    public int intValue() {
      return (int) doubleValue();
    }
    
    @Override
    public long longValue() {
      return (long) doubleValue();
    }
    
    @Override
    public float floatValue() {
      return (float) doubleValue();
    }
    
    @Override
    public double doubleValue() {
      return (realNeg ? -1 : 1) * Math.exp(logValue);
    }
    
    @Override
    public int compareTo(LogNumber o) {
      int compare = Boolean.compare(!this.realNeg, !o.realNeg);
      if (0 == compare) compare = Double.compare(this.logValue, o.logValue);
      return compare;
    }
    
    public LogNumber add(LogNumber right) {
      assert (Double.isFinite(this.logValue));
      assert (Double.isFinite(right.logValue));
      LogNumber r = log(right.doubleValue() + this.doubleValue());
      assert (Double.isFinite(r.logValue));
      return r;
    }
    
    public LogNumber multiply(LogNumber right) {
      assert (Double.isFinite(this.logValue));
      assert (Double.isFinite(right.logValue));
      LogNumber r = new LogNumber(realNeg == right.realNeg, logValue + right.logValue);
      assert (Double.isFinite(r.logValue));
      return r;
    }
    
  }
  
  public static int dim(final int... dims) {
    int total = 1;
    for (final int dim : dims) {
      total *= dim;
    }
    return total;
  }
  
  protected volatile LogNumber[] data;
  protected final int[] dims;
  protected final int[] skips;
  
  protected LogNDArray() {
    super();
    this.data = null;
    this.skips = null;
    this.dims = null;
  }
  
  public LogNDArray(final int... dims) {
    this(dims, null);
  }
  
  public LogNDArray(final int[] dims, final LogNumber[] data) {
    this.dims = Arrays.copyOf(dims, dims.length);
    this.skips = new int[dims.length];
    for (int i = 0; i < this.skips.length; i++)
    {
      if (i == 0) {
        this.skips[i] = 1;
      } else {
        this.skips[i] = this.skips[i - 1] * dims[i - 1];
      }
    }
    assert null == data || NDArray.dim(dims) == data.length;
    assert null == data || 0 < data.length;
    this.data = data;// Arrays.copyOf(data, data.length);
  }
  
  public LogNDArray(NDArray ndArray) {
    this(ndArray.dims, log(ndArray.data));
  }
  
  public Stream<Coordinate> coordStream() {
    return coordStream(false);
  }
  
  public Stream<Coordinate> coordStream(final boolean paralell) {
    return Util.toStream(new Iterator<Coordinate>() {
      
      int cnt = 0;
      int[] val = new int[dims.length];
      
      @Override
      public boolean hasNext() {
        return this.cnt < dim();
      }
      
      @Override
      public Coordinate next() {
        final int[] last = Arrays.copyOf(this.val, this.val.length);
        for (int i = 0; i < this.val.length; i++)
        {
          if (++this.val[i] >= dims[i]) {
            this.val[i] = 0;
          } else {
            break;
          }
        }
        final int index = this.cnt++;
        // assert index(last) == index;
        return new Coordinate(index, last);
      }
    }, dim(), paralell);
  }
  
  public int dim() {
    return getData().length;
  }
  
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final LogNDArray other = (LogNDArray) obj;
    if (!Arrays.equals(getData(), other.getData())) return false;
    if (!Arrays.equals(this.dims, other.dims)) return false;
    return true;
  }
  
  public LogNumber get(final Coordinate coords) {
    final LogNumber v = getData()[coords.index];
    assert Double.isFinite(v.logValue);
    return v;
  }
  
  public LogNumber get(final int... coords) {
    // assert IntStream.range(dims.length,coords.length).allMatch(i->coords[i]==0);
    // assert coords.length==dims.length;
    final LogNumber v = getData()[index(coords)];
    assert Double.isFinite(v.logValue);
    return v;
  }
  
  public LogNumber[] getData() {
    if (null == this.data) {
      synchronized (this) {
        if (null == this.data) {
          this.data = new LogNumber[NDArray.dim(this.dims)];
        }
      }
    }
    return this.data;
  }
  
  public int[] getDims() {
    return this.dims;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(getData());
    result = prime * result + Arrays.hashCode(this.dims);
    return result;
  }
  
  public int index(final Coordinate coords) {
    return coords.index;
  }
  
  public int index(final int... coords) {
    int v = 0;
    for (int i = 0; i < this.skips.length && i < coords.length; i++) {
      v += this.skips[i] * coords[i];
    }
    return v;
    // return IntStream.range(0, skips.length).map(i->skips[i]*coords[i]).sum();
  }
  
  @Override
  public String toString() {
    return toString(new int[] {});
  }
  
  private String toString(final int... coords) {
    if (coords.length == dims.length)
      return get(coords).toString();
    else {
      List<String> list = IntStream.range(0, dims[coords.length]).mapToObj(i -> {
        return toString(_add(coords, i));
      }).collect(Collectors.<String> toList());
      if (list.size() > 10) {
        list = list.subList(0, 8);
        list.add("...");
      }
      final Optional<String> str = list.stream().limit(10).reduce((a, b) -> a + "," + b);
      return "{ " + str.get() + " }";
    }
  }
  
  private int[] _add(final int[] base, final int... extra) {
    final int[] copy = Arrays.copyOf(base, base.length + extra.length);
    for (int i = 0; i < extra.length; i++) {
      copy[i + base.length] = extra[i];
    }
    return copy;
  }
  
  public void set(final Coordinate coords, final LogNumber value) {
    assert Double.isFinite(value.logValue);
    set(coords.index, value);
  }
  
  public LogNDArray set(final LogNumber[] data) {
    for (int i = 0; i < getData().length; i++)
    {
      getData()[i] = data[i];
    }
    return this;
  }
  
  public void set(final int index, final LogNumber value) {
    assert Double.isFinite(value.logValue);
    getData()[index] = value;
  }
  
  public void set(final int[] coords, final LogNumber value) {
    assert Double.isFinite(value.logValue);
    set(index(coords), value);
  }
  
  private static LogNumber[] log(double[] data) {
    return DoubleStream.of(data).mapToObj(x -> LogNumber.log(x)).toArray(i->new LogNumber[i]);
  }
  
  public NDArray exp() {
    return new NDArray(getDims(), Stream.of(getData()).mapToDouble(x -> x.doubleValue()).toArray());
  }
  
  public synchronized void add(final int index, final LogNumber value) {
    assert Double.isFinite(value.logValue);
    set(index, getData()[index].add(value));
  }
  
  public void add(final int[] coords, final LogNumber value) {
    add(index(coords), value);
  }
  
  public LogNDArray scale(double rate) {
    double log = Math.log(rate);
    for (int i = 0; i < data.length; i++) {
      data[i].logValue += log;
    }
    return this;
  }
  
}