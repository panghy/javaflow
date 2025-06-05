package io.github.panghy.javaflow.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional tests for {@link Tuple} to improve branch coverage.
 */
class TupleBranchCoverageTest {

  @Test
  void testAddObjectWithNumber() {
    // Test addObject with various Number types to cover the Number branch
    Tuple t = new Tuple();

    // Test Integer
    Tuple t1 = t.addObject(42);
    assertEquals(42L, t1.getLong(0));

    // Test Short
    Tuple t2 = t.addObject((short) 100);
    assertEquals(100L, t2.getLong(0));

    // Test Byte
    Tuple t3 = t.addObject((byte) 10);
    assertEquals(10L, t3.getLong(0));

    // Test Float
    Tuple t4 = t.addObject(3.14f);
    assertEquals(3.14f, t4.getFloat(0));

    // Test Double
    Tuple t5 = t.addObject(2.718);
    assertEquals(2.718, t5.getDouble(0));

    // Test BigInteger
    BigInteger big = new BigInteger("999999999999999999999");
    Tuple t6 = t.addObject(big);
    assertEquals(big, t6.getBigInteger(0));
  }

  @Test
  void testGetNestedListFromTuple() {
    // Test getNestedList when the element is a Tuple
    Tuple inner = Tuple.from("a", "b", "c");
    Tuple outer = new Tuple().add(inner);

    List<Object> list = outer.getNestedList(0);
    assertEquals(3, list.size());
    assertEquals("a", list.get(0));
    assertEquals("b", list.get(1));
    assertEquals("c", list.get(2));
  }

  @Test
  void testGetNestedTupleFromList() {
    // Test getNestedTuple when the element is a List
    List<Object> list = Arrays.asList("x", "y", "z");
    Tuple t = new Tuple().add(list);

    Tuple nested = t.getNestedTuple(0);
    assertEquals(3, nested.size());
    assertEquals("x", nested.getString(0));
    assertEquals("y", nested.getString(1));
    assertEquals("z", nested.getString(2));
  }

  @Test
  void testGetNestedListInvalidType() {
    // Test getNestedList with invalid type
    Tuple t = new Tuple().add("not a list");

    assertThrows(ClassCastException.class, () -> t.getNestedList(0));
  }

  @Test
  void testGetNestedTupleInvalidType() {
    // Test getNestedTuple with invalid type
    Tuple t = new Tuple().add("not a tuple");

    assertThrows(ClassCastException.class, () -> t.getNestedTuple(0));
  }

  @Test
  void testGetBigIntegerFromNull() {
    // Test getBigInteger with null
    Tuple t = new Tuple().addObject(null);

    assertThrows(NullPointerException.class, () -> t.getBigInteger(0));
  }

  @Test
  void testGetBigIntegerFromBigInteger() {
    // Test getBigInteger when value is already BigInteger
    BigInteger bigInt = new BigInteger("123456789012345678901234567890");
    Tuple t = new Tuple().add(bigInt);

    BigInteger result = t.getBigInteger(0);
    assertEquals(bigInt, result);
    assertSame(bigInt, result); // Should return the same instance
  }

  @Test
  void testGetBigIntegerFromLong() {
    // Test getBigInteger when value is Long
    Tuple t = new Tuple().add(42L);

    BigInteger result = t.getBigInteger(0);
    assertEquals(BigInteger.valueOf(42L), result);
  }

  @Test
  void testGetPackedSizeNested() {
    // Test getPackedSize with nested parameter
    Tuple t = new Tuple().add("test").add((String) null).add(42L);

    // getPackedSize(true) should count null differently
    int sizeNested = t.getPackedSize(true);
    int sizeNormal = t.getPackedSize(false);

    // When nested=true, null values get extra encoding
    assertTrue(sizeNested > sizeNormal);
  }

  @Test
  void testCompareToWithPackedTuples() {
    // Test compareTo when both tuples have packed representation
    Tuple t1 = Tuple.from("a", "b");
    Tuple t2 = Tuple.from("a", "c");

    // Force packing
    t1.pack();
    t2.pack();

    // Should use byte comparison
    assertTrue(t1.compareTo(t2) < 0);
    assertTrue(t2.compareTo(t1) > 0);
    assertEquals(0, t1.compareTo(t1));
  }

  @Test
  void testCompareToWithUnpackedTuples() {
    // Test compareTo when tuples don't have packed representation
    Tuple t1 = new Tuple().add("x").add(1L);
    Tuple t2 = new Tuple().add("x").add(2L);

    // Don't pack - should use element comparison
    assertTrue(t1.compareTo(t2) < 0);
    assertTrue(t2.compareTo(t1) > 0);
  }

  @Test
  void testCompareToMixedPackedUnpacked() {
    // Test compareTo with one packed and one unpacked
    Tuple t1 = Tuple.from("test", 100L);
    Tuple t2 = new Tuple().add("test").add(200L);

    // Pack only t1
    t1.pack();

    // Should still compare correctly
    assertTrue(t1.compareTo(t2) < 0);
  }

  @Test
  void testEqualsWithNullObject() {
    Tuple t = new Tuple().add("test");

    assertFalse(t.equals(null));
  }

  @Test
  void testEqualsWithSelf() {
    Tuple t = new Tuple().add("test");

    assertTrue(t.equals(t));
  }

  @Test
  void testEqualsWithNonTuple() {
    Tuple t = new Tuple().add("test");

    assertFalse(t.equals("not a tuple"));
    assertFalse(t.equals(42));
    assertFalse(t.equals(new Object()));
  }

  @Test
  void testPackInternalWithCopyFalse() {
    // Test packInternal with copy=false
    Tuple t = new Tuple().add("test");

    // First pack to initialize internal packed array
    byte[] packed1 = t.pack();

    // Call packInternal with copy=false (package-private access via reflection if needed)
    // For now, we can test indirectly through packMaybe()
    byte[] packed2 = t.packMaybe();

    // Should return the same array reference when copy=false
    assertArrayEquals(packed1, packed2);
  }

  @Test
  void testAddAllTupleWithBothPacked() {
    // Test addAll(Tuple) when both tuples have packed representations
    Tuple t1 = Tuple.from("a", "b");
    Tuple t2 = Tuple.from("c", "d");

    // Force packing
    t1.pack();
    t2.pack();

    // Add them
    Tuple combined = t1.addAll(t2);

    // The combined tuple should have pre-computed packed representation
    assertEquals(4, combined.size());
    assertEquals("a", combined.getString(0));
    assertEquals("b", combined.getString(1));
    assertEquals("c", combined.getString(2));
    assertEquals("d", combined.getString(3));
  }

  @Test
  void testAddAllTupleWithBothPackedAndKnownSize() {
    // Test addAll(Tuple) when both have memoized packed sizes
    Tuple t1 = Tuple.from("x");
    Tuple t2 = Tuple.from("y");

    // Force size calculation
    t1.getPackedSize();
    t2.getPackedSize();

    // Force packing
    t1.pack();
    t2.pack();

    // Add them
    Tuple combined = t1.addAll(t2);

    // The combined tuple should have pre-computed size
    int expectedSize = t1.getPackedSize() + t2.getPackedSize();
    assertEquals(expectedSize, combined.getPackedSize());
  }

  @Test
  void testGetPackedSizeWithNullsInNestedMode() {
    // Test branch in getPackedSize(nested) that counts nulls
    Tuple t = new Tuple().add("first").addObject(null).add("third").addObject(null);

    // Force memoization
    t.getPackedSize();

    // Call with nested=true to hit the null counting branch
    int nestedSize = t.getPackedSize(true);
    int normalSize = t.getPackedSize(false);

    // nested=true adds extra for each null
    assertTrue(nestedSize > normalSize);
  }

  @Test
  void testPackIntoWithAlreadyPacked() {
    // Test packInto when tuple is already packed
    Tuple t = Tuple.from("test", 42L);

    // Force packing
    byte[] packed = t.pack();

    // Now pack into buffer
    ByteBuffer buffer = ByteBuffer.allocate(packed.length);
    t.packInto(buffer);

    buffer.flip();
    byte[] result = new byte[buffer.remaining()];
    buffer.get(result);

    assertArrayEquals(packed, result);
  }

  @Test
  void testAddCharacter() {
    // Test add(char) which converts to long
    Tuple t = new Tuple();

    Tuple t1 = t.add('A');
    assertEquals(65L, t1.getLong(0)); // 'A' = 65

    Tuple t2 = t.add('€'); // Unicode char
    assertEquals(8364L, t2.getLong(0)); // '€' = 8364
  }

  @Test
  void testAddByte() {
    // Test add(byte) which converts to long
    Tuple t = new Tuple();

    Tuple t1 = t.add((byte) 127);
    assertEquals(127L, t1.getLong(0));

    Tuple t2 = t.add((byte) -128);
    assertEquals(-128L, t2.getLong(0));
  }

  @Test
  void testHashCodeWithUnpackedTuple() {
    // Test hashCode calculation for unpacked tuple
    Tuple t = new Tuple().add("test").add(42L);

    int hash1 = t.hashCode();
    int hash2 = t.hashCode(); // Should be memoized

    assertEquals(hash1, hash2);
    assertTrue(hash1 != 0); // Very unlikely to be 0
  }

  @Test
  void testGetBytesWithNull() {
    // Test getBytes when value is null
    Tuple t = new Tuple().add((byte[]) null);

    assertNull(t.getBytes(0));
  }

  @Test
  void testGetStringWithNull() {
    // Test getString when value is null
    Tuple t = new Tuple().add((String) null);

    assertNull(t.getString(0));
  }

  @Test
  void testGetUUIDWithNull() {
    // Test getUUID when value is null
    Tuple t = new Tuple().add((UUID) null);

    assertNull(t.getUUID(0));
  }

  @Test
  void testGetNestedListWithNull() {
    // Test getNestedList when value is null
    Tuple t = new Tuple().add((List<?>) null);

    assertNull(t.getNestedList(0));
  }

  @Test
  void testGetNestedTupleWithNull() {
    // Test getNestedTuple when value is null
    Tuple t = new Tuple().add((Tuple) null);

    assertNull(t.getNestedTuple(0));
  }

  @Test
  void testAddAllListWithLargeList() {
    // Test addAll(List) with size calculation
    List<Object> items = Arrays.asList("a", "b", "c", "d", "e");
    Tuple t1 = new Tuple().add("start");

    Tuple t2 = t1.addAll(items);

    assertEquals(6, t2.size());
    assertEquals("start", t2.getString(0));
    assertEquals("a", t2.getString(1));
    assertEquals("e", t2.getString(5));
  }

  @Test
  void testFromItemsWithNonListIterable() {
    // Test fromItems with an Iterable that's not a List
    Iterable<String> iterable = Arrays.asList("x", "y", "z");

    Tuple t = Tuple.fromItems(iterable);

    assertEquals(3, t.size());
    assertEquals("x", t.getString(0));
    assertEquals("y", t.getString(1));
    assertEquals("z", t.getString(2));
  }

  @Test
  void testPackWithPrefixAndAlreadyPacked() {
    // Test pack(prefix) when tuple is already packed
    Tuple t = Tuple.from("test");

    // Force packing
    t.pack();

    // Pack with prefix
    byte[] prefix = {1, 2, 3};
    byte[] withPrefix = t.pack(prefix);

    // Should start with prefix
    assertEquals(prefix[0], withPrefix[0]);
    assertEquals(prefix[1], withPrefix[1]);
    assertEquals(prefix[2], withPrefix[2]);
  }

  @Test
  void testFromBytesEdgeCases() {
    // Test fromBytes with edge cases
    Tuple original = new Tuple().add("test");
    byte[] packed = original.pack();

    // Test with exact bounds
    Tuple t1 = Tuple.fromBytes(packed, 0, packed.length);
    assertEquals(original, t1);

    // Test with larger array
    byte[] larger = new byte[packed.length + 10];
    System.arraycopy(packed, 0, larger, 5, packed.length);

    Tuple t2 = Tuple.fromBytes(larger, 5, packed.length);
    assertEquals(original, t2);
  }

  @Test
  void testAddAllTupleOnlyOneHasMemoizedSize() {
    // Test addAll when only one tuple has memoized size
    Tuple t1 = Tuple.from("a");
    Tuple t2 = Tuple.from("b");

    // Only memoize size for t1
    t1.getPackedSize();

    Tuple combined = t1.addAll(t2);
    assertEquals(2, combined.size());
  }

  @Test
  void testAddAllTupleOnlyOneHasPacked() {
    // Test addAll when only one tuple is packed
    Tuple t1 = Tuple.from("x");
    Tuple t2 = Tuple.from("y");

    // Only pack t1
    t1.pack();

    Tuple combined = t1.addAll(t2);
    assertEquals(2, combined.size());
  }
}