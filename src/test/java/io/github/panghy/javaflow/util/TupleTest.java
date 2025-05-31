package io.github.panghy.javaflow.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link Tuple} class.
 */
class TupleTest {

  @Test
  void testEmptyTuple() {
    Tuple empty = new Tuple();
    
    assertEquals(0, empty.size());
    assertTrue(empty.isEmpty());
    assertEquals(Collections.emptyList(), empty.getItems());
    assertEquals(0, empty.getPackedSize());
    assertArrayEquals(new byte[0], empty.pack());
  }

  @Test
  void testAddString() {
    Tuple tuple = new Tuple()
        .add("hello")
        .add("world");
    
    assertEquals(2, tuple.size());
    assertEquals("hello", tuple.getString(0));
    assertEquals("world", tuple.getString(1));
    assertEquals(Arrays.asList("hello", "world"), tuple.getItems());
  }

  @Test
  void testAddLong() {
    Tuple tuple = new Tuple()
        .add(42L)
        .add(-100L);
    
    assertEquals(2, tuple.size());
    assertEquals(42L, tuple.getLong(0));
    assertEquals(-100L, tuple.getLong(1));
  }

  @Test
  void testAddBytes() {
    byte[] bytes1 = {1, 2, 3};
    byte[] bytes2 = {4, 5, 6};
    
    Tuple tuple = new Tuple()
        .add(bytes1)
        .add(bytes2);
    
    assertEquals(2, tuple.size());
    assertArrayEquals(bytes1, tuple.getBytes(0));
    assertArrayEquals(bytes2, tuple.getBytes(1));
  }

  @Test
  void testAddBoolean() {
    Tuple tuple = new Tuple()
        .add(true)
        .add(false);
    
    assertEquals(2, tuple.size());
    assertTrue(tuple.getBoolean(0));
    assertFalse(tuple.getBoolean(1));
  }

  @Test
  void testAddUUID() {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    
    Tuple tuple = new Tuple()
        .add(uuid1)
        .add(uuid2);
    
    assertEquals(2, tuple.size());
    assertEquals(uuid1, tuple.getUUID(0));
    assertEquals(uuid2, tuple.getUUID(1));
  }

  @Test
  void testAddBigInteger() {
    BigInteger big1 = new BigInteger("123456789012345678901234567890");
    BigInteger big2 = new BigInteger("-987654321098765432109876543210");
    
    Tuple tuple = new Tuple()
        .add(big1)
        .add(big2);
    
    assertEquals(2, tuple.size());
    assertEquals(big1, tuple.getBigInteger(0));
    assertEquals(big2, tuple.getBigInteger(1));
  }

  @Test
  void testAddFloat() {
    Tuple tuple = new Tuple()
        .add(3.14f)
        .add(-2.71f);
    
    assertEquals(2, tuple.size());
    assertEquals(3.14f, tuple.getFloat(0), 0.001f);
    assertEquals(-2.71f, tuple.getFloat(1), 0.001f);
  }

  @Test
  void testAddDouble() {
    Tuple tuple = new Tuple()
        .add(3.14159)
        .add(-2.71828);
    
    assertEquals(2, tuple.size());
    assertEquals(3.14159, tuple.getDouble(0), 0.00001);
    assertEquals(-2.71828, tuple.getDouble(1), 0.00001);
  }

  @Test
  void testAddList() {
    List<Object> list1 = Arrays.asList(1, "hello", true);
    List<Object> list2 = Arrays.asList("world", 42);
    
    Tuple tuple = new Tuple()
        .add(list1)
        .add(list2);
    
    assertEquals(2, tuple.size());
    assertEquals(list1, tuple.getNestedList(0));
    assertEquals(list2, tuple.getNestedList(1));
  }

  @Test
  void testAddTuple() {
    Tuple nested1 = new Tuple().add("nested").add(1);
    Tuple nested2 = new Tuple().add("another").add(2);
    
    Tuple tuple = new Tuple()
        .add(nested1)
        .add(nested2);
    
    assertEquals(2, tuple.size());
    assertEquals(nested1, tuple.getNestedTuple(0));
    assertEquals(nested2, tuple.getNestedTuple(1));
  }

  @Test
  void testAddBytesWithOffsetAndLength() {
    byte[] source = {1, 2, 3, 4, 5, 6};
    
    Tuple tuple = new Tuple()
        .add(source, 1, 3); // Should add {2, 3, 4}
    
    assertEquals(1, tuple.size());
    assertArrayEquals(new byte[]{2, 3, 4}, tuple.getBytes(0));
  }

  @Test
  void testAddAll() {
    List<Object> items = Arrays.asList("hello", 42, true);
    
    Tuple tuple = new Tuple().addAll(items);
    
    assertEquals(3, tuple.size());
    assertEquals("hello", tuple.getString(0));
    assertEquals(42L, tuple.getLong(1));
    assertTrue(tuple.getBoolean(2));
  }

  @Test
  void testAddAllTuple() {
    Tuple tuple1 = new Tuple().add("first").add(1);
    Tuple tuple2 = new Tuple().add("second").add(2);
    
    Tuple combined = tuple1.addAll(tuple2);
    
    assertEquals(4, combined.size());
    assertEquals("first", combined.getString(0));
    assertEquals(1L, combined.getLong(1));
    assertEquals("second", combined.getString(2));
    assertEquals(2L, combined.getLong(3));
  }

  @Test
  void testAddObject() {
    Tuple tuple = new Tuple()
        .addObject("string")
        .addObject(42L)
        .addObject(true)
        .addObject(new byte[]{1, 2, 3});
    
    assertEquals(4, tuple.size());
    assertEquals("string", tuple.get(0));
    assertEquals(42L, tuple.get(1));
    assertEquals(true, tuple.get(2));
    assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) tuple.get(3));
  }

  @Test
  void testNullValues() {
    Tuple tuple = new Tuple()
        .add((String) null)
        .add((byte[]) null)
        .add((UUID) null)
        .add((List<?>) null)
        .add((Tuple) null);
    
    assertEquals(5, tuple.size());
    assertNull(tuple.getString(0));
    assertNull(tuple.getBytes(1));
    assertNull(tuple.getUUID(2));
    assertNull(tuple.getNestedList(3));
    assertNull(tuple.getNestedTuple(4));
  }

  @Test
  void testNullNumberThrowsException() {
    Tuple tuple = new Tuple().addObject(null);
    
    assertThrows(NullPointerException.class, () -> tuple.getLong(0));
    assertThrows(NullPointerException.class, () -> tuple.getFloat(0));
    assertThrows(NullPointerException.class, () -> tuple.getDouble(0));
    assertThrows(NullPointerException.class, () -> tuple.getBoolean(0));
  }

  @Test
  void testFromBytes() {
    Tuple original = new Tuple().add("hello").add(42L).add(true);
    byte[] packed = original.pack();
    
    Tuple unpacked = Tuple.fromBytes(packed);
    
    assertEquals(original, unpacked);
    assertEquals(original.getItems(), unpacked.getItems());
  }

  @Test
  void testFromBytesWithOffsetAndLength() {
    Tuple original = new Tuple().add("test");
    byte[] packed = original.pack();
    
    // Create a larger array with the packed data in the middle
    byte[] largerArray = new byte[packed.length + 4];
    System.arraycopy(packed, 0, largerArray, 2, packed.length);
    
    Tuple unpacked = Tuple.fromBytes(largerArray, 2, packed.length);
    
    assertEquals(original, unpacked);
  }

  @Test
  void testFromItems() {
    List<Object> items = Arrays.asList("hello", 42L, true, new byte[]{1, 2, 3});
    
    Tuple tuple = Tuple.fromItems(items);
    
    assertEquals(4, tuple.size());
    assertEquals(items, tuple.getItems());
  }

  @Test
  void testFromList() {
    List<Object> items = Arrays.asList("hello", 42L, true);
    
    Tuple tuple = Tuple.fromList(items);
    
    assertEquals(3, tuple.size());
    assertEquals(items, tuple.getItems());
  }

  @Test
  void testFromVarargs() {
    Tuple tuple = Tuple.from("hello", 42L, true, new byte[]{1, 2, 3});
    
    assertEquals(4, tuple.size());
    assertEquals("hello", tuple.getString(0));
    assertEquals(42L, tuple.getLong(1));
    assertTrue(tuple.getBoolean(2));
    assertArrayEquals(new byte[]{1, 2, 3}, tuple.getBytes(3));
  }

  @Test
  void testFromStream() {
    Tuple tuple = Tuple.fromStream(Arrays.asList("a", "b", "c").stream());
    
    assertEquals(3, tuple.size());
    assertEquals(Arrays.asList("a", "b", "c"), tuple.getItems());
  }

  @Test
  void testPackAndUnpack() {
    Tuple original = new Tuple()
        .add("hello")
        .add(42L)
        .add(true)
        .add(new byte[]{1, 2, 3})
        .add(3.14);
    
    byte[] packed = original.pack();
    Tuple unpacked = Tuple.fromBytes(packed);
    
    assertEquals(original, unpacked);
    assertEquals(original.size(), unpacked.size());
    
    for (int i = 0; i < original.size(); i++) {
      Object originalItem = original.get(i);
      Object unpackedItem = unpacked.get(i);
      
      if (originalItem instanceof byte[] && unpackedItem instanceof byte[]) {
        assertArrayEquals((byte[]) originalItem, (byte[]) unpackedItem);
      } else {
        assertEquals(originalItem, unpackedItem);
      }
    }
  }

  @Test
  void testPackWithPrefix() {
    Tuple tuple = new Tuple().add("test");
    byte[] prefix = {1, 2, 3};
    
    byte[] packedWithPrefix = tuple.pack(prefix);
    
    // Should start with the prefix
    for (int i = 0; i < prefix.length; i++) {
      assertEquals(prefix[i], packedWithPrefix[i]);
    }
    
    // The rest should be the tuple data
    byte[] normalPacked = tuple.pack();
    for (int i = 0; i < normalPacked.length; i++) {
      assertEquals(normalPacked[i], packedWithPrefix[i + prefix.length]);
    }
  }

  @Test
  void testPackInto() {
    Tuple tuple = new Tuple().add("hello").add(42L);
    
    ByteBuffer buffer = ByteBuffer.allocate(tuple.getPackedSize());
    tuple.packInto(buffer);
    
    byte[] result = buffer.array();
    byte[] expected = tuple.pack();
    
    assertArrayEquals(expected, result);
  }

  @Test
  void testPopFront() {
    Tuple original = new Tuple().add("first").add("second").add("third");
    
    Tuple popped = original.popFront();
    
    assertEquals(2, popped.size());
    assertEquals("second", popped.getString(0));
    assertEquals("third", popped.getString(1));
    
    // Original should be unchanged
    assertEquals(3, original.size());
  }

  @Test
  void testPopFrontEmptyTuple() {
    Tuple empty = new Tuple();
    
    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
      empty.popFront();
    });
    
    assertEquals("Tuple contains no elements", exception.getMessage());
  }

  @Test
  void testPopBack() {
    Tuple original = new Tuple().add("first").add("second").add("third");
    
    Tuple popped = original.popBack();
    
    assertEquals(2, popped.size());
    assertEquals("first", popped.getString(0));
    assertEquals("second", popped.getString(1));
    
    // Original should be unchanged
    assertEquals(3, original.size());
  }

  @Test
  void testPopBackEmptyTuple() {
    Tuple empty = new Tuple();
    
    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
      empty.popBack();
    });
    
    assertEquals("Tuple contains no elements", exception.getMessage());
  }

  @Test
  void testRange() {
    Tuple tuple = new Tuple().add("prefix").add("test");
    
    Range range = tuple.range();
    
    // The range method creates a range by appending 0x0 and 0xff
    byte[] packed = tuple.pack();
    byte[] expectedBegin = ByteArrayUtil.join(packed, new byte[]{0x0});
    byte[] expectedEnd = ByteArrayUtil.join(packed, new byte[]{(byte) 0xff});
    
    assertArrayEquals(expectedBegin, range.begin);
    assertArrayEquals(expectedEnd, range.end);
  }

  @Test
  void testRangeWithPrefix() {
    Tuple tuple = new Tuple().add("test");
    byte[] prefix = {1, 2, 3};
    
    Range range = tuple.range(prefix);
    
    // The range method with prefix creates a range by appending 0x0 and 0xff
    byte[] packedWithPrefix = tuple.pack(prefix);
    byte[] expectedBegin = ByteArrayUtil.join(packedWithPrefix, new byte[]{0x0});
    byte[] expectedEnd = ByteArrayUtil.join(packedWithPrefix, new byte[]{(byte) 0xff});
    
    assertArrayEquals(expectedBegin, range.begin);
    assertArrayEquals(expectedEnd, range.end);
  }

  @Test
  void testCompareTo() {
    Tuple tuple1 = new Tuple().add("apple").add(1);
    Tuple tuple2 = new Tuple().add("apple").add(2);
    Tuple tuple3 = new Tuple().add("banana").add(1);
    
    assertTrue(tuple1.compareTo(tuple2) < 0);
    assertTrue(tuple2.compareTo(tuple1) > 0);
    assertTrue(tuple1.compareTo(tuple3) < 0);
    assertTrue(tuple3.compareTo(tuple1) > 0);
    assertEquals(0, tuple1.compareTo(tuple1));
  }

  @Test
  void testCompareToWithDifferentLengths() {
    Tuple shorter = new Tuple().add("test");
    Tuple longer = new Tuple().add("test").add("more");
    
    assertTrue(shorter.compareTo(longer) < 0);
    assertTrue(longer.compareTo(shorter) > 0);
  }

  @Test
  void testEquals() {
    Tuple tuple1 = new Tuple().add("hello").add(42L);
    Tuple tuple2 = new Tuple().add("hello").add(42L);
    Tuple tuple3 = new Tuple().add("hello").add(43L);
    
    assertEquals(tuple1, tuple2);
    assertNotEquals(tuple1, tuple3);
    assertNotEquals(tuple1, null);
    assertNotEquals(tuple1, "not a tuple");
  }

  @Test
  void testHashCode() {
    Tuple tuple1 = new Tuple().add("hello").add(42L);
    Tuple tuple2 = new Tuple().add("hello").add(42L);
    Tuple tuple3 = new Tuple().add("hello").add(43L);
    
    assertEquals(tuple1.hashCode(), tuple2.hashCode());
    assertNotEquals(tuple1.hashCode(), tuple3.hashCode());
  }

  @Test
  void testToString() {
    Tuple tuple = new Tuple().add("hello").add(42L).add(true);
    
    String result = tuple.toString();
    
    assertNotNull(result);
    assertTrue(result.contains("hello"));
    assertTrue(result.contains("42"));
    assertTrue(result.contains("true"));
  }

  @Test
  void testGetOutOfBounds() {
    Tuple tuple = new Tuple().add("test");
    
    assertThrows(IndexOutOfBoundsException.class, () -> tuple.get(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> tuple.get(1));
    assertThrows(IndexOutOfBoundsException.class, () -> tuple.getString(1));
  }

  @Test
  void testTypeMismatch() {
    Tuple tuple = new Tuple().add("string").add(42L);
    
    // Trying to get string as long should throw
    assertThrows(ClassCastException.class, () -> tuple.getLong(0));
    // Trying to get long as string should throw
    assertThrows(ClassCastException.class, () -> tuple.getString(1));
  }

  @Test
  void testIterable() {
    Tuple tuple = new Tuple().add("a").add("b").add("c");
    
    int count = 0;
    for (Object item : tuple) {
      assertNotNull(item);
      count++;
    }
    
    assertEquals(3, count);
  }

  @Test
  void testStream() {
    Tuple tuple = new Tuple().add("a").add("b").add("c");
    
    List<Object> streamResult = tuple.stream().collect(java.util.stream.Collectors.toList());
    
    assertEquals(tuple.getItems(), streamResult);
  }

  @Test
  void testPackedSizeConsistency() {
    Tuple tuple = new Tuple()
        .add("hello")
        .add(42L)
        .add(true)
        .add(new byte[]{1, 2, 3});
    
    int reportedSize = tuple.getPackedSize();
    byte[] packed = tuple.pack();
    
    assertEquals(reportedSize, packed.length);
  }

  @Test
  void testMemoization() {
    Tuple tuple = new Tuple().add("test");
    
    // First call should calculate
    byte[] packed1 = tuple.pack();
    
    // Second call should return cached result
    byte[] packed2 = tuple.pack();
    
    // Should be the same array reference (memoized)
    assertArrayEquals(packed1, packed2);
  }

  @Test
  void testLargeNumbers() {
    // Test with numbers at the boundaries
    Tuple tuple = new Tuple()
        .add(Long.MAX_VALUE)
        .add(Long.MIN_VALUE)
        .add(Integer.MAX_VALUE)
        .add(Integer.MIN_VALUE);
    
    assertEquals(Long.MAX_VALUE, tuple.getLong(0));
    assertEquals(Long.MIN_VALUE, tuple.getLong(1));
    assertEquals((long) Integer.MAX_VALUE, tuple.getLong(2));
    assertEquals((long) Integer.MIN_VALUE, tuple.getLong(3));
  }

  @Test
  void testSpecialFloatValues() {
    Tuple tuple = new Tuple()
        .add(Float.NaN)
        .add(Float.POSITIVE_INFINITY)
        .add(Float.NEGATIVE_INFINITY)
        .add(Double.NaN)
        .add(Double.POSITIVE_INFINITY)
        .add(Double.NEGATIVE_INFINITY);
    
    assertTrue(Float.isNaN(tuple.getFloat(0)));
    assertEquals(Float.POSITIVE_INFINITY, tuple.getFloat(1));
    assertEquals(Float.NEGATIVE_INFINITY, tuple.getFloat(2));
    assertTrue(Double.isNaN(tuple.getDouble(3)));
    assertEquals(Double.POSITIVE_INFINITY, tuple.getDouble(4));
    assertEquals(Double.NEGATIVE_INFINITY, tuple.getDouble(5));
  }

  @Test
  void testRoundTripConsistency() {
    // Test that pack/unpack preserves all supported types
    Tuple original = new Tuple()
        .add("string")
        .add(42L)
        .add(3.14f)
        .add(2.718)
        .add(true)
        .add(false)
        .add(new byte[]{1, 2, 3})
        .add(UUID.randomUUID())
        .add(new BigInteger("123456789012345678901234567890"))
        .add((String) null)
        .add(Arrays.asList("nested", "list"))
        .add(new Tuple().add("nested").add("tuple"));
    
    byte[] packed = original.pack();
    Tuple unpacked = Tuple.fromBytes(packed);
    
    assertEquals(original.size(), unpacked.size());
    
    // Check individual elements, handling special cases for byte arrays and nested structures
    for (int i = 0; i < original.size(); i++) {
      Object originalItem = original.get(i);
      Object unpackedItem = unpacked.get(i);
      
      if (originalItem instanceof byte[] && unpackedItem instanceof byte[]) {
        assertArrayEquals((byte[]) originalItem, (byte[]) unpackedItem);
      } else if (originalItem instanceof Tuple && unpackedItem instanceof List) {
        // Nested tuples are unpacked as Lists - compare their contents
        Tuple originalTuple = (Tuple) originalItem;
        @SuppressWarnings("unchecked")
        List<Object> unpackedList = (List<Object>) unpackedItem;
        assertEquals(originalTuple.getItems(), unpackedList);
      } else {
        assertEquals(originalItem, unpackedItem);
      }
    }
  }
}