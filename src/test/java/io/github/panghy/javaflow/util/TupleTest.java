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

  @Test
  void testStaticPackEmpty() {
    // Test packing zero elements
    byte[] packed = Tuple.packItems();
    
    assertArrayEquals(new byte[0], packed);
    assertEquals(0, Tuple.getItemsPackedSize());
  }

  @Test
  void testStaticPackSingleElement() {
    // Test packing a single element
    byte[] packed = Tuple.packItems("hello");
    byte[] expected = new Tuple().add("hello").pack();
    
    assertArrayEquals(expected, packed);
  }

  @Test
  void testStaticPackMultipleElements() {
    // Test packing multiple elements
    byte[] packed = Tuple.packItems("hello", 42L, true, new byte[]{1, 2, 3});
    byte[] expected = new Tuple()
        .add("hello")
        .add(42L)
        .add(true)
        .add(new byte[]{1, 2, 3})
        .pack();
    
    assertArrayEquals(expected, packed);
  }

  @Test
  void testStaticPackIntoBuffer() {
    ByteBuffer buffer = ByteBuffer.allocate(1024);
    int initialPosition = buffer.position();
    
    // Pack into buffer
    Tuple.packItemsInto(buffer, "hello", 42L, true);
    
    // Check that position advanced
    assertTrue(buffer.position() > initialPosition);
    
    // Verify packed data
    buffer.flip();
    byte[] result = new byte[buffer.remaining()];
    buffer.get(result);
    
    byte[] expected = Tuple.packItems("hello", 42L, true);
    assertArrayEquals(expected, result);
  }

  @Test
  void testStaticPackIntoEmptyBuffer() {
    ByteBuffer buffer = ByteBuffer.allocate(10);
    int initialPosition = buffer.position();
    
    // Pack nothing into buffer
    Tuple.packItemsInto(buffer);
    
    // Position should not change
    assertEquals(initialPosition, buffer.position());
  }

  @Test
  void testStaticGetPackedSize() {
    // Test size calculation
    int size = Tuple.getItemsPackedSize("hello", 42L, true, new byte[]{1, 2, 3});
    int expectedSize = new Tuple()
        .add("hello")
        .add(42L)
        .add(true)
        .add(new byte[]{1, 2, 3})
        .getPackedSize();
    
    assertEquals(expectedSize, size);
  }

  @Test
  void testStaticPackConsistency() {
    // Test that static pack produces same result as instance pack
    Object[] items = {"string", 42L, 3.14, true, new byte[]{1, 2, 3}, UUID.randomUUID()};
    
    // Static pack
    byte[] staticPacked = Tuple.packItems(items);
    
    // Instance pack
    Tuple tuple = new Tuple();
    for (Object item : items) {
      tuple = tuple.addObject(item);
    }
    byte[] instancePacked = tuple.pack();
    
    assertArrayEquals(instancePacked, staticPacked);
  }

  @Test
  void testStaticPackWithNulls() {
    // Test that static pack handles nulls correctly
    byte[] packed = Tuple.packItems("hello", null, "world");
    byte[] expected = new Tuple()
        .add("hello")
        .add((String) null)
        .add("world")
        .pack();
    
    assertArrayEquals(expected, packed);
  }

  @Test
  void testStaticPackComplexTypes() {
    // Test with complex types
    UUID uuid = UUID.randomUUID();
    BigInteger bigInt = new BigInteger("123456789012345678901234567890");
    List<Object> list = Arrays.asList("nested", "list");
    Tuple nestedTuple = new Tuple().add("nested").add("tuple");
    
    byte[] packed = Tuple.packItems("string", 42L, 3.14f, 2.718, true, new byte[]{1, 2, 3}, 
                                    uuid, bigInt, list, nestedTuple);
    
    byte[] expected = new Tuple()
        .add("string")
        .add(42L)
        .add(3.14f)
        .add(2.718)
        .add(true)
        .add(new byte[]{1, 2, 3})
        .add(uuid)
        .add(bigInt)
        .add(list)
        .add(nestedTuple)
        .pack();
    
    assertArrayEquals(expected, packed);
  }

  @Test
  void testStaticPackRoundTrip() {
    // Test that static pack can be unpacked correctly
    Object[] items = {"hello", 42L, true, new byte[]{1, 2, 3}};
    
    byte[] packed = Tuple.packItems(items);
    Tuple unpacked = Tuple.fromBytes(packed);
    
    assertEquals(items.length, unpacked.size());
    assertEquals("hello", unpacked.getString(0));
    assertEquals(42L, unpacked.getLong(1));
    assertTrue(unpacked.getBoolean(2));
    assertArrayEquals(new byte[]{1, 2, 3}, unpacked.getBytes(3));
  }

  @Test
  void testReadString() {
    // Test reading a string value
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems("hello"));
    String value = Tuple.readString(buffer);
    
    assertEquals("hello", value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadStringNull() {
    // Test reading a null string value
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((String) null));
    String value = Tuple.readString(buffer);
    
    assertNull(value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadLong() {
    // Test reading a long value
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(42L));
    long value = Tuple.readLong(buffer);
    
    assertEquals(42L, value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadBoolean() {
    // Test reading boolean values
    ByteBuffer trueBuffer = ByteBuffer.wrap(Tuple.packItems(true));
    boolean trueValue = Tuple.readBoolean(trueBuffer);
    
    assertTrue(trueValue);
    assertEquals(0, trueBuffer.remaining());

    ByteBuffer falseBuffer = ByteBuffer.wrap(Tuple.packItems(false));
    boolean falseValue = Tuple.readBoolean(falseBuffer);
    
    assertFalse(falseValue);
    assertEquals(0, falseBuffer.remaining());
  }

  @Test
  void testReadBytes() {
    // Test reading a byte array
    byte[] original = {1, 2, 3, 4, 5};
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(original));
    byte[] value = Tuple.readBytes(buffer);
    
    assertArrayEquals(original, value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadBytesNull() {
    // Test reading a null byte array
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((byte[]) null));
    byte[] value = Tuple.readBytes(buffer);
    
    assertNull(value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadDouble() {
    // Test reading a double value
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14159));
    double value = Tuple.readDouble(buffer);
    
    assertEquals(3.14159, value, 0.00001);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadFloat() {
    // Test reading a float value
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14f));
    float value = Tuple.readFloat(buffer);
    
    assertEquals(3.14f, value, 0.001f);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadUUID() {
    // Test reading a UUID value
    UUID original = UUID.randomUUID();
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(original));
    UUID value = Tuple.readUUID(buffer);
    
    assertEquals(original, value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadUUIDNull() {
    // Test reading a null UUID
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((UUID) null));
    UUID value = Tuple.readUUID(buffer);
    
    assertNull(value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadMultipleValues() {
    // Test reading multiple values from the same buffer
    byte[] packed = Tuple.packItems("hello", 42L, true, new byte[]{1, 2, 3});
    ByteBuffer buffer = ByteBuffer.wrap(packed);
    
    String str = Tuple.readString(buffer);
    long num = Tuple.readLong(buffer);
    boolean bool = Tuple.readBoolean(buffer);
    byte[] bytes = Tuple.readBytes(buffer);
    
    assertEquals("hello", str);
    assertEquals(42L, num);
    assertTrue(bool);
    assertArrayEquals(new byte[]{1, 2, 3}, bytes);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadWrongType() {
    // Test reading wrong type throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems("hello"));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readLong(buffer);
    });
    
    assertTrue(exception.getMessage().contains("Expected number but found: String"));
  }

  @Test
  void testReadLongFromFloat() {
    // Test reading long from float throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14f));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readLong(buffer);
    });
    
    assertTrue(exception.getMessage().contains("Expected integral number but found floating-point: Float"));
  }

  @Test
  void testReadLongFromDouble() {
    // Test reading long from double throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readLong(buffer);
    });
    
    assertTrue(exception.getMessage().contains("Expected integral number but found floating-point: Double"));
  }

  @Test
  void testReadDoubleFromFloat() {
    // Test reading double from float works (float can be read as double)
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14f));
    double value = Tuple.readDouble(buffer);
    
    assertEquals(3.14f, value, 0.001);
  }

  @Test
  void testReadDoubleFromLong() {
    // Test reading double from long throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(42L));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readDouble(buffer);
    });
    
    assertTrue(exception.getMessage().contains("Expected floating-point number but found: Long"));
  }

  @Test
  void testReadFloatFromDouble() {
    // Test reading float from double throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readFloat(buffer);
    });
    
    assertTrue(exception.getMessage().contains("Expected float but found: Double"));
  }

  @Test
  void testReadFloatFromLong() {
    // Test reading float from long throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(42L));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readFloat(buffer);
    });
    
    assertTrue(exception.getMessage().contains("Expected float but found: Long"));
  }

  @Test
  void testReadInt() {
    // Test reading int from valid range
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(42L));
    int value = Tuple.readInt(buffer);
    
    assertEquals(42, value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadIntFromMaxValue() {
    // Test reading int from Integer.MAX_VALUE
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((long) Integer.MAX_VALUE));
    int value = Tuple.readInt(buffer);
    
    assertEquals(Integer.MAX_VALUE, value);
  }

  @Test
  void testReadIntFromMinValue() {
    // Test reading int from Integer.MIN_VALUE
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((long) Integer.MIN_VALUE));
    int value = Tuple.readInt(buffer);
    
    assertEquals(Integer.MIN_VALUE, value);
  }

  @Test
  void testReadIntOverflow() {
    // Test reading int from value too large
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(Integer.MAX_VALUE + 1L));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readInt(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in an int"));
    assertTrue(exception.getMessage().contains("2147483648"));
  }

  @Test
  void testReadIntUnderflow() {
    // Test reading int from value too small
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(Integer.MIN_VALUE - 1L));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readInt(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in an int"));
    assertTrue(exception.getMessage().contains("-2147483649"));
  }

  @Test
  void testReadIntFromFloat() {
    // Test reading int from float throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14f));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readInt(buffer);
    });
    
    assertTrue(exception.getMessage().contains("Expected integral number but found floating-point: Float"));
  }

  @Test
  void testReadShort() {
    // Test reading short from valid range
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(42L));
    short value = Tuple.readShort(buffer);
    
    assertEquals(42, value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadShortFromMaxValue() {
    // Test reading short from Short.MAX_VALUE
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((long) Short.MAX_VALUE));
    short value = Tuple.readShort(buffer);
    
    assertEquals(Short.MAX_VALUE, value);
  }

  @Test
  void testReadShortFromMinValue() {
    // Test reading short from Short.MIN_VALUE
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((long) Short.MIN_VALUE));
    short value = Tuple.readShort(buffer);
    
    assertEquals(Short.MIN_VALUE, value);
  }

  @Test
  void testReadShortOverflow() {
    // Test reading short from value too large
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(Short.MAX_VALUE + 1L));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readShort(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in a short"));
    assertTrue(exception.getMessage().contains("32768"));
  }

  @Test
  void testReadShortUnderflow() {
    // Test reading short from value too small
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(Short.MIN_VALUE - 1L));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readShort(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in a short"));
    assertTrue(exception.getMessage().contains("-32769"));
  }

  @Test
  void testReadShortFromFloat() {
    // Test reading short from float throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14f));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readShort(buffer);
    });
    
    assertTrue(exception.getMessage().contains("Expected integral number but found floating-point: Float"));
  }

  @Test
  void testReadByte() {
    // Test reading byte from valid range
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(42L));
    byte value = Tuple.readByte(buffer);
    
    assertEquals(42, value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadByteFromMaxValue() {
    // Test reading byte from Byte.MAX_VALUE
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((long) Byte.MAX_VALUE));
    byte value = Tuple.readByte(buffer);
    
    assertEquals(Byte.MAX_VALUE, value);
  }

  @Test
  void testReadByteFromMinValue() {
    // Test reading byte from Byte.MIN_VALUE
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((long) Byte.MIN_VALUE));
    byte value = Tuple.readByte(buffer);
    
    assertEquals(Byte.MIN_VALUE, value);
  }

  @Test
  void testReadByteOverflow() {
    // Test reading byte from value too large
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(Byte.MAX_VALUE + 1L));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readByte(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in a byte"));
    assertTrue(exception.getMessage().contains("128"));
  }

  @Test
  void testReadByteUnderflow() {
    // Test reading byte from value too small
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(Byte.MIN_VALUE - 1L));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readByte(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in a byte"));
    assertTrue(exception.getMessage().contains("-129"));
  }

  @Test
  void testReadByteFromFloat() {
    // Test reading byte from float throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14f));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readByte(buffer);
    });
    
    assertTrue(exception.getMessage().contains("Expected integral number but found floating-point: Float"));
  }

  @Test
  void testReadByteFromBigInteger() {
    // Test reading byte from BigInteger that fits in byte range
    java.math.BigInteger bigInt = java.math.BigInteger.valueOf(100);
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(bigInt));
    byte value = Tuple.readByte(buffer);
    
    assertEquals(100, value);
  }

  @Test
  void testReadByteFromBigIntegerOverflow() {
    // Test reading byte from BigInteger that doesn't fit
    java.math.BigInteger bigInt = java.math.BigInteger.valueOf(Byte.MAX_VALUE + 1);
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(bigInt));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readByte(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in a byte"));
  }

  @Test
  void testReadIntShortByteSequential() {
    // Test reading multiple values sequentially
    byte[] packed = Tuple.packItems(100L, 200L, 50L, -50L);
    ByteBuffer buffer = ByteBuffer.wrap(packed);
    
    int first = Tuple.readInt(buffer);
    short second = Tuple.readShort(buffer);
    byte third = Tuple.readByte(buffer);
    byte fourth = Tuple.readByte(buffer);
    
    assertEquals(100, first);
    assertEquals(200, second);
    assertEquals(50, third);
    assertEquals(-50, fourth);
    assertEquals(0, buffer.remaining()); // Should have consumed entire buffer
  }

  @Test
  void testReadIntFromBigInteger() {
    // Test reading int from BigInteger that fits in int range
    java.math.BigInteger bigInt = java.math.BigInteger.valueOf(1000);
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(bigInt));
    int value = Tuple.readInt(buffer);
    
    assertEquals(1000, value);
  }

  @Test
  void testReadShortFromBigInteger() {
    // Test reading short from BigInteger that fits in short range
    java.math.BigInteger bigInt = java.math.BigInteger.valueOf(1000);
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(bigInt));
    short value = Tuple.readShort(buffer);
    
    assertEquals(1000, value);
  }

  @Test
  void testReadIntFromBigIntegerOverflow() {
    // Test reading int from BigInteger that doesn't fit
    java.math.BigInteger bigInt = java.math.BigInteger.valueOf(Integer.MAX_VALUE).add(java.math.BigInteger.ONE);
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(bigInt));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readInt(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in an int"));
  }

  @Test
  void testReadNullNumber() {
    // Test reading null number throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((String) null));
    
    NullPointerException exception = assertThrows(NullPointerException.class, () -> {
      Tuple.readLong(buffer);
    });
    
    assertEquals("Number types in Tuples may not be null", exception.getMessage());
  }

  @Test
  void testReadEmptyBuffer() {
    // Test reading from empty buffer throws exception
    ByteBuffer buffer = ByteBuffer.wrap(new byte[0]);
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readString(buffer);
    });
    
    assertEquals("No data to read from buffer", exception.getMessage());
  }

  @Test
  void testReadBufferPositioning() {
    // Test that buffer position is correctly advanced
    byte[] packed = Tuple.packItems("first", "second", "third");
    ByteBuffer buffer = ByteBuffer.wrap(packed);
    
    int initialPosition = buffer.position();
    String first = Tuple.readString(buffer);
    int afterFirstPosition = buffer.position();
    String second = Tuple.readString(buffer);
    int afterSecondPosition = buffer.position();
    String third = Tuple.readString(buffer);
    int finalPosition = buffer.position();
    
    assertEquals("first", first);
    assertEquals("second", second);
    assertEquals("third", third);
    
    // Each read should advance the position
    assertTrue(afterFirstPosition > initialPosition);
    assertTrue(afterSecondPosition > afterFirstPosition);
    assertTrue(finalPosition > afterSecondPosition);
    assertEquals(packed.length, finalPosition); // Should have consumed entire buffer
  }

  @Test
  void testAddChar() {
    Tuple tuple = new Tuple()
        .add('A')
        .add('z')
        .add('\u0000')
        .add('\uFFFF');
    
    assertEquals(4, tuple.size());
    assertEquals(65L, tuple.getLong(0)); // 'A' = 65
    assertEquals(122L, tuple.getLong(1)); // 'z' = 122
    assertEquals(0L, tuple.getLong(2)); // '\u0000' = 0
    assertEquals(65535L, tuple.getLong(3)); // '\uFFFF' = 65535
  }

  @Test
  void testAddObjectWithCharacter() {
    Tuple tuple = new Tuple()
        .addObject('X')
        .addObject(Character.valueOf('Y'));
    
    assertEquals(2, tuple.size());
    assertEquals(88L, tuple.getLong(0)); // 'X' = 88
    assertEquals(89L, tuple.getLong(1)); // 'Y' = 89
  }

  @Test
  void testReadChar() {
    // Test reading char from valid range
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(65L)); // 'A'
    char value = Tuple.readChar(buffer);
    
    assertEquals('A', value);
    assertEquals(0, buffer.remaining()); // Buffer should be fully consumed
  }

  @Test
  void testReadCharSpecialValues() {
    // Test reading special char values
    ByteBuffer buffer1 = ByteBuffer.wrap(Tuple.packItems(0L));
    char value1 = Tuple.readChar(buffer1);
    assertEquals('\u0000', value1);

    ByteBuffer buffer2 = ByteBuffer.wrap(Tuple.packItems(65535L));
    char value2 = Tuple.readChar(buffer2);
    assertEquals('\uFFFF', value2);
    
    ByteBuffer buffer3 = ByteBuffer.wrap(Tuple.packItems(32L));
    char value3 = Tuple.readChar(buffer3);
    assertEquals(' ', value3);
  }

  @Test
  void testReadCharFromMaxValue() {
    // Test reading char from Character.MAX_VALUE
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((long) Character.MAX_VALUE));
    char value = Tuple.readChar(buffer);
    
    assertEquals(Character.MAX_VALUE, value);
  }

  @Test
  void testReadCharFromMinValue() {
    // Test reading char from Character.MIN_VALUE
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems((long) Character.MIN_VALUE));
    char value = Tuple.readChar(buffer);
    
    assertEquals(Character.MIN_VALUE, value);
  }

  @Test
  void testReadCharOverflow() {
    // Test reading char from value too large
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(Character.MAX_VALUE + 1L));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readChar(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in a char"));
    assertTrue(exception.getMessage().contains("65536"));
  }

  @Test
  void testReadCharNegative() {
    // Test reading char from negative value
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(-1L));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readChar(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in a char"));
    assertTrue(exception.getMessage().contains("-1"));
  }

  @Test
  void testReadCharFromFloat() {
    // Test reading char from float throws exception
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14f));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readChar(buffer);
    });
    
    assertTrue(exception.getMessage().contains("Expected integral number but found floating-point: Float"));
  }

  @Test
  void testReadCharFromBigInteger() {
    // Test reading char from BigInteger that fits in char range
    java.math.BigInteger bigInt = java.math.BigInteger.valueOf(1000);
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(bigInt));
    char value = Tuple.readChar(buffer);
    
    assertEquals((char) 1000, value);
  }

  @Test
  void testReadCharFromBigIntegerOverflow() {
    // Test reading char from BigInteger that doesn't fit
    java.math.BigInteger bigInt = java.math.BigInteger.valueOf(Character.MAX_VALUE + 1);
    ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(bigInt));
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Tuple.readChar(buffer);
    });
    
    assertTrue(exception.getMessage().contains("cannot fit in a char"));
  }

  @Test
  void testCharRoundTrip() {
    // Test that pack/unpack preserves char values
    Tuple original = new Tuple()
        .add('A')
        .add('z')
        .add('0')
        .add('9')
        .add(' ')
        .add('\n')
        .add('\t')
        .add('\u00E9') // é
        .add('\u4E2D') // 中
        .add('\uFFFF');
    
    byte[] packed = original.pack();
    Tuple unpacked = Tuple.fromBytes(packed);
    
    assertEquals(10, unpacked.size());
    
    // All chars are stored as longs
    assertEquals(65L, unpacked.getLong(0)); // 'A'
    assertEquals(122L, unpacked.getLong(1)); // 'z'
    assertEquals(48L, unpacked.getLong(2)); // '0'
    assertEquals(57L, unpacked.getLong(3)); // '9'
    assertEquals(32L, unpacked.getLong(4)); // ' '
    assertEquals(10L, unpacked.getLong(5)); // '\n'
    assertEquals(9L, unpacked.getLong(6)); // '\t'
    assertEquals(233L, unpacked.getLong(7)); // 'é'
    assertEquals(20013L, unpacked.getLong(8)); // '中'
    assertEquals(65535L, unpacked.getLong(9)); // '\uFFFF'
  }

  @Test
  void testCharInStaticPack() {
    // Test packing chars using static method
    byte[] packed = Tuple.packItems('H', 'i', '!');
    Tuple unpacked = Tuple.fromBytes(packed);
    
    assertEquals(3, unpacked.size());
    assertEquals(72L, unpacked.getLong(0)); // 'H'
    assertEquals(105L, unpacked.getLong(1)); // 'i'
    assertEquals(33L, unpacked.getLong(2)); // '!'
  }
}