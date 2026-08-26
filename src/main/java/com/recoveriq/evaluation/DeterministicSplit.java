package com.recoveriq.evaluation;
import java.util.List;
/** Stable held-out split based only on record identity and a configured seed. */
public final class DeterministicSplit {
 public record Split<T>(List<T> development,List<T> heldOut){}
 public static <T> Split<T> split(List<T> values,long seed,double developmentShare){ if(developmentShare<=0||developmentShare>=1)throw new IllegalArgumentException("share must be between zero and one"); var dev=values.stream().filter(v->unit(v,seed)<developmentShare).toList(); var test=values.stream().filter(v->unit(v,seed)>=developmentShare).toList(); return new Split<>(dev,test); }
 private static double unit(Object value,long seed){ long h=seed; for(char c:String.valueOf(value).toCharArray())h=31*h+c;return (h&Long.MAX_VALUE)/(double)Long.MAX_VALUE; }
}
