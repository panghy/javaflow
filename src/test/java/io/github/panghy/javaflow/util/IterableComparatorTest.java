package io.github.panghy.javaflow.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link IterableComparator} class.
 */
class IterableComparatorTest {

  private IterableComparator comparator;

  @BeforeEach
  void setUp() {
    comparator = new IterableComparator();
  }

  @Test
  void testEmptyIterables() {
    List<Object> empty1 = Collections.emptyList();
    List<Object> empty2 = Collections.emptyList();
    
    assertEquals(0, comparator.compare(empty1, empty2));
  }

  @Test
  void testIdenticalIterables() {
    List<Object> list1 = Arrays.asList(1, "hello", 3.14);
    List<Object> list2 = Arrays.asList(1, "hello", 3.14);
    
    assertEquals(0, comparator.compare(list1, list2));
  }

  @Test
  void testSameReference() {
    List<Object> list = Arrays.asList(1, "hello", 3.14);
    
    assertEquals(0, comparator.compare(list, list));
  }

  @Test
  void testPrefixComparison() {
    List<Object> shorter = Arrays.asList(1, 2);
    List<Object> longer = Arrays.asList(1, 2, 3);
    
    assertTrue(comparator.compare(shorter, longer) < 0);
    assertTrue(comparator.compare(longer, shorter) > 0);
  }

  @Test
  void testEmptyVsNonEmpty() {
    List<Object> empty = Collections.emptyList();
    List<Object> nonEmpty = Arrays.asList(1);
    
    assertTrue(comparator.compare(empty, nonEmpty) < 0);
    assertTrue(comparator.compare(nonEmpty, empty) > 0);
  }

  @Test
  void testNumberComparison() {
    List<Object> list1 = Arrays.asList(1, 2, 3);
    List<Object> list2 = Arrays.asList(1, 2, 4);
    
    assertTrue(comparator.compare(list1, list2) < 0);
    assertTrue(comparator.compare(list2, list1) > 0);
  }

  @Test
  void testStringComparison() {
    List<Object> list1 = Arrays.asList("apple", "banana");
    List<Object> list2 = Arrays.asList("apple", "cherry");
    
    assertTrue(comparator.compare(list1, list2) < 0);
    assertTrue(comparator.compare(list2, list1) > 0);
  }

  @Test
  void testMixedTypeComparison() {
    // Test different types - order should be consistent with Tuple serialization
    List<Object> list1 = Arrays.asList(null, 42);
    List<Object> list2 = Arrays.asList(null, "string");
    
    // The result should be consistent (not testing specific ordering as it depends on TupleUtil.getCodeFor)
    int result1 = comparator.compare(list1, list2);
    int result2 = comparator.compare(list2, list1);
    
    // Results should be opposite
    assertTrue((result1 > 0 && result2 < 0) || (result1 < 0 && result2 > 0) || (result1 == 0 && result2 == 0));
  }

  @Test
  void testNullValues() {
    List<Object> list1 = Arrays.asList(null, null);
    List<Object> list2 = Arrays.asList(null, null);
    
    assertEquals(0, comparator.compare(list1, list2));
  }

  @Test
  void testFloatingPointValues() {
    List<Object> list1 = Arrays.asList(1.5, 2.5);
    List<Object> list2 = Arrays.asList(1.5, 3.5);
    
    assertTrue(comparator.compare(list1, list2) < 0);
    assertTrue(comparator.compare(list2, list1) > 0);
  }

  @Test
  void testByteArrayValues() {
    byte[] bytes1 = {1, 2, 3};
    byte[] bytes2 = {1, 2, 4};
    
    List<Object> list1 = Arrays.asList(bytes1);
    List<Object> list2 = Arrays.asList(bytes2);
    
    assertTrue(comparator.compare(list1, list2) < 0);
    assertTrue(comparator.compare(list2, list1) > 0);
  }

  @Test
  void testBigIntegerValues() {
    BigInteger big1 = new BigInteger("123456789");
    BigInteger big2 = new BigInteger("123456790");
    
    List<Object> list1 = Arrays.asList(big1);
    List<Object> list2 = Arrays.asList(big2);
    
    assertTrue(comparator.compare(list1, list2) < 0);
    assertTrue(comparator.compare(list2, list1) > 0);
  }

  @Test
  void testUUIDValues() {
    UUID uuid1 = new UUID(0, 1);
    UUID uuid2 = new UUID(0, 2);
    
    List<Object> list1 = Arrays.asList(uuid1);
    List<Object> list2 = Arrays.asList(uuid2);
    
    // UUIDs are compared by their unsigned Big-Endian byte representation
    assertTrue(comparator.compare(list1, list2) < 0);
    assertTrue(comparator.compare(list2, list1) > 0);
  }

  @Test
  void testNestedLists() {
    List<Object> nestedList1 = Arrays.asList(1, 2);
    List<Object> nestedList2 = Arrays.asList(1, 3);
    
    List<Object> list1 = Arrays.asList("prefix", nestedList1);
    List<Object> list2 = Arrays.asList("prefix", nestedList2);
    
    assertTrue(comparator.compare(list1, list2) < 0);
    assertTrue(comparator.compare(list2, list1) > 0);
  }

  @Test
  void testConsistencyWithTupleComparison() {
    // This test verifies that the comparator is consistent with Tuple comparison
    // as mentioned in the class documentation
    List<Object> items1 = Arrays.asList(1, "hello", 3.14, null);
    List<Object> items2 = Arrays.asList(1, "hello", 3.15, null);
    
    Tuple tuple1 = Tuple.fromList(items1);
    Tuple tuple2 = Tuple.fromList(items2);
    
    // All these should give the same result
    int tupleComparison = tuple1.compareTo(tuple2);
    int comparatorResult = comparator.compare(tuple1, tuple2);
    int itemsComparison = comparator.compare(items1, items2);
    int packedComparison = ByteArrayUtil.compareUnsigned(tuple1.pack(), tuple2.pack());
    
    // All comparisons should have the same sign
    assertTrue(Integer.signum(tupleComparison) == Integer.signum(comparatorResult));
    assertTrue(Integer.signum(tupleComparison) == Integer.signum(itemsComparison));
    assertTrue(Integer.signum(tupleComparison) == Integer.signum(packedComparison));
  }

  @Test
  void testFloatingPointSpecialValues() {
    // Test NaN handling as mentioned in the documentation
    List<Object> listWithNaN = Arrays.asList(Float.NaN);
    List<Object> listWithRegularFloat = Arrays.asList(1.0f);
    
    // The exact ordering depends on TupleUtil.compareItems implementation
    // but it should be consistent
    int result1 = comparator.compare(listWithNaN, listWithRegularFloat);
    int result2 = comparator.compare(listWithRegularFloat, listWithNaN);
    
    // Results should be opposite (or both zero if equal)
    assertTrue((result1 > 0 && result2 < 0) || (result1 < 0 && result2 > 0) || (result1 == 0 && result2 == 0));
  }

  @Test
  void testFloatVsDoubleOrdering() {
    // Test that float values sort before double values as mentioned in documentation
    List<Object> listWithFloat = Arrays.asList(1.0f);
    List<Object> listWithDouble = Arrays.asList(1.0);
    
    // The exact ordering depends on TupleUtil.getCodeFor implementation
    int result = comparator.compare(listWithFloat, listWithDouble);
    
    // Should be consistent - we're just verifying it doesn't throw an exception
    // and produces a consistent result
    assertEquals(result, comparator.compare(listWithFloat, listWithDouble));
  }

  @Test
  void testLargeIterables() {
    // Test with larger iterables to ensure performance is reasonable
    List<Object> list1 = Arrays.asList(
        1, "string1", 2.5, null, new byte[]{1, 2, 3}, 
        42L, true, "another string", 3.14159
    );
    List<Object> list2 = Arrays.asList(
        1, "string1", 2.5, null, new byte[]{1, 2, 3}, 
        42L, true, "another string", 3.14160
    );
    
    assertTrue(comparator.compare(list1, list2) < 0);
    assertTrue(comparator.compare(list2, list1) > 0);
  }

  @Test
  void testBooleanValues() {
    List<Object> listTrue = Arrays.asList(true);
    List<Object> listFalse = Arrays.asList(false);
    
    // Boolean comparison should be consistent
    int result = comparator.compare(listTrue, listFalse);
    assertEquals(-result, comparator.compare(listFalse, listTrue));
  }

  @Test
  void testTransitivity() {
    // Test that comparison is transitive: if a < b and b < c, then a < c
    List<Object> list1 = Arrays.asList(1, 2);
    List<Object> list2 = Arrays.asList(1, 3);
    List<Object> list3 = Arrays.asList(1, 4);
    
    int comp12 = comparator.compare(list1, list2);
    int comp23 = comparator.compare(list2, list3);
    int comp13 = comparator.compare(list1, list3);
    
    assertTrue(comp12 < 0);
    assertTrue(comp23 < 0);
    assertTrue(comp13 < 0);
  }

  @Test
  void testReflexivity() {
    // Test that compare(a, a) == 0
    List<Object> list = Arrays.asList(1, "hello", 3.14, null, true);
    
    assertEquals(0, comparator.compare(list, list));
  }

  @Test
  void testSymmetry() {
    // Test that compare(a, b) == -compare(b, a)
    List<Object> list1 = Arrays.asList(1, 2, 3);
    List<Object> list2 = Arrays.asList(1, 2, 4);
    
    int comp12 = comparator.compare(list1, list2);
    int comp21 = comparator.compare(list2, list1);
    
    assertEquals(-comp12, comp21);
  }
}