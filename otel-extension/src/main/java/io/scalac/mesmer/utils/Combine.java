package io.scalac.mesmer.utils;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Combine {

  @SafeVarargs
  public static List<String> combine(List<String>... values) {
    return Stream.of(values).flatMap(Collection::stream).collect(Collectors.toList());
  }
}
