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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link TupleUtil} class.
 */
class TupleUtilTest {

  // Helper method to pack items by calculating size first
  private byte[] packItems(List<Object> items) {
    int packedSize = TupleUtil.getPackedSize(items, false);
    return TupleUtil.pack(items, packedSize);
  }

  @Test
  void testGetCodeForNull() {
    assertEquals(0x00, TupleUtil.getCodeFor(null));
  }

  @Test
  void testGetCodeForByteArray() {
    assertEquals(0x01, TupleUtil.getCodeFor(new byte[]{1, 2, 3}));
  }

  @Test
  void testGetCodeForString() {
    assertEquals(0x02, TupleUtil.getCodeFor("hello"));
  }

  @Test
  void testGetCodeForNestedTuple() {
    Tuple nested = new Tuple().add("test");
    assertEquals(0x05, TupleUtil.getCodeFor(nested));
  }

  @Test
  void testGetCodeForNestedList() {
    List<Object> list = Arrays.asList("a", "b");
    assertEquals(0x05, TupleUtil.getCodeFor(list));
  }

  @Test
  void testGetCodeForNegativeNumbers() {
    // Negative numbers should have consistent type codes
    int negLongCode = TupleUtil.getCodeFor(-1L);
    int negBigIntCode = TupleUtil.getCodeFor(BigInteger.valueOf(-1000));
    
    assertTrue(negLongCode > 0);
    assertTrue(negBigIntCode > 0);
  }

  @Test
  void testGetCodeForPositiveNumbers() {
    // Positive numbers should have consistent type codes
    int posLongCode = TupleUtil.getCodeFor(1L);
    int posBigIntCode = TupleUtil.getCodeFor(BigInteger.valueOf(1000));
    
    assertTrue(posLongCode > 0);
    assertTrue(posBigIntCode > 0);
  }

  @Test
  void testGetCodeForFloat() {
    assertEquals(0x20, TupleUtil.getCodeFor(3.14f));
  }

  @Test
  void testGetCodeForDouble() {
    assertEquals(0x21, TupleUtil.getCodeFor(3.14));
  }

  @Test
  void testGetCodeForBoolean() {
    // Just verify they return consistent codes
    int falseCode = TupleUtil.getCodeFor(false);
    int trueCode = TupleUtil.getCodeFor(true);
    
    // Both booleans should map to the same type code
    assertEquals(falseCode, trueCode);
  }

  @Test
  void testGetCodeForUUID() {
    UUID uuid = UUID.randomUUID();
    assertEquals(0x30, TupleUtil.getCodeFor(uuid));
  }

  @Test
  void testGetCodeForUnsupportedType() {
    Object unsupported = new Object();
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      TupleUtil.getCodeFor(unsupported);
    });
    
    // Just verify an exception is thrown with some message
    assertNotNull(exception.getMessage());
  }

  @Test
  void testCompareItemsWithSameTypes() {
    // String comparison
    assertTrue(TupleUtil.compareItems("apple", "banana") < 0);
    assertTrue(TupleUtil.compareItems("banana", "apple") > 0);
    assertEquals(0, TupleUtil.compareItems("same", "same"));
    
    // Number comparison
    assertTrue(TupleUtil.compareItems(1L, 2L) < 0);
    assertTrue(TupleUtil.compareItems(2L, 1L) > 0);
    assertEquals(0, TupleUtil.compareItems(5L, 5L));
  }

  @Test
  void testCompareItemsWithDifferentTypes() {
    // Different types should be ordered by their type codes
    String str = "hello";
    Long num = 42L;
    
    int result = TupleUtil.compareItems(str, num);
    int expectedResult = Integer.compare(TupleUtil.getCodeFor(str), TupleUtil.getCodeFor(num));
    
    assertEquals(Integer.signum(expectedResult), Integer.signum(result));
  }

  @Test
  void testCompareItemsWithNulls() {
    assertEquals(0, TupleUtil.compareItems(null, null));
    assertTrue(TupleUtil.compareItems(null, "string") < 0);
    assertTrue(TupleUtil.compareItems("string", null) > 0);
  }

  @Test
  void testCompareItemsWithSameReference() {
    String str = "test";
    assertEquals(0, TupleUtil.compareItems(str, str));
  }

  @Test
  void testUnpackEmptyArray() {
    List<Object> result = TupleUtil.unpack(new byte[0]);
    assertEquals(Collections.emptyList(), result);
  }

  @Test
  void testUnpackAndPack() {
    // Create a list of items
    List<Object> original = Arrays.asList(
        "hello",
        42L,
        true,
        new byte[]{1, 2, 3},
        3.14f
    );
    
    // Pack the items
    byte[] packed = packItems(original);
    
    // Unpack and verify
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(original.size(), unpacked.size());
    assertEquals(original.get(0), unpacked.get(0)); // String
    assertEquals(original.get(1), unpacked.get(1)); // Long
    assertEquals(original.get(2), unpacked.get(2)); // Boolean
    assertArrayEquals((byte[]) original.get(3), (byte[]) unpacked.get(3)); // byte array
    assertEquals(original.get(4), unpacked.get(4)); // Float
  }

  @Test
  void testPackWithByteBuffer() {
    List<Object> items = Arrays.asList("test", 123L, true);
    
    int packedSize = TupleUtil.getPackedSize(items, false);
    ByteBuffer buffer = ByteBuffer.allocate(packedSize);
    
    TupleUtil.pack(buffer, items);
    
    byte[] result = buffer.array();
    byte[] expected = TupleUtil.pack(items, packedSize);
    
    assertArrayEquals(expected, result);
  }

  @Test
  void testGetPackedSizeConsistency() {
    List<Object> items = Arrays.asList(
        "hello",
        42L,
        true,
        new byte[]{1, 2, 3, 4, 5},
        3.14159
    );
    
    int reportedSize = TupleUtil.getPackedSize(items, false);
    byte[] packed = TupleUtil.pack(items, reportedSize);
    
    assertEquals(reportedSize, packed.length);
  }

  @Test
  void testPackedSizeWithNested() {
    List<Object> nestedList = Arrays.asList("nested", 1L);
    List<Object> items = Arrays.asList("parent", nestedList);
    
    int sizeNotNested = TupleUtil.getPackedSize(items, false);
    int sizeNested = TupleUtil.getPackedSize(items, true);
    
    // Nested size should account for extra terminator bytes
    assertTrue(sizeNested >= sizeNotNested);
  }

  @Test
  void testEncodeDecodeStringWithSpecialCharacters() {
    List<Object> items = Arrays.asList(
        "regular string",
        "string with null \0 character",
        "unicode: café ñoño 中文",
        "emoji: 😀🎵"
    );
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items, unpacked);
  }

  @Test
  void testEncodeDecodeBigIntegers() {
    List<Object> items = Arrays.asList(
        new BigInteger("0"),
        new BigInteger("123456789012345678901234567890"),
        new BigInteger("-987654321098765432109876543210"),
        BigInteger.valueOf(Long.MAX_VALUE),
        BigInteger.valueOf(Long.MIN_VALUE)
    );
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items.size(), unpacked.size());
    
    // BigInteger(0) may be converted to Long(0) during encoding/decoding
    Object unpacked0 = unpacked.get(0);
    assertTrue(unpacked0.equals(0L) || unpacked0.equals(BigInteger.ZERO));
    
    // Large BigIntegers should remain as BigIntegers
    assertEquals(items.get(1), unpacked.get(1));
    assertEquals(items.get(2), unpacked.get(2));
    
    // BigIntegers that fit in Long range may be converted to Long
    Object unpacked3 = unpacked.get(3);
    assertTrue(unpacked3.equals(Long.MAX_VALUE) || unpacked3.equals(BigInteger.valueOf(Long.MAX_VALUE)));
    
    Object unpacked4 = unpacked.get(4);
    assertTrue(unpacked4.equals(Long.MIN_VALUE) || unpacked4.equals(BigInteger.valueOf(Long.MIN_VALUE)));
  }

  @Test
  void testEncodeDecodeFloatSpecialValues() {
    List<Object> items = Arrays.asList(
        Float.NaN,
        Float.POSITIVE_INFINITY,
        Float.NEGATIVE_INFINITY,
        0.0f,
        -0.0f,
        Float.MAX_VALUE,
        Float.MIN_VALUE
    );
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items.size(), unpacked.size());
    
    for (int i = 0; i < items.size(); i++) {
      Float original = (Float) items.get(i);
      Float unpackedVal = (Float) unpacked.get(i);
      
      if (Float.isNaN(original)) {
        assertTrue(Float.isNaN(unpackedVal));
      } else {
        assertEquals(original, unpackedVal);
      }
    }
  }

  @Test
  void testEncodeDecodeDoubleSpecialValues() {
    List<Object> items = Arrays.asList(
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY,
        0.0,
        -0.0,
        Double.MAX_VALUE,
        Double.MIN_VALUE
    );
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items.size(), unpacked.size());
    
    for (int i = 0; i < items.size(); i++) {
      Double original = (Double) items.get(i);
      Double unpackedVal = (Double) unpacked.get(i);
      
      if (Double.isNaN(original)) {
        assertTrue(Double.isNaN(unpackedVal));
      } else {
        assertEquals(original, unpackedVal);
      }
    }
  }

  @Test
  void testEncodeDecodeUUIDs() {
    List<Object> items = Arrays.asList(
        UUID.randomUUID(),
        new UUID(0L, 0L),
        new UUID(Long.MAX_VALUE, Long.MAX_VALUE),
        new UUID(Long.MIN_VALUE, Long.MIN_VALUE)
    );
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items, unpacked);
  }

  @Test
  void testEncodeDecodeNestedStructures() {
    Tuple nestedTuple = new Tuple().add("nested").add(42L);
    List<Object> nestedList = Arrays.asList("list", "items", 123L);
    
    List<Object> items = Arrays.asList(
        "parent",
        nestedTuple,
        nestedList,
        "after nesting"
    );
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items.size(), unpacked.size());
    assertEquals(items.get(0), unpacked.get(0)); // String
    
    // Nested tuple comes back as a List when unpacked via TupleUtil
    @SuppressWarnings("unchecked")
    List<Object> unpackedTupleAsList = (List<Object>) unpacked.get(1);
    assertEquals(nestedTuple.getItems(), unpackedTupleAsList);
    
    // Nested list
    @SuppressWarnings("unchecked")
    List<Object> unpackedList = (List<Object>) unpacked.get(2);
    assertEquals(nestedList, unpackedList);
    
    assertEquals(items.get(3), unpacked.get(3)); // String after
  }

  @Test
  void testEncodeDecodeNulls() {
    List<Object> items = Arrays.asList(
        null,
        "not null",
        null,
        42L,
        null
    );
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items, unpacked);
  }

  @Test
  void testEncodeDecodeEmptyByteArray() {
    List<Object> items = Arrays.asList(
        new byte[0],
        new byte[]{1, 2, 3},
        new byte[0]
    );
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items.size(), unpacked.size());
    assertArrayEquals((byte[]) items.get(0), (byte[]) unpacked.get(0));
    assertArrayEquals((byte[]) items.get(1), (byte[]) unpacked.get(1));
    assertArrayEquals((byte[]) items.get(2), (byte[]) unpacked.get(2));
  }

  @Test
  void testCompareItemsTransitivity() {
    String a = "apple";
    String b = "banana";
    String c = "cherry";
    
    assertTrue(TupleUtil.compareItems(a, b) < 0);
    assertTrue(TupleUtil.compareItems(b, c) < 0);
    assertTrue(TupleUtil.compareItems(a, c) < 0);
  }

  @Test
  void testCompareItemsReflexivity() {
    String item = "test";
    assertEquals(0, TupleUtil.compareItems(item, item));
  }

  @Test
  void testCompareItemsSymmetry() {
    String item1 = "apple";
    String item2 = "banana";
    
    int result12 = TupleUtil.compareItems(item1, item2);
    int result21 = TupleUtil.compareItems(item2, item1);
    
    assertEquals(-Integer.signum(result12), Integer.signum(result21));
  }

  @Test
  void testLargeData() {
    // Test with larger datasets to ensure performance is reasonable
    List<Object> items = Arrays.asList(
        "large string test with many characters and unicode: café ñoño 中文 😀",
        new byte[1000], // Large byte array
        new BigInteger("123456789012345678901234567890123456789012345678901234567890"),
        Arrays.asList("nested", "list", "with", "many", "items", 1L, 2L, 3L, 4L, 5L)
    );
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items.size(), unpacked.size());
    assertNotNull(unpacked);
  }

  @Test
  void testPackWithLargerExpectedSize() {
    List<Object> items = Arrays.asList("test", 42L);
    
    int correctSize = TupleUtil.getPackedSize(items, false);
    
    // Providing larger expected size should work (buffer will have extra space)
    byte[] packed1 = TupleUtil.pack(items, correctSize + 10); // Too large
    byte[] packed2 = packItems(items);  // Correct size
    
    // Results should be the same when trimmed to actual content
    byte[] trimmed1 = new byte[correctSize];
    System.arraycopy(packed1, 0, trimmed1, 0, correctSize);
    
    assertArrayEquals(trimmed1, packed2);
  }
  
  @Test
  void testPackWithTooSmallExpectedSize() {
    List<Object> items = Arrays.asList("test", 42L);
    
    int correctSize = TupleUtil.getPackedSize(items, false);
    
    // Providing too small expected size should throw BufferOverflowException
    assertThrows(Exception.class, () -> {
      TupleUtil.pack(items, correctSize - 1);
    });
  }

  @Test
  void testCornerCaseNumbers() {
    List<Object> items = Arrays.asList(
        0L,
        1L,
        -1L,
        Long.MAX_VALUE,
        Long.MIN_VALUE,
        (long) Integer.MAX_VALUE,
        (long) Integer.MIN_VALUE,
        255L, // Edge case for byte encoding
        256L, // Just above byte range
        65535L, // Edge case for short encoding
        65536L  // Just above short range
    );
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items, unpacked);
  }

  @Test
  void testBooleanValues() {
    List<Object> items = Arrays.asList(true, false, true, false);
    
    byte[] packed = packItems(items);
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(items, unpacked);
  }

  @Test
  void testMixedNumberTypes() {
    // Test that different number types with same values compare correctly
    Long longVal = 42L;
    BigInteger bigIntVal = BigInteger.valueOf(42);
    
    // Should have same type code
    assertEquals(TupleUtil.getCodeFor(longVal), TupleUtil.getCodeFor(bigIntVal));
    
    // Should compare as equal
    assertEquals(0, TupleUtil.compareItems(longVal, bigIntVal));
    assertEquals(0, TupleUtil.compareItems(bigIntVal, longVal));
  }

  @Test
  void testUnpackFromByteBuffer() {
    // Test unpacking from ByteBuffer
    byte[] packed = Tuple.packItems("hello", 42L, true);
    ByteBuffer buffer = ByteBuffer.wrap(packed);
    
    List<Object> unpacked = TupleUtil.unpack(buffer);
    
    assertEquals(3, unpacked.size());
    assertEquals("hello", unpacked.get(0));
    assertEquals(42L, unpacked.get(1));
    assertEquals(true, unpacked.get(2));
    
    // Buffer position should be advanced
    assertEquals(packed.length, buffer.position());
  }

  @Test
  void testUnpackFromByteBufferWithRemainingData() {
    // Test unpacking from ByteBuffer with concatenated tuple data
    // When two separately packed tuples are concatenated, unpack sees them as separate items
    byte[] packed1 = Tuple.packItems("first");
    byte[] packed2 = Tuple.packItems("second");
    byte[] combined = new byte[packed1.length + packed2.length];
    System.arraycopy(packed1, 0, combined, 0, packed1.length);
    System.arraycopy(packed2, 0, combined, packed1.length, packed2.length);
    
    ByteBuffer buffer = ByteBuffer.wrap(combined);
    
    // Unpack all items from buffer (should see both items since they're concatenated)
    List<Object> unpacked = TupleUtil.unpack(buffer);
    assertEquals(2, unpacked.size());
    assertEquals("first", unpacked.get(0));
    assertEquals("second", unpacked.get(1));
    assertEquals(combined.length, buffer.position()); // Should consume entire buffer
  }

  @Test
  void testUnpackSingleItem() {
    // Test unpacking single item from ByteBuffer
    byte[] packed = Tuple.packItems("hello", 42L, true);
    ByteBuffer buffer = ByteBuffer.wrap(packed);
    
    Object item = TupleUtil.unpackSingleItem(buffer);
    
    assertEquals("hello", item);
    
    // Buffer position should be advanced past the first item only
    assertTrue(buffer.position() > 0);
    assertTrue(buffer.position() < packed.length);
    
    // Should be able to read the next item
    Object secondItem = TupleUtil.unpackSingleItem(buffer);
    assertEquals(42L, secondItem);
  }

  @Test
  void testUnpackSingleItemMultiple() {
    // Test reading multiple single items sequentially
    byte[] packed = Tuple.packItems("first", "second", "third");
    ByteBuffer buffer = ByteBuffer.wrap(packed);
    
    String first = (String) TupleUtil.unpackSingleItem(buffer);
    String second = (String) TupleUtil.unpackSingleItem(buffer);
    String third = (String) TupleUtil.unpackSingleItem(buffer);
    
    assertEquals("first", first);
    assertEquals("second", second);
    assertEquals("third", third);
    assertEquals(packed.length, buffer.position()); // Should have consumed entire buffer
  }

  @Test
  void testUnpackSingleItemEmpty() {
    // Test unpacking from empty buffer
    ByteBuffer buffer = ByteBuffer.wrap(new byte[0]);
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      TupleUtil.unpackSingleItem(buffer);
    });
    
    assertEquals("No data to read from buffer", exception.getMessage());
  }

  @Test
  void testUnpackSingleItemComplexTypes() {
    // Test with complex types
    byte[] originalBytes = {1, 2, 3, 4, 5};
    byte[] packed = Tuple.packItems(originalBytes, "string", 42L);
    ByteBuffer buffer = ByteBuffer.wrap(packed);
    
    byte[] readBytes = (byte[]) TupleUtil.unpackSingleItem(buffer);
    String readString = (String) TupleUtil.unpackSingleItem(buffer);
    Long readLong = (Long) TupleUtil.unpackSingleItem(buffer);
    
    assertArrayEquals(originalBytes, readBytes);
    assertEquals("string", readString);
    assertEquals(42L, readLong);
    assertEquals(packed.length, buffer.position());
  }

  @Test
  void testUnpackSingleItemNullValue() {
    // Test with null values
    byte[] packed = Tuple.packItems((String) null, "hello");
    ByteBuffer buffer = ByteBuffer.wrap(packed);
    
    Object nullValue = TupleUtil.unpackSingleItem(buffer);
    String stringValue = (String) TupleUtil.unpackSingleItem(buffer);
    
    assertEquals(null, nullValue);
    assertEquals("hello", stringValue);
  }

  @Test
  void testUnpackFromByteArrayStillWorks() {
    // Test that the original byte array method still works (regression test)
    byte[] packed = Tuple.packItems("hello", 42L, true);
    
    List<Object> unpacked = TupleUtil.unpack(packed);
    
    assertEquals(3, unpacked.size());
    assertEquals("hello", unpacked.get(0));
    assertEquals(42L, unpacked.get(1));
    assertEquals(true, unpacked.get(2));
  }
}