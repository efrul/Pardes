/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.internal.vectorization;

import java.io.IOException;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.apache.lucene.codecs.hnsw.FlatVectorsScorer;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.VectorUtil;

/**
 * A provider of vectorization implementations. Depending on the Java version and availability of
 * vectorization modules in the Java runtime this class provides optimized implementations (using
 * SIMD) of several algorithms used throughout Apache Lucene.
 *
 * <p>Expert: set the {@value #UPPER_JAVA_FEATURE_VERSION_SYSPROP} system property to increase the
 * set of Java versions this class will provide optimized implementations for.
 *
 * @lucene.internal
 */
public abstract class VectorizationProvider {

  static final OptionalInt TESTS_VECTOR_SIZE;
  static final boolean TESTS_FORCE_INTEGER_VECTORS;
  static final int UPPER_JAVA_FEATURE_VERSION = getUpperJavaFeatureVersion();

  static {
    var vs = OptionalInt.empty();
    try {
      vs =
          Stream.ofNullable(System.getProperty("tests.vectorsize"))
              .filter(Predicate.not(Set.of("", "default")::contains))
              .mapToInt(Integer::parseInt)
              .findAny();
    } catch (
        @SuppressWarnings("unused")
        SecurityException se) {
      // ignored
    }
    TESTS_VECTOR_SIZE = vs;

    boolean enforce = false;
    try {
      enforce = Boolean.getBoolean("tests.forceintegervectors");
    } catch (
        @SuppressWarnings("unused")
        SecurityException se) {
      // ignored
    }
    TESTS_FORCE_INTEGER_VECTORS = enforce;
  }

  private static final String UPPER_JAVA_FEATURE_VERSION_SYSPROP =
      "org.apache.lucene.vectorization.upperJavaFeatureVersion";
  private static final int DEFAULT_UPPER_JAVA_FEATURE_VERSION = 26;

  private static int getUpperJavaFeatureVersion() {
    int runtimeVersion = DEFAULT_UPPER_JAVA_FEATURE_VERSION;
    try {
      String str = System.getProperty(UPPER_JAVA_FEATURE_VERSION_SYSPROP);
      if (str != null) {
        runtimeVersion = Math.max(Integer.parseInt(str), runtimeVersion);
      }
    } catch (@SuppressWarnings("unused") NumberFormatException | SecurityException ignored) {
      Logger.getLogger(VectorizationProvider.class.getName())
          .warning(
              "Cannot read sysprop "
                  + UPPER_JAVA_FEATURE_VERSION_SYSPROP
                  + ", so the default value will be used.");
    }
    return runtimeVersion;
  }

  /**
   * Returns the default instance of the provider matching vectorization possibilities of actual
   * runtime.
   *
   * @throws IllegalCallerException if the singleton getter is not called from known Lucene classes.
   */
  public static VectorizationProvider getInstance() {
    ensureCaller();
    return Objects.requireNonNull(
        Holder.INSTANCE, "call to getInstance() from subclass of VectorizationProvider");
  }

  VectorizationProvider() {
    // no instance/subclass except from this package
  }

  /**
   * Returns a singleton (stateless) {@link VectorUtilSupport} to support SIMD usage in {@link
   * VectorUtil}.
   */
  public abstract VectorUtilSupport getVectorUtilSupport();

  /** Returns a FlatVectorsScorer that supports the Lucene99 format. */
  public abstract FlatVectorsScorer getLucene99FlatVectorsScorer();

  /** Returns a FlatVectorsScorer that supports the Lucene99 format. */
  public abstract FlatVectorsScorer getLucene99ScalarQuantizedVectorsScorer();

  /** Create a new {@link PostingDecodingUtil} for the given {@link IndexInput}. */
  public abstract PostingDecodingUtil newPostingDecodingUtil(IndexInput input) throws IOException;

  /** Returns a {@link DocValuesRangeSupport} for SIMD-accelerated range evaluation. */
  public DocValuesRangeSupport getDocValuesRangeSupport() {
    return DefaultDocValuesRangeSupport.INSTANCE;
  }

  /**
   * Returns a {@link DocValuesBulkDecodeSupport} instance for bulk numeric value decode. The
   * returned instance uses SIMD when available (Panama Vector API), falling back to a scalar loop
   * otherwise.
   */
  public abstract DocValuesBulkDecodeSupport getDocValuesBulkDecodeSupport();

  // *** Lookup mechanism: ***

  // visible for tests
  static VectorizationProvider lookup(boolean testMode) {
    // Android: there is no Hotspot, no jdk.incubator.vector and no java.lang.Module/Runtime.version
    // on ART, so the scalar provider is the only one that can work (the Panama classes are stripped).
    return new DefaultVectorizationProvider();
  }

  private static void ensureCaller() {
    // Android: StackWalker only exists from API 34, so the caller check is skipped; the provider
    // is stateless and only reachable through Lucene's own classes anyway.
  }

  /** This static holder class prevents classloading deadlock. */
  private static final class Holder {
    private Holder() {}

    static final VectorizationProvider INSTANCE = lookup(false);
  }
}
