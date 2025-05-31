package io.github.panghy.javaflow.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link StringUtil} class.
 */
class StringUtilTest {

  // Test constants for surrogate pairs
  private static final String EMOJI_FACE = "😀"; // U+1F600, requires surrogate pair
  private static final String MUSICAL_NOTE = "𝄞"; // U+1D11E, requires surrogate pair
  private static final char HIGH_SURROGATE = '\uD800';
  private static final char LOW_SURROGATE = '\uDC00';
  private static final char INVALID_LOW_SURROGATE = '\uDFFF';

  @Test
  void testAdjustForSurrogatesWithRegularCharacters() {
    // Test with regular characters above low surrogate range
    char c = 'A'; // 0x41, well below surrogate range
    char result = StringUtil.adjustForSurrogates(c, "ABC", 0);
    
    // Should apply the ABOVE_SURROGATES adjustment
    char expected = (char) (c + (Character.MAX_VALUE - Character.MAX_LOW_SURROGATE));
    assertEquals(expected, result);
  }

  @Test
  void testAdjustForSurrogatesWithHighCharacters() {
    // Test with characters above the low surrogate range
    char c = '\uE000'; // Above low surrogate range
    char result = StringUtil.adjustForSurrogates(c, "test", 0);
    
    // Should subtract SURROGATE_COUNT
    char expected = (char) (c - (Character.MAX_LOW_SURROGATE - Character.MIN_HIGH_SURROGATE + 1));
    assertEquals(expected, result);
  }

  @Test
  void testAdjustForSurrogatesWithValidSurrogatePair() {
    String validPair = new String(new char[]{HIGH_SURROGATE, LOW_SURROGATE});
    
    char result = StringUtil.adjustForSurrogates(HIGH_SURROGATE, validPair, 0);
    
    // Should apply ABOVE_SURROGATES adjustment for valid surrogate
    char expected = (char) (HIGH_SURROGATE + (Character.MAX_VALUE - Character.MAX_LOW_SURROGATE));
    assertEquals(expected, result);
  }

  @Test
  void testAdjustForSurrogatesWithHighSurrogateWithoutLow() {
    String invalidString = new String(new char[]{HIGH_SURROGATE, 'A'});
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.adjustForSurrogates(HIGH_SURROGATE, invalidString, 0);
    });
    
    assertEquals("malformed UTF-16 string contains high surrogate that is not followed by low surrogate", 
                 exception.getMessage());
  }

  @Test
  void testAdjustForSurrogatesWithHighSurrogateAtEndOfString() {
    String invalidString = new String(new char[]{HIGH_SURROGATE});
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.adjustForSurrogates(HIGH_SURROGATE, invalidString, 0);
    });
    
    assertEquals("malformed UTF-16 string contains high surrogate that is not followed by low surrogate", 
                 exception.getMessage());
  }

  @Test
  void testAdjustForSurrogatesWithLowSurrogateWithoutHigh() {
    String invalidString = new String(new char[]{'A', LOW_SURROGATE});
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.adjustForSurrogates(LOW_SURROGATE, invalidString, 1);
    });
    
    assertEquals("malformed UTF-16 string contains low surrogate without prior high surrogate", 
                 exception.getMessage());
  }

  @Test
  void testAdjustForSurrogatesWithLowSurrogateAtStartOfString() {
    String invalidString = new String(new char[]{LOW_SURROGATE, 'A'});
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.adjustForSurrogates(LOW_SURROGATE, invalidString, 0);
    });
    
    assertEquals("malformed UTF-16 string contains low surrogate without prior high surrogate", 
                 exception.getMessage());
  }

  @Test
  void testValidateWithValidString() {
    // Should not throw any exception
    StringUtil.validate("Hello, World!");
    StringUtil.validate("Regular ASCII text");
    StringUtil.validate("Café"); // UTF-16 but no surrogates
    StringUtil.validate(""); // Empty string
  }

  @Test
  void testValidateWithValidSurrogatePairs() {
    // Should not throw any exception
    StringUtil.validate(EMOJI_FACE);
    StringUtil.validate(MUSICAL_NOTE);
    StringUtil.validate("Hello " + EMOJI_FACE + " World");
  }

  @Test
  void testValidateWithHighSurrogateWithoutLow() {
    String invalidString = new String(new char[]{HIGH_SURROGATE, 'A'});
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.validate(invalidString);
    });
    
    assertEquals("malformed UTF-16 string contains high surrogate that is not followed by low surrogate", 
                 exception.getMessage());
  }

  @Test
  void testValidateWithHighSurrogateAtEnd() {
    String invalidString = "Hello" + HIGH_SURROGATE;
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.validate(invalidString);
    });
    
    assertEquals("malformed UTF-16 string contains high surrogate that is not followed by low surrogate", 
                 exception.getMessage());
  }

  @Test
  void testValidateWithLowSurrogateWithoutHigh() {
    String invalidString = new String(new char[]{'A', LOW_SURROGATE});
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.validate(invalidString);
    });
    
    assertEquals("malformed UTF-16 string contains low surrogate without prior high surrogate", 
                 exception.getMessage());
  }

  @Test
  void testValidateWithLowSurrogateAtStart() {
    String invalidString = LOW_SURROGATE + "Hello";
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.validate(invalidString);
    });
    
    assertEquals("malformed UTF-16 string contains low surrogate without prior high surrogate", 
                 exception.getMessage());
  }

  @Test
  void testCompareUtf8WithIdenticalStrings() {
    assertEquals(0, StringUtil.compareUtf8("hello", "hello"));
    assertEquals(0, StringUtil.compareUtf8("", ""));
    assertEquals(0, StringUtil.compareUtf8("test", "test"));
  }

  @Test
  void testCompareUtf8WithDifferentStrings() {
    assertTrue(StringUtil.compareUtf8("apple", "banana") < 0);
    assertTrue(StringUtil.compareUtf8("banana", "apple") > 0);
    assertTrue(StringUtil.compareUtf8("test", "testing") < 0);
    assertTrue(StringUtil.compareUtf8("testing", "test") > 0);
  }

  @Test
  void testCompareUtf8WithPrefixes() {
    assertTrue(StringUtil.compareUtf8("test", "testing") < 0);
    assertTrue(StringUtil.compareUtf8("testing", "test") > 0);
    assertTrue(StringUtil.compareUtf8("", "nonempty") < 0);
    assertTrue(StringUtil.compareUtf8("nonempty", "") > 0);
  }

  @Test
  void testCompareUtf8WithSurrogates() {
    // Test comparison involving surrogate pairs
    String withEmoji = "hello" + EMOJI_FACE;
    String withoutEmoji = "hello";
    
    // The exact result depends on the surrogate adjustment logic
    int result = StringUtil.compareUtf8(withEmoji, withoutEmoji);
    // Should be consistent
    assertEquals(-StringUtil.compareUtf8(withoutEmoji, withEmoji), result);
  }

  @Test
  void testCompareUtf8WithUnicodeCharacters() {
    // Test with various Unicode characters
    String ascii = "hello";
    String unicode = "héllo"; // With accent
    
    int result = StringUtil.compareUtf8(ascii, unicode);
    // Should be consistent in ordering
    assertEquals(-StringUtil.compareUtf8(unicode, ascii), result);
  }

  @Test
  void testCompareUtf8Transitivity() {
    // Test transitivity: if a < b and b < c, then a < c
    String a = "apple";
    String b = "banana";
    String c = "cherry";
    
    assertTrue(StringUtil.compareUtf8(a, b) < 0);
    assertTrue(StringUtil.compareUtf8(b, c) < 0);
    assertTrue(StringUtil.compareUtf8(a, c) < 0);
  }

  @Test
  void testPackedSizeWithAsciiCharacters() {
    // ASCII characters should be 1 byte each
    assertEquals(5, StringUtil.packedSize("hello"));
    assertEquals(0, StringUtil.packedSize(""));
    assertEquals(1, StringUtil.packedSize("a"));
  }

  @Test
  void testPackedSizeWithNullCharacter() {
    // Null character should be encoded as 2 bytes (\x00\xff)
    assertEquals(2, StringUtil.packedSize("\0"));
    assertEquals(9, StringUtil.packedSize("hello\0\0")); // 5 + 2 + 2
  }

  @Test
  void testPackedSizeWithTwoByteCharacters() {
    // Characters between 0x80 and 0x7FF should be 2 bytes
    assertEquals(2, StringUtil.packedSize("é")); // U+00E9
    assertEquals(2, StringUtil.packedSize("ñ")); // U+00F1
    assertEquals(4, StringUtil.packedSize("éñ")); // 2 + 2
  }

  @Test
  void testPackedSizeWithThreeByteCharacters() {
    // Characters between 0x800 and 0xFFFF (excluding surrogates) should be 3 bytes
    assertEquals(3, StringUtil.packedSize("€")); // U+20AC
    assertEquals(3, StringUtil.packedSize("中")); // U+4E2D (Chinese character)
    assertEquals(6, StringUtil.packedSize("€中")); // 3 + 3
  }

  @Test
  void testPackedSizeWithSurrogatePairs() {
    // Surrogate pairs (U+10000 to U+10FFFF) should be 4 bytes
    assertEquals(4, StringUtil.packedSize(EMOJI_FACE)); // U+1F600
    assertEquals(4, StringUtil.packedSize(MUSICAL_NOTE)); // U+1D11E
    assertEquals(8, StringUtil.packedSize(EMOJI_FACE + MUSICAL_NOTE)); // 4 + 4
  }

  @Test
  void testPackedSizeWithMixedCharacters() {
    // Mix of different character types
    String mixed = "a" + // 1 byte (ASCII)
                   "é" + // 2 bytes (two-byte UTF-8)
                   "€" + // 3 bytes (three-byte UTF-8)
                   EMOJI_FACE + // 4 bytes (four-byte UTF-8)
                   "\0"; // 2 bytes (null encoded as \x00\xff)
    
    assertEquals(1 + 2 + 3 + 4 + 2, StringUtil.packedSize(mixed));
  }

  @Test
  void testPackedSizeWithInvalidSurrogatePairs() {
    // High surrogate without low surrogate
    String invalidHigh = new String(new char[]{'a', HIGH_SURROGATE, 'b'});
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.packedSize(invalidHigh);
    });
    
    assertEquals("malformed UTF-16 string contains high surrogate that is not followed by low surrogate", 
                 exception.getMessage());
  }

  @Test
  void testPackedSizeWithInvalidLowSurrogate() {
    // Low surrogate without high surrogate
    String invalidLow = new String(new char[]{'a', LOW_SURROGATE, 'b'});
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.packedSize(invalidLow);
    });
    
    assertEquals("malformed UTF-16 string contains low surrogate without prior high surrogate", 
                 exception.getMessage());
  }

  @Test
  void testPackedSizeWithHighSurrogateAtEnd() {
    // High surrogate at end of string
    String invalidEnd = "hello" + HIGH_SURROGATE;
    
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
      StringUtil.packedSize(invalidEnd);
    });
    
    assertEquals("malformed UTF-16 string contains high surrogate that is not followed by low surrogate", 
                 exception.getMessage());
  }

  @Test
  void testPackedSizeCharacterRanges() {
    // Test boundary characters for different UTF-8 encoding lengths
    
    // Largest 1-byte character (0x7F)
    assertEquals(1, StringUtil.packedSize("\u007F"));
    
    // Smallest 2-byte character (0x80)
    assertEquals(2, StringUtil.packedSize("\u0080"));
    
    // Largest 2-byte character (0x7FF)
    assertEquals(2, StringUtil.packedSize("\u07FF"));
    
    // Smallest 3-byte character (0x800)
    assertEquals(3, StringUtil.packedSize("\u0800"));
    
    // Character just before surrogate range (0xD7FF)
    assertEquals(3, StringUtil.packedSize("\uD7FF"));
    
    // Character just after surrogate range (0xE000)
    assertEquals(3, StringUtil.packedSize("\uE000"));
    
    // Largest 3-byte character (0xFFFF)
    assertEquals(3, StringUtil.packedSize("\uFFFF"));
  }

  @Test
  void testCompareUtf8Consistency() {
    // Test that compareUtf8 is consistent with its contract
    String s1 = "test";
    String s2 = "testing";
    
    int result12 = StringUtil.compareUtf8(s1, s2);
    int result21 = StringUtil.compareUtf8(s2, s1);
    
    // Should be opposite signs (or both zero)
    assertTrue((result12 > 0 && result21 < 0) || 
               (result12 < 0 && result21 > 0) || 
               (result12 == 0 && result21 == 0));
  }

  @Test
  void testCompareUtf8Reflexivity() {
    // Test that compare(s, s) == 0
    String[] testStrings = {
        "",
        "hello",
        "world with spaces",
        "unicode: éñ€",
        EMOJI_FACE,
        "mixed" + EMOJI_FACE + "content"
    };
    
    for (String s : testStrings) {
      assertEquals(0, StringUtil.compareUtf8(s, s));
    }
  }

  @Test
  void testLargeStrings() {
    // Test with larger strings to ensure performance is reasonable
    StringBuilder large1 = new StringBuilder();
    StringBuilder large2 = new StringBuilder();
    
    for (int i = 0; i < 1000; i++) {
      large1.append("test").append(i);
      large2.append("test").append(i);
    }
    
    // Should be equal
    assertEquals(0, StringUtil.compareUtf8(large1.toString(), large2.toString()));
    
    // Test packed size calculation
    int packedSize = StringUtil.packedSize(large1.toString());
    assertTrue(packedSize > 0);
    
    // Validate the large string (should not throw)
    StringUtil.validate(large1.toString());
  }

  @Test
  void testEdgeCasesForSurrogateAdjustment() {
    // Test characters at the boundaries of the surrogate adjustment logic
    
    // Character just above MAX_LOW_SURROGATE
    char aboveLowSurrogate = (char) (Character.MAX_LOW_SURROGATE + 1);
    char result = StringUtil.adjustForSurrogates(aboveLowSurrogate, "test", 0);
    
    char expected = (char) (aboveLowSurrogate - (Character.MAX_LOW_SURROGATE - Character.MIN_HIGH_SURROGATE + 1));
    assertEquals(expected, result);
    
    // Character well above the surrogate range to test the first branch
    char wellAboveSurrogates = '\uF000'; // Well above surrogate range
    char result2 = StringUtil.adjustForSurrogates(wellAboveSurrogates, "test", 0);
    
    char expected2 = (char) (wellAboveSurrogates - (Character.MAX_LOW_SURROGATE - Character.MIN_HIGH_SURROGATE + 1));
    assertEquals(expected2, result2);
  }
}