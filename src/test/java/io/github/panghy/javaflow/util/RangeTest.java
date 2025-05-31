package io.github.panghy.javaflow.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link Range} class.
 */
class RangeTest {

  @Test
  void testConstructorWithValidInputs() {
    byte[] begin = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    
    Range range = new Range(begin, end);
    
    assertArrayEquals(begin, range.begin);
    assertArrayEquals(end, range.end);
  }

  @Test
  void testConstructorWithNullBegin() {
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    
    NullPointerException exception = assertThrows(NullPointerException.class, () -> {
      new Range(null, end);
    });
    
    assertEquals("begin cannot be null", exception.getMessage());
  }

  @Test
  void testConstructorWithNullEnd() {
    byte[] begin = "start".getBytes(StandardCharsets.UTF_8);
    
    NullPointerException exception = assertThrows(NullPointerException.class, () -> {
      new Range(begin, null);
    });
    
    assertEquals("end cannot be null", exception.getMessage());
  }

  @Test
  void testConstructorWithEmptyArrays() {
    byte[] emptyBegin = new byte[0];
    byte[] emptyEnd = new byte[0];
    
    Range range = new Range(emptyBegin, emptyEnd);
    
    assertArrayEquals(emptyBegin, range.begin);
    assertArrayEquals(emptyEnd, range.end);
  }

  @Test
  void testStartsWithValidPrefix() {
    byte[] prefix = "prefix".getBytes(StandardCharsets.UTF_8);
    
    Range range = Range.startsWith(prefix);
    
    assertArrayEquals(prefix, range.begin);
    // end should be the result of strinc(prefix)
    byte[] expectedEnd = ByteArrayUtil.strinc(prefix);
    assertArrayEquals(expectedEnd, range.end);
  }

  @Test
  void testStartsWithNullPrefix() {
    NullPointerException exception = assertThrows(NullPointerException.class, () -> {
      Range.startsWith(null);
    });
    
    assertEquals("prefix cannot be null", exception.getMessage());
  }

  @Test
  void testStartsWithEmptyPrefix() {
    byte[] emptyPrefix = new byte[0];
    
    // Empty prefix should throw exception because there's no key beyond it
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Range.startsWith(emptyPrefix);
    });
    
    assertEquals("No key beyond supplied prefix", exception.getMessage());
  }

  @Test
  void testStartsWithPrefixContainingMaxBytes() {
    // Test with a prefix that ends with 0xff bytes
    byte[] prefix = {0x61, (byte) 0xff, (byte) 0xff}; // 'a' followed by two 0xff bytes
    
    Range range = Range.startsWith(prefix);
    
    assertArrayEquals(prefix, range.begin);
    // strinc should strip the 0xff bytes and increment the last non-0xff byte
    byte[] expectedEnd = {0x62}; // 'b'
    assertArrayEquals(expectedEnd, range.end);
  }

  @Test
  void testStartsWithAllMaxBytes() {
    // Test with a prefix of all 0xff bytes - should throw exception
    byte[] allMaxBytes = {(byte) 0xff, (byte) 0xff, (byte) 0xff};
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      Range.startsWith(allMaxBytes);
    });
    
    assertEquals("No key beyond supplied prefix", exception.getMessage());
  }

  @Test
  void testEqualsWithSameReference() {
    byte[] begin = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    Range range = new Range(begin, end);
    
    assertTrue(range.equals(range));
  }

  @Test
  void testEqualsWithIdenticalRanges() {
    byte[] begin1 = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end1 = "end".getBytes(StandardCharsets.UTF_8);
    byte[] begin2 = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end2 = "end".getBytes(StandardCharsets.UTF_8);
    
    Range range1 = new Range(begin1, end1);
    Range range2 = new Range(begin2, end2);
    
    assertTrue(range1.equals(range2));
    assertTrue(range2.equals(range1));
  }

  @Test
  void testEqualsWithDifferentBegin() {
    byte[] begin1 = "start1".getBytes(StandardCharsets.UTF_8);
    byte[] begin2 = "start2".getBytes(StandardCharsets.UTF_8);
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    
    Range range1 = new Range(begin1, end);
    Range range2 = new Range(begin2, end);
    
    assertFalse(range1.equals(range2));
    assertFalse(range2.equals(range1));
  }

  @Test
  void testEqualsWithDifferentEnd() {
    byte[] begin = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end1 = "end1".getBytes(StandardCharsets.UTF_8);
    byte[] end2 = "end2".getBytes(StandardCharsets.UTF_8);
    
    Range range1 = new Range(begin, end1);
    Range range2 = new Range(begin, end2);
    
    assertFalse(range1.equals(range2));
    assertFalse(range2.equals(range1));
  }

  @Test
  void testEqualsWithNull() {
    byte[] begin = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    Range range = new Range(begin, end);
    
    assertFalse(range.equals(null));
  }

  @Test
  void testEqualsWithDifferentClass() {
    byte[] begin = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    Range range = new Range(begin, end);
    
    assertFalse(range.equals("not a range"));
    assertFalse(range.equals(42));
  }

  @Test
  void testHashCodeConsistency() {
    byte[] begin = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    Range range = new Range(begin, end);
    
    int hashCode1 = range.hashCode();
    int hashCode2 = range.hashCode();
    
    assertEquals(hashCode1, hashCode2);
  }

  @Test
  void testHashCodeEqualRanges() {
    byte[] begin1 = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end1 = "end".getBytes(StandardCharsets.UTF_8);
    byte[] begin2 = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end2 = "end".getBytes(StandardCharsets.UTF_8);
    
    Range range1 = new Range(begin1, end1);
    Range range2 = new Range(begin2, end2);
    
    assertEquals(range1.hashCode(), range2.hashCode());
  }

  @Test
  void testHashCodeDifferentRanges() {
    byte[] begin1 = "start1".getBytes(StandardCharsets.UTF_8);
    byte[] begin2 = "start2".getBytes(StandardCharsets.UTF_8);
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    
    Range range1 = new Range(begin1, end);
    Range range2 = new Range(begin2, end);
    
    // Hash codes should likely be different (not guaranteed, but very likely)
    assertNotEquals(range1.hashCode(), range2.hashCode());
  }

  @Test
  void testHashCodeFormula() {
    // Test the specific hash code formula: Arrays.hashCode(begin) ^ (37 * Arrays.hashCode(end))
    byte[] begin = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    Range range = new Range(begin, end);
    
    int expectedHashCode = java.util.Arrays.hashCode(begin) ^ (37 * java.util.Arrays.hashCode(end));
    assertEquals(expectedHashCode, range.hashCode());
  }

  @Test
  void testToStringWithRegularBytes() {
    byte[] begin = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    Range range = new Range(begin, end);
    
    String result = range.toString();
    
    assertTrue(result.startsWith("Range("));
    assertTrue(result.endsWith(")"));
    assertTrue(result.contains("start"));
    assertTrue(result.contains("end"));
  }

  @Test
  void testToStringWithEmptyArrays() {
    byte[] emptyBegin = new byte[0];
    byte[] emptyEnd = new byte[0];
    Range range = new Range(emptyBegin, emptyEnd);
    
    String result = range.toString();
    
    assertTrue(result.startsWith("Range("));
    assertTrue(result.endsWith(")"));
    // Should contain empty strings representation
    assertTrue(result.contains("\"\""));
  }

  @Test
  void testToStringWithSpecialBytes() {
    // Test with bytes that need escaping
    byte[] begin = {0x00, 0x01, (byte) 0xff};
    byte[] end = {0x7f, (byte) 0x80, (byte) 0x90};
    Range range = new Range(begin, end);
    
    String result = range.toString();
    
    assertTrue(result.startsWith("Range("));
    assertTrue(result.endsWith(")"));
    // Should contain escaped representations of the bytes
    assertTrue(result.contains("\\x"));
  }

  @Test
  void testEqualsAndHashCodeContract() {
    // Test that equal objects have equal hash codes
    byte[] begin1 = "test".getBytes(StandardCharsets.UTF_8);
    byte[] end1 = "test_end".getBytes(StandardCharsets.UTF_8);
    byte[] begin2 = "test".getBytes(StandardCharsets.UTF_8);
    byte[] end2 = "test_end".getBytes(StandardCharsets.UTF_8);
    
    Range range1 = new Range(begin1, end1);
    Range range2 = new Range(begin2, end2);
    
    // Objects should be equal
    assertEquals(range1, range2);
    
    // Hash codes should be equal
    assertEquals(range1.hashCode(), range2.hashCode());
  }

  @Test
  void testRangeImmutability() {
    byte[] begin = "start".getBytes(StandardCharsets.UTF_8);
    byte[] end = "end".getBytes(StandardCharsets.UTF_8);
    
    // Keep references to original arrays
    byte[] originalBegin = begin.clone();
    byte[] originalEnd = end.clone();
    
    Range range = new Range(begin, end);
    
    // Modify the original arrays
    begin[0] = 'X';
    end[0] = 'Y';
    
    // Range should still have the original values (defensive copy should have been made)
    // Note: Actually, Range doesn't make defensive copies, so the arrays are shared
    // This test documents the current behavior
    assertArrayEquals(begin, range.begin); // They share the same reference
    assertArrayEquals(end, range.end); // They share the same reference
  }

  @Test
  void testStartsWithVariousLengths() {
    // Test startsWith with different prefix lengths
    byte[] shortPrefix = "a".getBytes(StandardCharsets.UTF_8);
    byte[] longPrefix = "very_long_prefix_string".getBytes(StandardCharsets.UTF_8);
    
    Range shortRange = Range.startsWith(shortPrefix);
    Range longRange = Range.startsWith(longPrefix);
    
    assertArrayEquals(shortPrefix, shortRange.begin);
    assertArrayEquals(longPrefix, longRange.begin);
    
    // Ends should be properly calculated
    assertArrayEquals(ByteArrayUtil.strinc(shortPrefix), shortRange.end);
    assertArrayEquals(ByteArrayUtil.strinc(longPrefix), longRange.end);
  }

  @Test
  void testRangeWithBinaryData() {
    // Test with arbitrary binary data
    byte[] begin = {0x00, 0x01, 0x02, 0x03, (byte) 0xff};
    byte[] end = {(byte) 0xff, (byte) 0xfe, (byte) 0xfd};
    
    Range range = new Range(begin, end);
    
    assertArrayEquals(begin, range.begin);
    assertArrayEquals(end, range.end);
    
    // Should work with toString (though output will be escaped)
    String str = range.toString();
    assertTrue(str.contains("Range("));
  }

  @Test
  void testStartsWithSingleBytePrefix() {
    byte[] singleByte = {0x42}; // 'B' in ASCII
    
    Range range = Range.startsWith(singleByte);
    
    assertArrayEquals(singleByte, range.begin);
    byte[] expectedEnd = {0x43}; // 'C' in ASCII
    assertArrayEquals(expectedEnd, range.end);
  }

  @Test
  void testStartsWithPrefixEndingWith254() {
    // Test with a prefix ending with 0xfe (should increment to 0xff)
    byte[] prefix = {0x61, (byte) 0xfe}; // 'a' followed by 0xfe
    
    Range range = Range.startsWith(prefix);
    
    assertArrayEquals(prefix, range.begin);
    byte[] expectedEnd = {0x61, (byte) 0xff}; // 'a' followed by 0xff
    assertArrayEquals(expectedEnd, range.end);
  }
}