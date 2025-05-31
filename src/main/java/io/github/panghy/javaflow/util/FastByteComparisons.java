/*
 * FastByteComparisons.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2013-2024 Apple Inc. and the FoundationDB project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.panghy.javaflow.util;

import sun.misc.Unsafe;

import java.nio.ByteOrder;
import java.util.Comparator;


/**
 * Utility code to do optimized byte-array comparison.
 *
 * <p>This class provides high-performance lexicographic comparison of byte arrays
 * using platform-specific optimizations when available. The implementation uses
 * {@code sun.misc.Unsafe} for fast memory access on platforms that support it,
 * falling back to standard byte-by-byte comparison otherwise.
 *
 * <p>This implementation is derived from Guava's {@code UnsignedBytes} class
 * and has been modified to support comparisons of array segments with
 * non-zero offsets, making it suitable for use with {@link Tuple} serialization.
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li>Uses vectorized comparison when {@code Unsafe} is available</li>
 *   <li>Handles byte order differences automatically</li>
 *   <li>Optimized for both small and large byte arrays</li>
 *   <li>Thread-safe (all methods are static)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * byte[] array1 = {1, 2, 3, 4};
 * byte[] array2 = {1, 2, 5, 6};
 *
 * // Compare entire arrays
 * int result = FastByteComparisons.compareTo(array1, array2);
 *
 * // Compare array segments
 * int result2 = FastByteComparisons.compareTo(
 *     array1, 1, 2,  // offset=1, length=2
 *     array2, 1, 2   // offset=1, length=2
 * );
 * }</pre>
 *
 * <p><strong>Note:</strong> This class uses {@code sun.misc.Unsafe} which may not
 * be available in all JVM implementations or may be restricted by security policies.
 * The class gracefully falls back to standard comparison when {@code Unsafe} is
 * not available.
 *
 * <p>This class is derived from the FoundationDB project and maintains
 * compatibility with FoundationDB's performance optimizations.
 */
@SuppressWarnings("checkstyle:IllegalImport") // Unsafe needed for performance
abstract class FastByteComparisons {

  private static final int UNSIGNED_MASK = 0xFF;

  /**
   * Lexicographically compare two byte arrays.
   *
   * @param buffer1 left operand, expected to not be null
   * @param buffer2 right operand, expected to not be null
   * @param offset1 Where to start comparing in the left buffer, expected to be &gt;= 0
   * @param offset2 Where to start comparing in the right buffer, expected to be &gt;= 0
   * @param length1 How much to compare from the left buffer, expected to be &gt;= 0
   * @param length2 How much to compare from the right buffer, expected to be &gt;= 0
   * @return 0 if equal, &lt; 0 if left is less than right, etc.
   */
  public static int compareTo(byte[] buffer1, int offset1, int length1,
                              byte[] buffer2, int offset2, int length2) {
    return LexicographicalComparerHolder.BEST_COMPARER.compareTo(
        buffer1, offset1, length1, buffer2, offset2, length2);
  }

  /**
   * Interface for both the java and unsafe comparators + offset based comparisons.
   *
   * @param <T>
   */
  interface Comparer<T> extends Comparator<T> {
    /**
     * Lexicographically compare two byte arrays.
     *
     * @param buffer1 left operand
     * @param buffer2 right operand
     * @param offset1 Where to start comparing in the left buffer
     * @param offset2 Where to start comparing in the right buffer
     * @param length1 How much to compare from the left buffer
     * @param length2 How much to compare from the right buffer
     * @return 0 if equal, < 0 if left is less than right, etc.
     */
    int compareTo(T buffer1, int offset1, int length1,
                  T buffer2, int offset2, int length2);
  }

  /**
   * @return a byte[] comparator for use in sorting, collections, and so on internally
   * to the Java code.
   */
  public static Comparator<byte[]> comparator() {
    return LexicographicalComparerHolder.getBestComparer();
  }

  /**
   * Pure Java Comparer
   */
  static Comparer<byte[]> lexicographicalComparerJavaImpl() {
    return LexicographicalComparerHolder.PureJavaComparer.INSTANCE;
  }

  /**
   * Unsafe Comparer
   */
  static Comparer<byte[]> lexicographicalComparerUnsafeImpl() {
    return LexicographicalComparerHolder.UnsafeComparer.INSTANCE;
  }


  /**
   * Provides a lexicographical comparer implementation; either a Java
   * implementation or a faster implementation based on {@link Unsafe}.
   *
   * <p>Uses reflection to gracefully fall back to the Java implementation if
   * {@code Unsafe} isn't available.
   */
  private static class LexicographicalComparerHolder {
    static final String UNSAFE_COMPARER_NAME =
        LexicographicalComparerHolder.class.getName() + "$UnsafeComparer";

    static final Comparer<byte[]> BEST_COMPARER = getBestComparer();

    /**
     * Returns the Unsafe-using Comparer, or falls back to the pure-Java
     * implementation if unable to do so.
     */
    static Comparer<byte[]> getBestComparer() {
      String arch = System.getProperty("os.arch");
      boolean unaligned = arch.equals("i386") || arch.equals("x86")
                          || arch.equals("amd64") || arch.equals("x86_64")
                          || arch.equals("aarch64") || arch.contains("arm");
      if (!unaligned) {
        return lexicographicalComparerJavaImpl();
      }
      try {
        Class<?> theClass = Class.forName(UNSAFE_COMPARER_NAME);

        // yes, UnsafeComparer does implement Comparer<byte[]>
        @SuppressWarnings("unchecked")
        Comparer<byte[]> comparer =
            (Comparer<byte[]>) theClass.getEnumConstants()[0];
        return comparer;
      } catch (Throwable t) { // ensure we really catch *everything*
        return lexicographicalComparerJavaImpl();
      }
    }

    /**
     * Java Comparer doing byte by byte comparisons
     */
    enum PureJavaComparer implements Comparer<byte[]> {
      INSTANCE;

      /**
       * CompareTo looking at two buffers.
       *
       * @param buffer1 left operand
       * @param buffer2 right operand
       * @param offset1 Where to start comparing in the left buffer
       * @param offset2 Where to start comparing in the right buffer
       * @param length1 How much to compare from the left buffer
       * @param length2 How much to compare from the right buffer
       * @return 0 if equal, < 0 if left is less than right, etc.
       */
      @Override
      public int compareTo(byte[] buffer1, int offset1, int length1,
                           byte[] buffer2, int offset2, int length2) {
        // Short circuit equal case
        if (buffer1 == buffer2 &&
            offset1 == offset2 &&
            length1 == length2) {
          return 0;
        }
        int end1 = offset1 + length1;
        int end2 = offset2 + length2;
        for (int i = offset1, j = offset2; i < end1 && j < end2; i++, j++) {
          int a = (buffer1[i] & UNSIGNED_MASK);
          int b = (buffer2[j] & UNSIGNED_MASK);
          if (a != b) {
            return a - b;
          }
        }
        return length1 - length2;
      }

      /**
       * Supports Comparator
       *
       * @param o1 left operand
       *           expected to not be null
       * @param o2 right operand
       *           expected to not be null
       * @return comparison
       */
      @Override
      public int compare(byte[] o1, byte[] o2) {
        return compareTo(o1, 0, o1.length, o2, 0, o2.length);
      }
    }

    /**
     * Takes advantage of word based comparisons
     */
    @SuppressWarnings("unused") // used via reflection
    enum UnsafeComparer implements Comparer<byte[]> {
      INSTANCE;

      static final Unsafe theUnsafe;

      /**
       * The offset to the first element in a byte array.
       */
      static final int BYTE_ARRAY_BASE_OFFSET;

      @Override
      public int compare(byte[] o1, byte[] o2) {
        return compareTo(o1, 0, o1.length, o2, 0, o2.length);
      }

      static {
        // Use reflection to get Unsafe instance, which works in test environments
        try {
          java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
          f.setAccessible(true);
          theUnsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
          throw new ExceptionInInitializerError("Failed to get Unsafe instance: " + e.getMessage());
        }

        BYTE_ARRAY_BASE_OFFSET = theUnsafe.arrayBaseOffset(byte[].class);

        // sanity check - this should never fail
        if (theUnsafe.arrayIndexScale(byte[].class) != 1) {
          throw new AssertionError();
        }
      }

      static final boolean LITTLE_ENDIAN =
          ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN);

      /**
       * Lexicographically compare two arrays.
       *
       * @param buffer1 left operand
       * @param buffer2 right operand
       * @param offset1 Where to start comparing in the left buffer
       * @param offset2 Where to start comparing in the right buffer
       * @param length1 How much to compare from the left buffer
       * @param length2 How much to compare from the right buffer
       * @return 0 if equal, < 0 if left is less than right, etc.
       */
      @Override
      public int compareTo(byte[] buffer1, int offset1, int length1,
                           byte[] buffer2, int offset2, int length2) {
        // Short circuit equal case
        if (buffer1 == buffer2 &&
            offset1 == offset2 &&
            length1 == length2) {
          return 0;
        }
        final int stride = 8;
        final int minLength = Math.min(length1, length2);
        int strideLimit = minLength & -stride;
        final long offset1Adj = offset1 + BYTE_ARRAY_BASE_OFFSET;
        final long offset2Adj = offset2 + BYTE_ARRAY_BASE_OFFSET;
        int i;

        /*
         * Compare 8 bytes at a time. Benchmarking on x86 shows a stride of 8 bytes is no slower
         * than 4 bytes even on 32-bit. On the other hand, it is substantially faster on 64-bit.
         */
        for (i = 0; i < strideLimit; i += stride) {
          long lw = theUnsafe.getLong(buffer1, offset1Adj + i);
          long rw = theUnsafe.getLong(buffer2, offset2Adj + i);
          if (lw != rw) {
            if (!LITTLE_ENDIAN) {
              return ((lw + Long.MIN_VALUE) < (rw + Long.MIN_VALUE)) ? -1 : 1;
            }

            /*
             * We want to compare only the first index where left[index] != right[index]. This
             * corresponds to the least significant nonzero byte in lw ^ rw, since lw and rw are
             * little-endian. Long.numberOfTrailingZeros(diff) tells us the least significant
             * nonzero bit, and zeroing out the first three bits of L.nTZ gives us the shift to get
             * that least significant nonzero byte. This comparison logic is based on UnsignedBytes
             * comparator from guava v21
             */
            int n = Long.numberOfTrailingZeros(lw ^ rw) & ~0x7;
            return ((int) ((lw >>> n) & UNSIGNED_MASK)) - ((int) ((rw >>> n) & UNSIGNED_MASK));
          }
        }

        // The epilogue to cover the last (minLength % stride) elements.
        for (; i < minLength; i++) {
          int a = (buffer1[offset1 + i] & UNSIGNED_MASK);
          int b = (buffer2[offset2 + i] & UNSIGNED_MASK);
          if (a != b) {
            return a - b;
          }
        }
        return length1 - length2;
      }
    }
  }
}
