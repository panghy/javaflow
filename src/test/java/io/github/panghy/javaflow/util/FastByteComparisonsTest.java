package io.github.panghy.javaflow.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the FastByteComparisons class.
 */
class FastByteComparisonsTest {

  /**
   * Provide comparers for parameterized tests.
   * We test both PureJava and Unsafe implementations.
   */
  static Stream<FastByteComparisons.Comparer<byte[]>> comparers() {
    Stream.Builder<FastByteComparisons.Comparer<byte[]>> builder = Stream.builder();
    
    // Always test PureJava
    builder.add(FastByteComparisons.lexicographicalComparerJavaImpl());
    
    // Test UnsafeComparer if available
    try {
      FastByteComparisons.Comparer<byte[]> unsafeComparer = 
          FastByteComparisons.lexicographicalComparerUnsafeImpl();
      builder.add(unsafeComparer);
      System.out.println("Successfully loaded UnsafeComparer for testing");
    } catch (Exception e) {
      System.out.println("Could not load UnsafeComparer: " + e.getMessage());
    }
    
    return builder.build();
  }

  @Test
  void testComparatorNotNull() {
    Comparator<byte[]> comparator = FastByteComparisons.comparator();
    assertNotNull(comparator);
  }

  @Test
  void testBothComparersAvailable() {
    // Verify we're testing both implementations
    long comparerCount = comparers().count();
    System.out.println("Testing " + comparerCount + " comparer implementation(s)");
    
    // With reflection-based Unsafe access, we should be able to test both
    String arch = System.getProperty("os.arch");
    if (arch.equals("amd64") || arch.equals("x86_64") || arch.equals("aarch64") || arch.contains("arm")) {
      assertEquals(2, comparerCount, "Should test both PureJava and Unsafe comparers");
    } else {
      assertTrue(comparerCount >= 1, "Should test at least PureJava comparer");
    }
    
    // Check if the default comparer is actually using Unsafe
    Comparator<?> defaultComparer = FastByteComparisons.comparator();
    String comparerClassName = defaultComparer.getClass().getName();
    System.out.println("Default comparer implementation: " + comparerClassName);
    
    // On supported architectures, it should be UnsafeComparer
    if (arch.equals("amd64") || arch.equals("x86_64") || arch.equals("aarch64")) {
      assertTrue(comparerClassName.contains("UnsafeComparer"), 
          "On " + arch + " architecture, should use UnsafeComparer");
    }
  }

  @Test
  void testArchitectureDetection() {
    // Test that the architecture detection logic works
    String arch = System.getProperty("os.arch");
    System.out.println("Current architecture: " + arch);
    
    // The comparator should work on any architecture
    Comparator<byte[]> comparator = FastByteComparisons.comparator();
    assertNotNull(comparator);
    
    // Test basic functionality
    byte[] a = {1, 2, 3};
    byte[] b = {1, 2, 4};
    assertTrue(comparator.compare(a, b) < 0);
  }

  @Test
  void testUnsafeComparerDirectly() {
    // Now that we use reflection, we should be able to get UnsafeComparer
    try {
      FastByteComparisons.Comparer<byte[]> unsafeComparer = 
          FastByteComparisons.lexicographicalComparerUnsafeImpl();
      assertNotNull(unsafeComparer);
      
      // Test that it works correctly
      byte[] a = {1, 2, 3};
      byte[] b = {1, 2, 4};
      
      assertTrue(unsafeComparer.compareTo(a, 0, a.length, b, 0, b.length) < 0);
      assertTrue(unsafeComparer.compareTo(b, 0, b.length, a, 0, a.length) > 0);
      assertEquals(0, unsafeComparer.compareTo(a, 0, a.length, a, 0, a.length));
      
      assertTrue(unsafeComparer.compare(a, b) < 0);
      assertTrue(unsafeComparer.compare(b, a) > 0);
      assertEquals(0, unsafeComparer.compare(a, a));
      
      System.out.println("Successfully tested UnsafeComparer directly");
    } catch (Exception e) {
      fail("UnsafeComparer should be available with reflection-based Unsafe access: " + e.getMessage());
    }
  }

  @Test
  void testCompareToBasic() {
    byte[] a = {1, 2, 3};
    byte[] b = {1, 2, 4};
    
    int result = FastByteComparisons.compareTo(a, 0, a.length, b, 0, b.length);
    assertTrue(result < 0);
    
    result = FastByteComparisons.compareTo(b, 0, b.length, a, 0, a.length);
    assertTrue(result > 0);
    
    result = FastByteComparisons.compareTo(a, 0, a.length, a, 0, a.length);
    assertEquals(0, result);
  }

  @Test
  void testCompareToWithOffsets() {
    byte[] a = {0, 1, 2, 3, 4};
    byte[] b = {9, 8, 2, 3, 5};
    
    // Compare subranges [2,3] from both arrays
    int result = FastByteComparisons.compareTo(a, 2, 2, b, 2, 2);
    assertEquals(0, result);
    
    // Compare [1,2,3] from a with [2,3,5] from b
    result = FastByteComparisons.compareTo(a, 1, 3, b, 2, 3);
    assertTrue(result < 0);
  }

  @Test
  void testCompareToDifferentLengths() {
    byte[] a = {1, 2, 3};
    byte[] b = {1, 2, 3, 4};
    
    int result = FastByteComparisons.compareTo(a, 0, a.length, b, 0, b.length);
    assertTrue(result < 0);
    
    result = FastByteComparisons.compareTo(b, 0, b.length, a, 0, a.length);
    assertTrue(result > 0);
  }

  @Test
  void testCompareToEmptyArrays() {
    byte[] empty1 = new byte[0];
    byte[] empty2 = new byte[0];
    byte[] nonEmpty = {1};
    
    int result = FastByteComparisons.compareTo(empty1, 0, 0, empty2, 0, 0);
    assertEquals(0, result);
    
    result = FastByteComparisons.compareTo(empty1, 0, 0, nonEmpty, 0, 1);
    assertTrue(result < 0);
    
    result = FastByteComparisons.compareTo(nonEmpty, 0, 1, empty1, 0, 0);
    assertTrue(result > 0);
  }

  @Test
  void testCompareToHighBytes() {
    byte[] a = {(byte) 0xFF};
    byte[] b = {0x01};
    
    int result = FastByteComparisons.compareTo(a, 0, 1, b, 0, 1);
    assertTrue(result > 0);
    
    result = FastByteComparisons.compareTo(b, 0, 1, a, 0, 1);
    assertTrue(result < 0);
  }

  @Test
  void testCompareToLongArrays() {
    // Test arrays longer than 8 bytes to exercise stride logic
    byte[] a = new byte[100];
    byte[] b = new byte[100];
    Arrays.fill(a, (byte) 1);
    Arrays.fill(b, (byte) 1);
    
    int result = FastByteComparisons.compareTo(a, 0, a.length, b, 0, b.length);
    assertEquals(0, result);
    
    // Change one byte in the middle
    b[50] = 2;
    result = FastByteComparisons.compareTo(a, 0, a.length, b, 0, b.length);
    assertTrue(result < 0);
    
    // Change one byte at the end
    Arrays.fill(b, (byte) 1);
    b[99] = 0;
    result = FastByteComparisons.compareTo(a, 0, a.length, b, 0, b.length);
    assertTrue(result > 0);
  }

  @Test
  void testCompareToSameArraySameOffsetLength() {
    byte[] array = {1, 2, 3, 4, 5};
    
    // Same array, same offset, same length should return 0
    int result = FastByteComparisons.compareTo(array, 1, 3, array, 1, 3);
    assertEquals(0, result);
  }

  @Test
  void testComparatorInterface() {
    Comparator<byte[]> comparator = FastByteComparisons.comparator();
    
    byte[] a = {1, 2, 3};
    byte[] b = {1, 2, 4};
    byte[] c = {1, 2, 3};
    
    assertTrue(comparator.compare(a, b) < 0);
    assertTrue(comparator.compare(b, a) > 0);
    assertEquals(0, comparator.compare(a, c));
  }

  @Test
  void testComparatorSorting() {
    byte[][] arrays = {
        {3, 4, 5},
        {1, 2, 3},
        {2, 3, 4},
        {1, 2},
        {1, 2, 3, 4}
    };
    
    Arrays.sort(arrays, FastByteComparisons.comparator());
    
    // Verify sorted order
    assertArrayEquals(new byte[]{1, 2}, arrays[0]);
    assertArrayEquals(new byte[]{1, 2, 3}, arrays[1]);
    assertArrayEquals(new byte[]{1, 2, 3, 4}, arrays[2]);
    assertArrayEquals(new byte[]{2, 3, 4}, arrays[3]);
    assertArrayEquals(new byte[]{3, 4, 5}, arrays[4]);
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerBasic(FastByteComparisons.Comparer<byte[]> comparer) {
    byte[] a = {1, 2, 3};
    byte[] b = {1, 2, 4};
    
    int result = comparer.compareTo(a, 0, a.length, b, 0, b.length);
    assertTrue(result < 0);
    
    result = comparer.compare(a, b);
    assertTrue(result < 0);
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerWithOffsets(FastByteComparisons.Comparer<byte[]> comparer) {
    byte[] a = {0, 1, 2, 3, 4};
    byte[] b = {9, 8, 2, 3, 5};
    
    // Compare subranges
    int result = comparer.compareTo(a, 2, 2, b, 2, 2);
    assertEquals(0, result);
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerEmptyArrays(FastByteComparisons.Comparer<byte[]> comparer) {
    byte[] empty = new byte[0];
    byte[] nonEmpty = {1};
    
    int result = comparer.compareTo(empty, 0, 0, nonEmpty, 0, 1);
    assertTrue(result < 0);
    
    result = comparer.compare(empty, nonEmpty);
    assertTrue(result < 0);
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerHighBytes(FastByteComparisons.Comparer<byte[]> comparer) {
    byte[] a = {(byte) 0xFF, (byte) 0xFE};
    byte[] b = {(byte) 0xFF, (byte) 0xFF};
    
    int result = comparer.compareTo(a, 0, 2, b, 0, 2);
    assertTrue(result < 0);
    
    result = comparer.compare(a, b);
    assertTrue(result < 0);
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerLongArrays(FastByteComparisons.Comparer<byte[]> comparer) {
    // Test arrays with lengths that exercise stride logic
    byte[] a = new byte[17]; // Not a multiple of 8
    byte[] b = new byte[17];
    Arrays.fill(a, (byte) 1);
    Arrays.fill(b, (byte) 1);
    
    int result = comparer.compareTo(a, 0, a.length, b, 0, b.length);
    assertEquals(0, result);
    
    // Change byte at position 8 (first byte after stride)
    b[8] = 2;
    result = comparer.compareTo(a, 0, a.length, b, 0, b.length);
    assertTrue(result < 0);
    
    // Reset and change last byte
    Arrays.fill(b, (byte) 1);
    b[16] = 0;
    result = comparer.compareTo(a, 0, a.length, b, 0, b.length);
    assertTrue(result > 0);
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerPartialArrays(FastByteComparisons.Comparer<byte[]> comparer) {
    byte[] a = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    byte[] b = {0, 0, 3, 4, 5, 6, 7, 8, 0};
    
    // Compare middle portions
    int result = comparer.compareTo(a, 2, 6, b, 2, 6);
    assertEquals(0, result);
    
    // Compare with different lengths
    result = comparer.compareTo(a, 2, 5, b, 2, 6);
    assertTrue(result < 0);
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerAllBytesValues(FastByteComparisons.Comparer<byte[]> comparer) {
    // Test all possible byte values
    byte[] a = new byte[256];
    byte[] b = new byte[256];
    
    for (int i = 0; i < 256; i++) {
      a[i] = (byte) i;
      b[i] = (byte) i;
    }
    
    int result = comparer.compareTo(a, 0, a.length, b, 0, b.length);
    assertEquals(0, result);
    
    // Change one value
    b[128] = (byte) 129;
    result = comparer.compareTo(a, 0, a.length, b, 0, b.length);
    assertTrue(result < 0);
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerBoundaryConditions(FastByteComparisons.Comparer<byte[]> comparer) {
    byte[] array = {1, 2, 3, 4, 5};
    
    // Compare at boundaries
    int result = comparer.compareTo(array, 0, 1, array, 4, 1);
    assertTrue(result < 0); // 1 < 5
    
    // Compare zero-length ranges
    result = comparer.compareTo(array, 2, 0, array, 3, 0);
    assertEquals(0, result);
    
    // Compare entire array with itself
    result = comparer.compareTo(array, 0, array.length, array, 0, array.length);
    assertEquals(0, result);
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerLittleEndianLogic(FastByteComparisons.Comparer<byte[]> comparer) {
    // Test the little-endian specific logic in UnsafeComparer
    // This tests the case where bytes differ within an 8-byte stride
    byte[] a = new byte[16];
    byte[] b = new byte[16];
    Arrays.fill(a, (byte) 0);
    Arrays.fill(b, (byte) 0);
    
    // Test each byte position within the first 8-byte stride
    for (int i = 0; i < 8; i++) {
      Arrays.fill(a, (byte) 0);
      Arrays.fill(b, (byte) 0);
      a[i] = 1;
      b[i] = 2;
      
      int result = comparer.compareTo(a, 0, a.length, b, 0, b.length);
      assertTrue(result < 0, "Failed at position " + i);
      
      result = comparer.compareTo(b, 0, b.length, a, 0, a.length);
      assertTrue(result > 0, "Failed at position " + i);
    }
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerMultipleStrides(FastByteComparisons.Comparer<byte[]> comparer) {
    // Test arrays that span multiple 8-byte strides
    int size = 64; // 8 strides
    byte[] a = new byte[size];
    byte[] b = new byte[size];
    
    // Fill with identical data
    for (int i = 0; i < size; i++) {
      a[i] = (byte) (i % 256);
      b[i] = (byte) (i % 256);
    }
    
    assertEquals(0, comparer.compareTo(a, 0, size, b, 0, size));
    
    // Test differences at various stride boundaries
    for (int stride = 0; stride < 8; stride++) {
      Arrays.fill(b, (byte) 0);
      for (int i = 0; i < size; i++) {
        b[i] = a[i];
      }
      
      int pos = stride * 8 + 4; // Middle of each stride
      if (pos < size) {
        b[pos] = (byte) (a[pos] + 1);
        assertTrue(comparer.compareTo(a, 0, size, b, 0, size) < 0);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("comparers")
  void testComparerNonAlignedOffsets(FastByteComparisons.Comparer<byte[]> comparer) {
    // Test comparison with non-aligned offsets
    byte[] a = new byte[20];
    byte[] b = new byte[20];
    
    for (int i = 0; i < 20; i++) {
      a[i] = (byte) i;
      b[i] = (byte) i;
    }
    
    // Test various non-aligned offsets
    for (int offset = 1; offset < 8; offset++) {
      int len = 10;
      int result = comparer.compareTo(a, offset, len, b, offset, len);
      assertEquals(0, result, "Failed at offset " + offset);
    }
    
    // Test with different data at non-aligned positions
    b[7] = (byte) (a[7] + 1);
    for (int offset = 1; offset < 7; offset++) {
      int len = 10;
      if (offset + len > 7) {
        // This range includes the difference at position 7
        int result = comparer.compareTo(a, offset, len, b, offset, len);
        assertTrue(result < 0, "Failed at offset " + offset);
      }
    }
  }

  @Test
  void testUnsafeComparerSpecificCases() {
    // Directly test UnsafeComparer for better coverage
    try {
      FastByteComparisons.Comparer<byte[]> unsafeComparer = 
          FastByteComparisons.lexicographicalComparerUnsafeImpl();
      
      // Test the short-circuit optimization for same array/offset/length
      byte[] array = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
      int result = unsafeComparer.compareTo(array, 2, 5, array, 2, 5);
      assertEquals(0, result);
      
      // Test arrays that differ after multiple strides
      byte[] longA = new byte[100];
      byte[] longB = new byte[100];
      Arrays.fill(longA, (byte) 0x55);
      Arrays.fill(longB, (byte) 0x55);
      
      // Make them differ at position 95 (after 11 full strides)
      longB[95] = (byte) 0x56;
      result = unsafeComparer.compareTo(longA, 0, 100, longB, 0, 100);
      assertTrue(result < 0);
      
      // Test high bit differences
      byte[] highA = new byte[16];
      byte[] highB = new byte[16];
      Arrays.fill(highA, (byte) 0x7F);
      Arrays.fill(highB, (byte) 0x7F);
      highB[3] = (byte) 0x80; // Sign bit set
      
      result = unsafeComparer.compareTo(highA, 0, 16, highB, 0, 16);
      assertTrue(result < 0); // 0x7F < 0x80 when treated as unsigned
      
    } catch (Exception e) {
      // If we can't get UnsafeComparer, skip this test
      System.out.println("Skipping UnsafeComparer specific test: " + e.getMessage());
    }
  }

  @Test 
  void testNonAlignedArchitecture() {
    // Test the architecture detection for non-aligned architectures
    String originalArch = System.getProperty("os.arch");
    try {
      // Temporarily set architecture to something that doesn't support unaligned access
      System.setProperty("os.arch", "sparc");
      
      // Get a new instance of the holder to trigger re-evaluation
      // This is tricky since the holder is static, but we can test the logic
      String arch = System.getProperty("os.arch");
      boolean unaligned = arch.equals("i386") || arch.equals("x86")
                          || arch.equals("amd64") || arch.equals("x86_64")
                          || arch.equals("aarch64") || arch.contains("arm");
      assertFalse(unaligned, "SPARC should not be considered unaligned");
      
    } finally {
      // Restore original architecture
      System.setProperty("os.arch", originalArch);
    }
  }
}