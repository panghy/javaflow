/*
 * TupleUtil.java
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

import java.math.BigInteger;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Low-level utility class for tuple serialization and deserialization.
 *
 * <p>This class provides the core implementation for encoding and decoding
 * {@link Tuple} objects to and from byte arrays. It handles the complex
 * logic for serializing various Java data types into a binary format that
 * maintains proper ordering semantics when compared lexicographically.
 *
 * <h2>Supported Data Types</h2>
 * <p>The following Java types are supported for tuple encoding:
 * <ul>
 *   <li>{@code null} values</li>
 *   <li>Byte arrays ({@code byte[]})</li>
 *   <li>Strings ({@code String}) - encoded as UTF-8</li>
 *   <li>Nested tuples ({@code Tuple}) and lists ({@code List})</li>
 *   <li>Numeric types: {@code Long}, {@code BigInteger}, {@code Float}, {@code Double}</li>
 *   <li>Boolean values ({@code Boolean})</li>
 *   <li>UUIDs ({@code UUID})</li>
 * </ul>
 *
 * <h2>Type Codes</h2>
 * <p>Each supported type is assigned a type code that determines its position
 * in the sort order. The type codes ensure that different types sort in a
 * predictable order relative to each other:
 * <ul>
 *   <li>0x00: {@code null}</li>
 *   <li>0x01: Byte arrays</li>
 *   <li>0x02: Strings</li>
 *   <li>0x05: Nested structures (Tuples/Lists)</li>
 *   <li>0x14-0x1C: Negative integers (BigInteger, Long)</li>
 *   <li>0x15-0x1C: Positive integers (Long, BigInteger)</li>
 *   <li>0x20: Float</li>
 *   <li>0x21: Double</li>
 *   <li>0x26-0x27: Boolean values</li>
 *   <li>0x30: UUID</li>
 * </ul>
 *
 * <h2>Encoding Format</h2>
 * <p>The encoding format is designed to maintain lexicographic ordering:
 * <ul>
 *   <li>Each element begins with a type code byte</li>
 *   <li>Strings are encoded as UTF-8 with null byte escaping</li>
 *   <li>Numbers use specialized encodings for proper ordering</li>
 *   <li>Nested structures are recursively encoded with terminators</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * List<Object> items = Arrays.asList("hello", 42L, true);
 *
 * // Calculate required buffer size
 * int size = TupleUtil.getPackedSize(items, false);
 *
 * // Serialize to bytes
 * byte[] packed = TupleUtil.pack(items, size);
 *
 * // Deserialize back to objects
 * List<Object> unpacked = TupleUtil.unpack(packed);
 * }</pre>
 *
 * <p><strong>Note:</strong> This class is package-private and intended for
 * internal use by the {@link Tuple} class. External code should use the
 * {@link Tuple} API instead of calling these methods directly.
 *
 * <p>This class is derived from the FoundationDB project and maintains
 * compatibility with FoundationDB's tuple encoding format.
 */
class TupleUtil {
  private static final byte nil = 0x00;
  private static final Charset UTF8 = StandardCharsets.UTF_8;
  private static final BigInteger LONG_MIN_VALUE = BigInteger.valueOf(Long.MIN_VALUE);
  private static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);
  private static final int UUID_BYTES = 2 * Long.BYTES;
  private static final IterableComparator iterableComparator = new IterableComparator();

  private static final byte BYTES_CODE = 0x01;
  private static final byte STRING_CODE = 0x02;
  private static final byte NESTED_CODE = 0x05;
  private static final byte INT_ZERO_CODE = 0x14;
  private static final byte POS_INT_END = 0x1d;
  private static final byte NEG_INT_START = 0x0b;
  private static final byte FLOAT_CODE = 0x20;
  private static final byte DOUBLE_CODE = 0x21;
  private static final byte FALSE_CODE = 0x26;
  private static final byte TRUE_CODE = 0x27;
  private static final byte UUID_CODE = 0x30;

  private static final byte[] NULL_ARR = new byte[]{nil};
  private static final byte[] NULL_ESCAPED_ARR = new byte[]{nil, (byte) 0xFF};

  static class DecodeState {
    final List<Object> values;
    int end;
    int nullCount; // Basically a hack to allow findTerminator to return the terminator and null count

    DecodeState() {
      values = new ArrayList<>();
      end = 0;
    }

    void add(Object value, int end) {
      values.add(value);
      this.end = end;
    }

    int findNullTerminator(ByteBuffer buffer, int from, int to) {
      nullCount = 0;
      int x = from;
      while (x < to) {
        if (buffer.get(x) == 0x00) {
          if (x + 1 >= to || buffer.get(x + 1) != (byte) 0xFF) {
            return x;
          } else {
            nullCount++;
            x += 2;
          }
        } else {
          x += 1;
        }
      }
      throw new IllegalArgumentException("No terminator found for bytes starting at " + from);
    }
  }

  static class EncodeState {
    final ByteBuffer encodedBytes;
    int totalLength;

    EncodeState(ByteBuffer dest) {
      encodedBytes = dest;
      encodedBytes.order(ByteOrder.BIG_ENDIAN);
      totalLength = 0;
    }

    EncodeState add(byte[] encoded) {
      encodedBytes.put(encoded);
      totalLength += encoded.length;
      return this;
    }

    EncodeState add(byte[] encoded, int offset, int length) {
      encodedBytes.put(encoded, offset, length);
      totalLength += length;
      return this;
    }

    EncodeState addNullEscaped(byte[] encoded) {
      int nullCount = ByteArrayUtil.nullCount(encoded);
      if (nullCount == 0) {
        encodedBytes.put(encoded);
      } else {
        ByteArrayUtil.replace(encoded, 0, encoded.length, NULL_ARR, NULL_ESCAPED_ARR, encodedBytes);
      }
      totalLength += encoded.length + nullCount;
      return this;
    }

    EncodeState add(byte b) {
      encodedBytes.put(b);
      totalLength++;
      return this;
    }

    EncodeState add(int i) {
      encodedBytes.putInt(i);
      totalLength += Integer.BYTES;
      return this;
    }

    EncodeState add(long l) {
      encodedBytes.putLong(l);
      totalLength += Long.BYTES;
      return this;
    }
  }

  // These four functions are for adjusting the encoding of floating point numbers so
  // that when their byte representation is written out in big-endian order, unsigned
  // lexicographic byte comparison orders the values in the same way as the semantic
  // ordering of the values. This means flipping all bits for negative values and flipping
  // only the most-significant bit (i.e., the sign bit as all values in Java are signed)
  // in the case that the number is positive. For these purposes, 0.0 is positive and -0.0
  // is negative.

  private static int encodeFloatBits(float f) {
    int intBits = Float.floatToRawIntBits(f);
    return (intBits < 0) ? (~intBits) : (intBits ^ Integer.MIN_VALUE);
  }

  private static long encodeDoubleBits(double d) {
    long longBits = Double.doubleToRawLongBits(d);
    return (longBits < 0L) ? (~longBits) : (longBits ^ Long.MIN_VALUE);
  }

  private static float decodeFloatBits(int i) {
    int origBits = (i >= 0) ? (~i) : (i ^ Integer.MIN_VALUE);
    return Float.intBitsToFloat(origBits);
  }

  private static double decodeDoubleBits(long l) {
    long origBits = (l >= 0) ? (~l) : (l ^ Long.MIN_VALUE);
    return Double.longBitsToDouble(origBits);
  }

  // Get the minimal number of bytes in the representation of a long.
  private static int minimalByteCount(long i) {
    return (Long.SIZE + 7 - Long.numberOfLeadingZeros(i >= 0 ? i : -i)) / 8;
  }

  private static int minimalByteCount(BigInteger i) {
    int bitLength = (i.compareTo(BigInteger.ZERO) >= 0) ? i.bitLength() : i.negate().bitLength();
    return (bitLength + 7) / 8;
  }

  static int getCodeFor(Object o) {
    if (o == null) {
      return nil;
    }
    if (o instanceof byte[]) {
      return BYTES_CODE;
    }
    if (o instanceof String) {
      return STRING_CODE;
    }
    if (o instanceof Float) {
      return FLOAT_CODE;
    }
    if (o instanceof Double) {
      return DOUBLE_CODE;
    }
    if (o instanceof Boolean) {
      return FALSE_CODE;
    }
    if (o instanceof UUID) {
      return UUID_CODE;
    }
    if (o instanceof Number) {
      return INT_ZERO_CODE;
    }
    if (o instanceof List<?>) {
      return NESTED_CODE;
    }
    if (o instanceof Tuple) {
      return NESTED_CODE;
    }
    throw new IllegalArgumentException("Unsupported data type: " + o.getClass().getName());
  }

  static void encode(EncodeState state, Object t, boolean nested) {
    if (t == null) {
      if (nested) {
        state.add(NULL_ESCAPED_ARR);
      } else {
        state.add(nil);
      }
    } else if (t instanceof byte[]) {
      encode(state, (byte[]) t);
    } else if (t instanceof String) {
      encode(state, (String) t);
    } else if (t instanceof Float) {
      encode(state, (Float) t);
    } else if (t instanceof Double) {
      encode(state, (Double) t);
    } else if (t instanceof Boolean) {
      encode(state, (Boolean) t);
    } else if (t instanceof UUID) {
      encode(state, (UUID) t);
    } else if (t instanceof BigInteger) {
      encode(state, (BigInteger) t);
    } else if (t instanceof Character) {
      encode(state, (long) ((Character) t).charValue());
    } else if (t instanceof Number) {
      encode(state, ((Number) t).longValue());
    } else if (t instanceof List<?>) {
      encode(state, (List<?>) t);
    } else if (t instanceof Tuple) {
      encode(state, (Tuple) t);
    } else {
      throw new IllegalArgumentException("Unsupported data type: " + t.getClass().getName());
    }
  }

  static void encode(EncodeState state, Object t) {
    encode(state, t, false);
  }

  static void encode(EncodeState state, byte[] bytes) {
    state.add(BYTES_CODE).addNullEscaped(bytes).add(nil);
  }

  static void encode(EncodeState state, String s) {
    StringUtil.validate(s);
    byte[] bytes = s.getBytes(UTF8);
    state.add(STRING_CODE).addNullEscaped(bytes).add(nil);
  }

  static void encode(EncodeState state, BigInteger i) {
    //System.out.println("Encoding integral " + i);
    if (i.equals(BigInteger.ZERO)) {
      state.add(INT_ZERO_CODE);
      return;
    }
    int n = minimalByteCount(i);
    if (n > 0xff) {
      throw new IllegalArgumentException("BigInteger magnitude is too large (more than 255 bytes)");
    }
    if (i.compareTo(BigInteger.ZERO) > 0) {
      byte[] bytes = i.toByteArray();
      if (n > Long.BYTES) {
        state.add(POS_INT_END);
        state.add((byte) n);
        state.add(bytes, bytes.length - n, n);
      } else {
        state.add((byte) (INT_ZERO_CODE + n));
        state.add(bytes, bytes.length - n, n);
      }
    } else {
      byte[] bytes = i.subtract(BigInteger.ONE).toByteArray();
      if (n > Long.BYTES) {
        state.add(NEG_INT_START);
        state.add((byte) (n ^ 0xff));
        if (bytes.length >= n) {
          state.add(bytes, bytes.length - n, n);
        } else {
          for (int x = 0; x < n - bytes.length; x++) {
            state.add((byte) 0x00);
          }
          state.add(bytes, 0, bytes.length);
        }
      } else {
        state.add((byte) (INT_ZERO_CODE - n));
        if (bytes.length >= n) {
          state.add(bytes, bytes.length - n, n);
        } else {
          for (int x = 0; x < n - bytes.length; x++) {
            state.add((byte) 0x00);
          }
          state.add(bytes, 0, bytes.length);
        }
      }
    }
  }

  static void encode(EncodeState state, long i) {
    if (i == 0L) {
      state.add(INT_ZERO_CODE);
      return;
    }
    int n = minimalByteCount(i);
    // First byte encodes number of bytes (as difference from INT_ZERO_CODE)
    state.add((byte) (INT_ZERO_CODE + (i >= 0 ? n : -n)));
    // For positive integers, copy the bytes in big-endian order excluding leading 0x00 bytes.
    // For negative integers, copy the bytes of the one's complement representation excluding
    // the leading 0xff bytes. As Java stores negative values in two's complement, we subtract 1
    // from negative values.
    long val = Long.reverseBytes((i >= 0) ? i : (i - 1)) >> (Long.SIZE - 8 * n);
    for (int x = 0; x < n; x++) {
      state.add((byte) (val & 0xff));
      val >>= 8;
    }
  }

  static void encode(EncodeState state, Float f) {
    state.add(FLOAT_CODE).add(encodeFloatBits(f));
  }

  static void encode(EncodeState state, Double d) {
    state.add(DOUBLE_CODE).add(encodeDoubleBits(d));
  }

  static void encode(EncodeState state, Boolean b) {
    state.add(b ? TRUE_CODE : FALSE_CODE);
  }

  static void encode(EncodeState state, UUID uuid) {
    state.add(UUID_CODE).add(uuid.getMostSignificantBits()).add(uuid.getLeastSignificantBits());
  }

  static void encode(EncodeState state, List<?> value) {
    state.add(NESTED_CODE);
    for (Object t : value) {
      encode(state, t, true);
    }
    state.add(nil);
  }

  static void encode(EncodeState state, Tuple value) {
    encode(state, value.elements);
  }


  static void decode(DecodeState state, ByteBuffer buffer, int baseOffset) {
    // SOMEDAY: codes over 127 will be a problem with the signed Java byte mess
    int code = buffer.get(baseOffset);
    int start = baseOffset + 1;
    if (code == nil) {
      state.add(null, start);
    } else if (code == BYTES_CODE) {
      int end = state.findNullTerminator(buffer, start, buffer.limit());
      //System.out.println("End of byte string: " + end);
      byte[] range;
      if (state.nullCount == 0) {
        range = new byte[end - start];
        // Get slice from buffer
        buffer.position(start);
        buffer.get(range, 0, end - start);
      } else {
        ByteBuffer dest = ByteBuffer.allocate(end - start - state.nullCount);
        // Need to copy data to temp array for ByteArrayUtil.replace
        byte[] temp = new byte[end - start];
        buffer.position(start);
        buffer.get(temp);
        ByteArrayUtil.replace(temp, 0, temp.length, NULL_ESCAPED_ARR, NULL_ARR, dest);
        range = dest.array();
      }
      //System.out.println(" -> byte string contents: '" + ArrayUtils.printable(range) + "'");
      state.add(range, end + 1);
    } else if (code == STRING_CODE) {
      int end = state.findNullTerminator(buffer, start, buffer.limit());
      //System.out.println("End of UTF8 string: " + end);
      String str;
      ByteBuffer byteBuffer;
      if (state.nullCount == 0) {
        // Create a slice of the buffer for the string data
        buffer.position(start);
        byteBuffer = buffer.slice();
        byteBuffer.limit(end - start);
      } else {
        byteBuffer = ByteBuffer.allocate(end - start - state.nullCount);
        // Need to copy data to temp array for ByteArrayUtil.replace
        byte[] temp = new byte[end - start];
        buffer.position(start);
        buffer.get(temp);
        ByteArrayUtil.replace(temp, 0, temp.length, NULL_ESCAPED_ARR, NULL_ARR, byteBuffer);
        byteBuffer.position(0);
      }
      try {
        CharsetDecoder decoder = UTF8.newDecoder().onMalformedInput(CodingErrorAction.REPORT);
        str = decoder.decode(byteBuffer).toString();
      } catch (CharacterCodingException e) {
        throw new IllegalArgumentException("malformed UTF-8 string", e);
      }
      //System.out.println(" -> UTF8 string contents: '" + str + "'");
      state.add(str, end + 1);
    } else if (code == FLOAT_CODE) {
      buffer.position(start);
      int rawFloatBits = buffer.getInt();
      float res = decodeFloatBits(rawFloatBits);
      state.add(res, start + Float.BYTES);
    } else if (code == DOUBLE_CODE) {
      buffer.position(start);
      long rawDoubleBits = buffer.getLong();
      double res = decodeDoubleBits(rawDoubleBits);
      state.add(res, start + Double.BYTES);
    } else if (code == FALSE_CODE) {
      state.add(false, start);
    } else if (code == TRUE_CODE) {
      state.add(true, start);
    } else if (code == UUID_CODE) {
      buffer.position(start);
      ByteOrder originalOrder = buffer.order();
      buffer.order(ByteOrder.BIG_ENDIAN);
      long msb = buffer.getLong();
      long lsb = buffer.getLong();
      buffer.order(originalOrder);
      state.add(new UUID(msb, lsb), start + UUID_BYTES);
    } else if (code == POS_INT_END) {
      int n = buffer.get(start) & 0xff;
      byte[] intBytes = new byte[n + 1];
      buffer.position(start + 1);
      buffer.get(intBytes, 1, n);
      BigInteger res = new BigInteger(intBytes);
      state.add(res, start + n + 1);
    } else if (code == NEG_INT_START) {
      int n = (buffer.get(start) ^ 0xff) & 0xff;
      byte[] intBytes = new byte[n + 1];
      buffer.position(start + 1);
      buffer.get(intBytes, 1, n);
      BigInteger origValue = new BigInteger(intBytes);
      BigInteger offset = BigInteger.ONE.shiftLeft(n * 8).subtract(BigInteger.ONE);
      state.add(origValue.subtract(offset), start + n + 1);
    } else if (code > NEG_INT_START && code < POS_INT_END) {
      // decode a long
      boolean positive = code >= INT_ZERO_CODE;
      int n = positive ? code - INT_ZERO_CODE : INT_ZERO_CODE - code;
      int end = start + n;

      if (buffer.limit() < end) {
        throw new IllegalArgumentException("Invalid tuple (possible truncation)");
      }

      if (positive && (n < Long.BYTES || buffer.get(start) > 0)) {
        long res = 0L;
        for (int i = start; i < end; i++) {
          res = (res << 8) | (buffer.get(i) & 0xff);
        }
        state.add(res, end);
      } else if (!positive && (n < Long.BYTES || buffer.get(start) < 0)) {
        long res = ~0L;
        for (int i = start; i < end; i++) {
          res = (res << 8) | (buffer.get(i) & 0xff);
        }
        state.add(res + 1, end);
      } else {
        byte[] longBytes = new byte[9];
        buffer.position(start);
        buffer.get(longBytes, longBytes.length - n, n);
        if (!positive) {
          for (int i = longBytes.length - n; i < longBytes.length; i++) {
            longBytes[i] = (byte) (longBytes[i] ^ 0xff);
          }
        }

        BigInteger val = new BigInteger(longBytes);
        if (!positive) {
          val = val.negate();
        }

        // Convert to long if in range -- otherwise, leave as BigInteger.
        if (val.compareTo(LONG_MIN_VALUE) >= 0 && val.compareTo(LONG_MAX_VALUE) <= 0) {
          state.add(val.longValue(), end);
        } else {
          // This can occur if the thing can be represented with 8 bytes but requires using
          // the most-significant bit as a normal bit instead of the sign bit.
          state.add(val, end);
        }
      }
    } else if (code == NESTED_CODE) {
      DecodeState subResult = new DecodeState();
      int endPos = start;
      boolean foundEnd = false;
      while (endPos < buffer.limit()) {
        if (buffer.get(endPos) == nil) {
          if (endPos + 1 < buffer.limit() && buffer.get(endPos + 1) == (byte) 0xff) {
            subResult.add(null, endPos + 2);
            endPos += 2;
          } else {
            endPos += 1;
            foundEnd = true;
            break;
          }
        } else {
          decode(subResult, buffer, endPos);
          endPos = subResult.end;
        }
      }
      if (!foundEnd) {
        throw new IllegalArgumentException("No terminator found for nested tuple starting at " + start);
      }
      state.add(subResult.values, endPos);
    } else {
      throw new IllegalArgumentException("Unknown tuple data type " + code + " at index " + baseOffset);
    }
  }

  static int compareItems(Object item1, Object item2) {
    if (item1 == item2) {
      // If we have pointer equality, just return 0 immediately.
      return 0;
    }
    int code1 = TupleUtil.getCodeFor(item1);
    int code2 = TupleUtil.getCodeFor(item2);

    if (code1 != code2) {
      return Integer.compare(code1, code2);
    }

    if (code1 == nil) {
      // All null's are equal. (Some may be more equal than others.)
      return 0;
    }
    if (code1 == BYTES_CODE) {
      return ByteArrayUtil.compareUnsigned((byte[]) item1, (byte[]) item2);
    }
    if (code1 == STRING_CODE) {
      return StringUtil.compareUtf8((String) item1, (String) item2);
    }
    if (code1 == INT_ZERO_CODE) {
      if (item1 instanceof Long && item2 instanceof Long) {
        // This should be the common case, so it's probably worth including as a way out.
        return Long.compare((Long) item1, (Long) item2);
      } else {
        BigInteger bi1;
        if (item1 instanceof BigInteger) {
          bi1 = (BigInteger) item1;
        } else {
          bi1 = BigInteger.valueOf(((Number) item1).longValue());
        }
        BigInteger bi2;
        if (item2 instanceof BigInteger) {
          bi2 = (BigInteger) item2;
        } else {
          bi2 = BigInteger.valueOf(((Number) item2).longValue());
        }
        return bi1.compareTo(bi2);
      }
    }
    if (code1 == FLOAT_CODE) {
      // This is done over vanilla float comparison basically to handle NaNs
      // sorting correctly.
      int fbits1 = encodeFloatBits((Float) item1);
      int fbits2 = encodeFloatBits((Float) item2);
      return Integer.compareUnsigned(fbits1, fbits2);
    }
    if (code1 == DOUBLE_CODE) {
      // This is done over vanilla double comparison basically to handle NaNs
      // sorting correctly.
      long dbits1 = encodeDoubleBits((Double) item1);
      long dbits2 = encodeDoubleBits((Double) item2);
      return Long.compareUnsigned(dbits1, dbits2);
    }
    if (code1 == FALSE_CODE) {
      return Boolean.compare((Boolean) item1, (Boolean) item2);
    }
    if (code1 == UUID_CODE) {
      // Java UUID.compareTo is signed, so we have to used the unsigned methods.
      UUID uuid1 = (UUID) item1;
      UUID uuid2 = (UUID) item2;
      int cmp1 = Long.compareUnsigned(uuid1.getMostSignificantBits(), uuid2.getMostSignificantBits());
      if (cmp1 != 0) {
        return cmp1;
      }
      return Long.compareUnsigned(uuid1.getLeastSignificantBits(), uuid2.getLeastSignificantBits());
    }
    if (code1 == NESTED_CODE) {
      return iterableComparator.compare((Iterable<?>) item1, (Iterable<?>) item2);
    }
    throw new IllegalArgumentException("Unknown tuple data type: " + item1.getClass());
  }

  static List<Object> unpack(byte[] bytes) {
    return unpack(ByteBuffer.wrap(bytes));
  }

  static List<Object> unpack(ByteBuffer buffer) {
    try {
      DecodeState decodeState = new DecodeState();
      int originalPosition = buffer.position();
      int pos = originalPosition;

      while (pos < buffer.limit()) {
        decode(decodeState, buffer, pos);
        pos = decodeState.end;
      }

      // Advance buffer position by the amount we consumed
      buffer.position(pos);

      return decodeState.values;
    } catch (IndexOutOfBoundsException | BufferOverflowException e) {
      throw new IllegalArgumentException("Invalid tuple (possible truncation)", e);
    }
  }

  /**
   * Unpacks a single item from the given ByteBuffer and advances its position.
   * This is more efficient than unpacking all items when only the first is needed.
   *
   * @param buffer the buffer to read from. Position will be advanced past the read item.
   * @return the first item unpacked from the buffer
   * @throws IllegalArgumentException if the buffer doesn't contain valid tuple data
   */
  static Object unpackSingleItem(ByteBuffer buffer) {
    if (!buffer.hasRemaining()) {
      throw new IllegalArgumentException("No data to read from buffer");
    }

    try {
      DecodeState decodeState = new DecodeState();
      int originalPosition = buffer.position();

      decode(decodeState, buffer, originalPosition);

      if (decodeState.values.isEmpty()) {
        throw new IllegalArgumentException("No data to read from buffer");
      }

      // Advance buffer position by the amount we consumed for the first item
      buffer.position(decodeState.end);

      return decodeState.values.getFirst();
    } catch (IndexOutOfBoundsException | BufferOverflowException e) {
      throw new IllegalArgumentException("Invalid tuple (possible truncation)", e);
    }
  }

  static void encodeAll(EncodeState state, List<Object> items) {
    for (Object t : items) {
      encode(state, t);
    }
  }

  static void pack(ByteBuffer dest, List<Object> items) {
    ByteOrder origOrder = dest.order();
    EncodeState state = new EncodeState(dest);
    encodeAll(state, items);
    dest.order(origOrder);
  }

  static byte[] pack(List<Object> items, int expectedSize) {
    ByteBuffer dest = ByteBuffer.allocate(expectedSize);
    pack(dest, items);
    return dest.array();
  }

  static int getPackedSize(List<?> items, boolean nested) {
    int packedSize = 0;
    for (Object item : items) {
      if (item == null) {
        packedSize += nested ? 2 : 1;
      } else if (item instanceof byte[] bytes) {
        packedSize += 2 + bytes.length + ByteArrayUtil.nullCount(bytes);
      } else if (item instanceof String) {
        int strPackedSize = StringUtil.packedSize((String) item);
        packedSize += 2 + strPackedSize;
      } else if (item instanceof Float) {
        packedSize += 1 + Float.BYTES;
      } else if (item instanceof Double) {
        packedSize += 1 + Double.BYTES;
      } else if (item instanceof Boolean) {
        packedSize += 1;
      } else if (item instanceof UUID) {
        packedSize += 1 + UUID_BYTES;
      } else if (item instanceof BigInteger bigInt) {
        int byteCount = minimalByteCount(bigInt);
        // If byteCount <= 8, then the encoding uses 1 byte for both the size
        // and type code. If byteCount > 8, then there is 1 byte for the type code
        // and 1 byte for the length. In both cases, the value is followed by
        // the byte count.
        packedSize += byteCount + ((byteCount <= 8) ? 1 : 2);
      } else if (item instanceof Character) {
        packedSize += 1 + minimalByteCount((long) ((Character) item).charValue());
      } else if (item instanceof Number) {
        packedSize += 1 + minimalByteCount(((Number) item).longValue());
      } else if (item instanceof List<?>) {
        packedSize += 2 + getPackedSize((List<?>) item, true);
      } else if (item instanceof Tuple) {
        packedSize += 2 + ((Tuple) item).getPackedSize(true);
      } else {
        throw new IllegalArgumentException("unknown type " + item.getClass() + " for tuple packing");
      }
    }
    return packedSize;
  }

  private TupleUtil() {
  }
}
