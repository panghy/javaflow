package io.github.panghy.javaflow.util;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the ByteArrayUtil class.
 */
class ByteArrayUtilTest {

  @Test
  void testEmptyBytesConstant() {
    assertEquals(0, ByteArrayUtil.EMPTY_BYTES.length);
    assertArrayEquals(new byte[0], ByteArrayUtil.EMPTY_BYTES);
  }

  // Join tests
  @Test
  void testJoinWithList() {
    byte[] a = {1, 2};
    byte[] b = {3, 4};
    byte[] c = {5, 6};
    byte[] interlude = {0};
    
    List<byte[]> parts = Arrays.asList(a, b, c);
    byte[] result = ByteArrayUtil.join(interlude, parts);
    
    assertArrayEquals(new byte[]{1, 2, 0, 3, 4, 0, 5, 6}, result);
  }

  @Test
  void testJoinWithEmptyList() {
    List<byte[]> parts = Collections.emptyList();
    byte[] result = ByteArrayUtil.join(new byte[]{1}, parts);
    assertArrayEquals(new byte[0], result);
  }

  @Test
  void testInterludeJoinBasic() {
    byte[] a = {1, 2};
    byte[] b = {3, 4};
    byte[] c = {5, 6};
    byte[] interlude = {0};
    
    byte[] result = ByteArrayUtil.interludeJoin(interlude, new byte[][]{a, b, c});
    assertArrayEquals(new byte[]{1, 2, 0, 3, 4, 0, 5, 6}, result);
  }

  @Test
  void testInterludeJoinWithNullInterlude() {
    byte[] a = {1, 2};
    byte[] b = {3, 4};
    
    byte[] result = ByteArrayUtil.interludeJoin(null, new byte[][]{a, b});
    assertArrayEquals(new byte[]{1, 2, 3, 4}, result);
  }

  @Test
  void testInterludeJoinWithEmptyInterlude() {
    byte[] a = {1, 2};
    byte[] b = {3, 4};
    
    byte[] result = ByteArrayUtil.interludeJoin(new byte[0], new byte[][]{a, b});
    assertArrayEquals(new byte[]{1, 2, 3, 4}, result);
  }

  @Test
  void testInterludeJoinWithNullParts() {
    byte[] result = ByteArrayUtil.interludeJoin(new byte[]{1}, null);
    assertArrayEquals(new byte[0], result);
  }

  @Test
  void testInterludeJoinWithEmptyParts() {
    byte[] result = ByteArrayUtil.interludeJoin(new byte[]{1}, new byte[0][]);
    assertArrayEquals(new byte[0], result);
  }

  @Test
  void testInterludeJoinWithSinglePart() {
    byte[] part = {1, 2, 3};
    byte[] result = ByteArrayUtil.interludeJoin(new byte[]{0}, new byte[][]{part});
    assertArrayEquals(new byte[]{1, 2, 3}, result);
  }

  @Test
  void testInterludeJoinWithEmptyPart() {
    byte[] a = {1, 2};
    byte[] b = new byte[0];
    byte[] c = {3, 4};
    byte[] interlude = {0};
    
    byte[] result = ByteArrayUtil.interludeJoin(interlude, new byte[][]{a, b, c});
    assertArrayEquals(new byte[]{1, 2, 0, 0, 3, 4}, result);
  }

  @Test
  void testJoinVarargs() {
    byte[] a = {1, 2};
    byte[] b = {3, 4};
    byte[] c = {5, 6};
    
    byte[] result = ByteArrayUtil.join(a, b, c);
    assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, result);
  }

  @Test
  void testJoinVarargsSingle() {
    byte[] a = {1, 2, 3};
    byte[] result = ByteArrayUtil.join(a);
    assertArrayEquals(new byte[]{1, 2, 3}, result);
  }

  @Test
  void testJoinVarargsEmpty() {
    byte[] result = ByteArrayUtil.join();
    assertArrayEquals(new byte[0], result);
  }

  // RegionEquals tests
  @Test
  void testRegionEqualsBasic() {
    byte[] src = {1, 2, 3, 4, 5};
    byte[] pattern = {3, 4};
    
    assertTrue(ByteArrayUtil.regionEquals(src, 2, pattern));
    assertFalse(ByteArrayUtil.regionEquals(src, 1, pattern));
  }

  @Test
  void testRegionEqualsWithNullSrc() {
    assertTrue(ByteArrayUtil.regionEquals(null, 0, null));
    assertFalse(ByteArrayUtil.regionEquals(null, 0, new byte[]{1}));
    assertThrows(IllegalArgumentException.class, () -> 
        ByteArrayUtil.regionEquals(null, 1, new byte[]{1}));
  }

  @Test
  void testRegionEqualsWithNullPattern() {
    byte[] src = {1, 2, 3};
    assertFalse(ByteArrayUtil.regionEquals(src, 0, null));
  }

  @Test
  void testRegionEqualsStartAfterEnd() {
    byte[] src = {1, 2, 3};
    assertThrows(IllegalArgumentException.class, () -> 
        ByteArrayUtil.regionEquals(src, 3, new byte[]{1}));
  }

  @Test
  void testRegionEqualsPatternTooLong() {
    byte[] src = {1, 2, 3};
    byte[] pattern = {2, 3, 4};
    assertFalse(ByteArrayUtil.regionEquals(src, 1, pattern));
  }

  @Test
  void testRegionEqualsAtEnd() {
    byte[] src = {1, 2, 3, 4};
    byte[] pattern = {3, 4};
    assertTrue(ByteArrayUtil.regionEquals(src, 2, pattern));
  }

  // Replace tests
  @Test
  void testReplaceBasic() {
    byte[] src = {1, 2, 3, 2, 4};
    byte[] pattern = {2};
    byte[] replacement = {5};
    
    byte[] result = ByteArrayUtil.replace(src, pattern, replacement);
    assertArrayEquals(new byte[]{1, 5, 3, 5, 4}, result);
  }

  @Test
  void testReplaceWithNull() {
    assertNull(ByteArrayUtil.replace(null, new byte[]{1}, new byte[]{2}));
  }

  @Test
  void testReplaceWithLongerReplacement() {
    byte[] src = {1, 2, 3};
    byte[] pattern = {2};
    byte[] replacement = {4, 5};
    
    byte[] result = ByteArrayUtil.replace(src, pattern, replacement);
    assertArrayEquals(new byte[]{1, 4, 5, 3}, result);
  }

  @Test
  void testReplaceWithShorterReplacement() {
    byte[] src = {1, 2, 3, 4};
    byte[] pattern = {2, 3};
    byte[] replacement = {5};
    
    byte[] result = ByteArrayUtil.replace(src, pattern, replacement);
    assertArrayEquals(new byte[]{1, 5, 4}, result);
  }

  @Test
  void testReplaceWithNullReplacement() {
    byte[] src = {1, 2, 3, 2, 4};
    byte[] pattern = {2};
    
    byte[] result = ByteArrayUtil.replace(src, pattern, null);
    assertArrayEquals(new byte[]{1, 3, 4}, result);
  }

  @Test
  void testReplaceWithEmptyPattern() {
    byte[] src = {1, 2, 3};
    byte[] pattern = new byte[0];
    byte[] replacement = {4};
    
    byte[] result = ByteArrayUtil.replace(src, pattern, replacement);
    assertArrayEquals(new byte[]{1, 2, 3}, result);
  }

  @Test
  void testReplaceWithNullPattern() {
    byte[] src = {1, 2, 3};
    byte[] replacement = {4};
    
    byte[] result = ByteArrayUtil.replace(src, null, replacement);
    assertArrayEquals(new byte[]{1, 2, 3}, result);
  }

  @Test
  void testReplaceWithOffsetAndLength() {
    byte[] src = {1, 2, 3, 2, 4, 2, 5};
    byte[] pattern = {2};
    byte[] replacement = {9};
    
    byte[] result = ByteArrayUtil.replace(src, 2, 3, pattern, replacement);
    assertArrayEquals(new byte[]{3, 9, 4}, result);
  }

  @Test
  void testReplaceWithInvalidOffset() {
    byte[] src = {1, 2, 3};
    assertThrows(IllegalArgumentException.class, () -> 
        ByteArrayUtil.replace(src, -1, 2, new byte[]{1}, new byte[]{2}));
    assertThrows(IllegalArgumentException.class, () -> 
        ByteArrayUtil.replace(src, 4, 2, new byte[]{1}, new byte[]{2}));
  }

  @Test
  void testReplaceWithInvalidLength() {
    byte[] src = {1, 2, 3};
    assertThrows(IllegalArgumentException.class, () -> 
        ByteArrayUtil.replace(src, 0, -1, new byte[]{1}, new byte[]{2}));
    assertThrows(IllegalArgumentException.class, () -> 
        ByteArrayUtil.replace(src, 1, 3, new byte[]{1}, new byte[]{2}));
  }

  @Test
  void testReplaceMultipleOccurrences() {
    byte[] src = {1, 2, 3, 2, 3, 2};
    byte[] pattern = {2, 3};
    byte[] replacement = {4};
    
    byte[] result = ByteArrayUtil.replace(src, pattern, replacement);
    assertArrayEquals(new byte[]{1, 4, 4, 2}, result);
  }

  @Test
  void testReplaceInternalMethod() {
    byte[] src = {1, 2, 3, 2, 4};
    byte[] pattern = {2};
    byte[] replacement = {5, 6};
    ByteBuffer dest = ByteBuffer.allocate(10);
    
    int newLength = ByteArrayUtil.replace(src, 0, src.length, pattern, replacement, dest);
    assertEquals(7, newLength);
    
    byte[] result = new byte[newLength];
    dest.flip();
    dest.get(result);
    assertArrayEquals(new byte[]{1, 5, 6, 3, 5, 6, 4}, result);
  }

  // Split tests
  @Test
  void testSplitBasic() {
    byte[] src = {1, 2, 3, 2, 4};
    byte[] delimiter = {2};
    
    List<byte[]> result = ByteArrayUtil.split(src, delimiter);
    assertEquals(3, result.size());
    assertArrayEquals(new byte[]{1}, result.get(0));
    assertArrayEquals(new byte[]{3}, result.get(1));
    assertArrayEquals(new byte[]{4}, result.get(2));
  }

  @Test
  void testSplitWithLeadingDelimiter() {
    byte[] src = {2, 1, 3};
    byte[] delimiter = {2};
    
    List<byte[]> result = ByteArrayUtil.split(src, delimiter);
    assertEquals(2, result.size());
    assertArrayEquals(new byte[0], result.get(0));
    assertArrayEquals(new byte[]{1, 3}, result.get(1));
  }

  @Test
  void testSplitWithTrailingDelimiter() {
    byte[] src = {1, 3, 2};
    byte[] delimiter = {2};
    
    List<byte[]> result = ByteArrayUtil.split(src, delimiter);
    assertEquals(2, result.size());
    assertArrayEquals(new byte[]{1, 3}, result.get(0));
    assertArrayEquals(new byte[0], result.get(1));
  }

  @Test
  void testSplitWithMultiByteDelimiter() {
    byte[] src = {1, 2, 3, 4, 2, 3, 5};
    byte[] delimiter = {2, 3};
    
    List<byte[]> result = ByteArrayUtil.split(src, delimiter);
    assertEquals(3, result.size());
    assertArrayEquals(new byte[]{1}, result.get(0));
    assertArrayEquals(new byte[]{4}, result.get(1));
    assertArrayEquals(new byte[]{5}, result.get(2));
  }

  @Test
  void testSplitNoDelimiterFound() {
    byte[] src = {1, 2, 3};
    byte[] delimiter = {4};
    
    List<byte[]> result = ByteArrayUtil.split(src, delimiter);
    assertEquals(1, result.size());
    assertArrayEquals(new byte[]{1, 2, 3}, result.get(0));
  }

  @Test
  void testSplitWithOffsetAndLength() {
    byte[] src = {1, 2, 3, 2, 4, 2, 5};
    byte[] delimiter = {2};
    
    List<byte[]> result = ByteArrayUtil.split(src, 2, 3, delimiter);
    assertEquals(2, result.size());
    assertArrayEquals(new byte[]{3}, result.get(0));
    assertArrayEquals(new byte[]{4}, result.get(1));
  }

  @Test
  void testSplitConsecutiveDelimiters() {
    byte[] src = {1, 2, 2, 3};
    byte[] delimiter = {2};
    
    List<byte[]> result = ByteArrayUtil.split(src, delimiter);
    assertEquals(3, result.size());
    assertArrayEquals(new byte[]{1}, result.get(0));
    assertArrayEquals(new byte[0], result.get(1));
    assertArrayEquals(new byte[]{3}, result.get(2));
  }

  // Comparison tests
  @Test
  void testCompareUnsigned() {
    byte[] a = {1, 2, 3};
    byte[] b = {1, 2, 4};
    byte[] c = {1, 2, 3};
    byte[] d = {1, 2};
    
    assertTrue(ByteArrayUtil.compareUnsigned(a, b) < 0);
    assertTrue(ByteArrayUtil.compareUnsigned(b, a) > 0);
    assertEquals(0, ByteArrayUtil.compareUnsigned(a, c));
    assertTrue(ByteArrayUtil.compareUnsigned(a, d) > 0);
    assertTrue(ByteArrayUtil.compareUnsigned(d, a) < 0);
  }

  @Test
  void testCompareUnsignedWithHighBytes() {
    byte[] a = {(byte) 0xFF};
    byte[] b = {0x01};
    
    assertTrue(ByteArrayUtil.compareUnsigned(a, b) > 0);
    assertTrue(ByteArrayUtil.compareUnsigned(b, a) < 0);
  }

  @Test
  void testComparator() {
    byte[] a = {1, 2, 3};
    byte[] b = {1, 2, 4};
    
    var comparator = ByteArrayUtil.comparator();
    assertTrue(comparator.compare(a, b) < 0);
    assertTrue(comparator.compare(b, a) > 0);
    assertEquals(0, comparator.compare(a, a));
  }

  // StartsWith tests
  @Test
  void testStartsWith() {
    byte[] array = {1, 2, 3, 4, 5};
    byte[] prefix1 = {1, 2};
    byte[] prefix2 = {1, 2, 3};
    byte[] notPrefix = {2, 3};
    byte[] tooLong = {1, 2, 3, 4, 5, 6};
    
    assertTrue(ByteArrayUtil.startsWith(array, prefix1));
    assertTrue(ByteArrayUtil.startsWith(array, prefix2));
    assertFalse(ByteArrayUtil.startsWith(array, notPrefix));
    assertFalse(ByteArrayUtil.startsWith(array, tooLong));
    assertTrue(ByteArrayUtil.startsWith(array, new byte[0]));
  }

  // Strinc tests
  @Test
  void testStrinc() {
    byte[] key1 = {1, 2, 3};
    byte[] result1 = ByteArrayUtil.strinc(key1);
    assertArrayEquals(new byte[]{1, 2, 4}, result1);
    
    byte[] key2 = {1, 2, (byte) 0xFF};
    byte[] result2 = ByteArrayUtil.strinc(key2);
    assertArrayEquals(new byte[]{1, 3}, result2);
    
    byte[] key3 = {(byte) 0xFF, (byte) 0xFF, 5};
    byte[] result3 = ByteArrayUtil.strinc(key3);
    assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, 6}, result3);
  }

  @Test
  void testStrincAllFF() {
    byte[] key = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    assertThrows(IllegalArgumentException.class, () -> ByteArrayUtil.strinc(key));
  }

  @Test
  void testStrincEmpty() {
    byte[] key = new byte[0];
    assertThrows(IllegalArgumentException.class, () -> ByteArrayUtil.strinc(key));
  }

  // KeyAfter tests
  @Test
  void testKeyAfter() {
    byte[] key = {1, 2, 3};
    byte[] result = ByteArrayUtil.keyAfter(key);
    assertArrayEquals(new byte[]{1, 2, 3, 0}, result);
  }

  @Test
  void testKeyAfterEmpty() {
    byte[] key = new byte[0];
    byte[] result = ByteArrayUtil.keyAfter(key);
    assertArrayEquals(new byte[]{0}, result);
  }

  // Rstrip tests
  @Test
  void testRstrip() {
    byte[] input1 = {1, 2, 3, 0, 0};
    byte[] result1 = ByteArrayUtil.rstrip(input1, (byte) 0);
    assertArrayEquals(new byte[]{1, 2, 3}, result1);
    
    byte[] input2 = {1, 2, 3};
    byte[] result2 = ByteArrayUtil.rstrip(input2, (byte) 0);
    assertArrayEquals(new byte[]{1, 2, 3}, result2);
    
    byte[] input3 = {0, 0, 0};
    byte[] result3 = ByteArrayUtil.rstrip(input3, (byte) 0);
    assertArrayEquals(new byte[0], result3);
    
    byte[] input4 = {(byte) 0xFF, (byte) 0xFF, 1, (byte) 0xFF};
    byte[] result4 = ByteArrayUtil.rstrip(input4, (byte) 0xFF);
    assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, 1}, result4);
  }

  // Encoding tests
  @Test
  void testEncodeInt() {
    long value = 0x0102030405060708L;
    byte[] encoded = ByteArrayUtil.encodeInt(value);
    
    assertEquals(8, encoded.length);
    // Little endian encoding
    assertEquals(0x08, encoded[0] & 0xFF);
    assertEquals(0x07, encoded[1] & 0xFF);
    assertEquals(0x06, encoded[2] & 0xFF);
    assertEquals(0x05, encoded[3] & 0xFF);
    assertEquals(0x04, encoded[4] & 0xFF);
    assertEquals(0x03, encoded[5] & 0xFF);
    assertEquals(0x02, encoded[6] & 0xFF);
    assertEquals(0x01, encoded[7] & 0xFF);
  }

  @Test
  void testDecodeInt() {
    byte[] encoded = {0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01};
    long decoded = ByteArrayUtil.decodeInt(encoded);
    assertEquals(0x0102030405060708L, decoded);
  }

  @Test
  void testDecodeIntInvalidLength() {
    byte[] tooShort = {1, 2, 3};
    byte[] tooLong = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    
    assertThrows(IllegalArgumentException.class, () -> ByteArrayUtil.decodeInt(tooShort));
    assertThrows(IllegalArgumentException.class, () -> ByteArrayUtil.decodeInt(tooLong));
  }

  @Test
  void testEncodeDecodeRoundTrip() {
    long[] values = {0, 1, -1, Long.MAX_VALUE, Long.MIN_VALUE, 12345678901234L};
    
    for (long value : values) {
      byte[] encoded = ByteArrayUtil.encodeInt(value);
      long decoded = ByteArrayUtil.decodeInt(encoded);
      assertEquals(value, decoded);
    }
  }

  // Printable tests
  @Test
  void testPrintableNull() {
    assertNull(ByteArrayUtil.printable(null));
  }

  @Test
  void testPrintableBasic() {
    byte[] input = "Hello".getBytes();
    assertEquals("Hello", ByteArrayUtil.printable(input));
  }

  @Test
  void testPrintableWithNonPrintable() {
    byte[] input = {0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x00, 0x01, 0x1F, 0x20, 0x7E, 0x7F};
    String result = ByteArrayUtil.printable(input);
    assertEquals("Hello\\x00\\x01\\x1f ~\\x7f", result);
  }

  @Test
  void testPrintableWithBackslash() {
    byte[] input = "test\\path".getBytes();
    assertEquals("test\\\\path", ByteArrayUtil.printable(input));
  }

  @Test
  void testPrintableWithHighBytes() {
    byte[] input = {(byte) 0xFF, (byte) 0x80, (byte) 0xAB};
    assertEquals("\\xff\\x80\\xab", ByteArrayUtil.printable(input));
  }

  @Test
  void testPrintableEmpty() {
    assertEquals("", ByteArrayUtil.printable(new byte[0]));
  }

  // NullCount tests
  @Test
  void testNullCount() {
    byte[] input1 = {1, 0, 2, 0, 3};
    assertEquals(2, ByteArrayUtil.nullCount(input1));
    
    byte[] input2 = {1, 2, 3};
    assertEquals(0, ByteArrayUtil.nullCount(input2));
    
    byte[] input3 = {0, 0, 0};
    assertEquals(3, ByteArrayUtil.nullCount(input3));
    
    byte[] input4 = new byte[0];
    assertEquals(0, ByteArrayUtil.nullCount(input4));
  }
}