/*
 * Tuple.java
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a set of elements that make up a sortable, typed key. This object
 * is comparable with other {@code Tuple}s and maintains lexicographic ordering
 * when serialized to bytes. {@code Tuple}s sort first by the first element,
 * then by the second, etc. This makes tuples ideal for building a variety of
 * higher-level data models and composite keys.<br>
 * <h2>Types</h2>
 * A {@code Tuple} can
 * contain byte arrays ({@code byte[]}), {@link String}s, {@link Number}s, {@link UUID}s,
 * {@code boolean}s, {@link Character}s, {@link List}s, other {@code Tuple}s, and {@code null}.
 * {@link Float} and {@link Double} instances will be serialized as single- and double-precision
 * numbers respectively, and {@link BigInteger}s within the range [{@code -2^2040+1},
 * {@code 2^2040-1}] are serialized without loss of precision (those outside the range
 * will raise an {@link IllegalArgumentException}). {@link Character}s are stored as their
 * numeric value (0-65535). All other {@code Number}s will be converted to
 * a {@code long} integral value, so the range will be constrained to
 * [{@code -2^63}, {@code 2^63-1}]. Note that for numbers outside this range the way that Java
 * truncates integral values may yield unexpected results.<br>
 * <h2>{@code null} values</h2>
 * The tuple encoding specification has a special type-code for {@code null} values.
 * The behavior of the tuple in the presence of {@code null} varies by type with the intention
 * of matching expected behavior in Java. {@code byte[]}, {@link String}s, {@link UUID}s, and
 * nested {@link List}s and {@code Tuple}s can be {@code null},
 * whereas numbers (e.g., {@code long}s and {@code double}s) and booleans cannot.
 * This means that the typed getters ({@link #getBytes(int) getBytes()}, {@link #getString(int) getString()}),
 * {@link #getUUID(int) getUUID()}, {@link #getNestedTuple(int) getNestedTuple()},
 * and {@link #getNestedList(int) getNestedList()}) will return {@code null} if the entry at that location was
 * {@code null} and the typed adds ({@link #add(byte[])}, {@link #add(String)},
 * {@link #add(Tuple)}, and {@link #add(List)}) will accept {@code null}. The
 * {@link #getLong(int) typed get for integers} and other typed getters, however, will throw a
 * {@link NullPointerException} if the entry in the {@code Tuple} was {@code null} at that position.<br>
 * <br>
 * This class is not thread safe.
 */
public class Tuple implements Comparable<Tuple>, Iterable<Object> {
  private static final IterableComparator comparator = new IterableComparator();
  private static final byte[] EMPTY_BYTES = new byte[0];

  List<Object> elements;
  private byte[] packed = null;
  private int memoizedHash = 0;
  private int memoizedPackedSize = -1;

  private Tuple(Tuple original, Object newItem) {
    this.elements = new ArrayList<>(original.elements.size() + 1);
    this.elements.addAll(original.elements);
    this.elements.add(newItem);
  }

  private Tuple(List<Object> elements) {
    this.elements = elements;
  }

  /**
   * Construct a new empty {@code Tuple}. After creation, items can be added
   * with calls to the variations of {@code add()}.
   *
   * @see #from(Object...)
   * @see #fromBytes(byte[])
   * @see #fromItems(Iterable)
   */
  public Tuple() {
    elements = Collections.emptyList();
    packed = EMPTY_BYTES;
    memoizedPackedSize = 0;
  }

  /**
   * Creates a copy of this {@code Tuple} with an appended last element. The
   * parameter is untyped but only {@link String}, {@code byte[]},
   * {@link Number}s, {@link UUID}s, {@link Boolean}s, {@link Character}s,
   * {@link List}s, {@code Tuple}s, and {@code null} are allowed. If an object
   * of another type is passed, then an {@link IllegalArgumentException} is thrown.
   *
   * @param o the object to append. Must be {@link String}, {@code byte[]},
   *          {@link Number}s, {@link UUID}, {@link List}, {@link Boolean},
   *          {@link Character}, or {@code null}.
   * @return a newly created {@code Tuple}
   */
  public Tuple addObject(Object o) {
    return switch (o) {
      case String s -> add(s);
      case byte[] bytes -> add(bytes);
      case UUID uuid -> add(uuid);
      case List<?> objects -> add(objects);
      case Tuple objects -> add(objects);
      case Boolean b -> add(b);
      case Character c -> add(c);
      case Number ignored -> new Tuple(this, o);
      case null -> new Tuple(this, o);
      default -> throw new IllegalArgumentException("Parameter type (" + o.getClass().getName() + ") not recognized");
    };
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@code String} appended as the last element.
   *
   * @param s the {@code String} to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(String s) {
    return new Tuple(this, s);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@code long} appended as the last element.
   *
   * @param l the number to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(long l) {
    return new Tuple(this, l);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@code byte} array appended as the last element.
   *
   * @param b the {@code byte}s to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(byte[] b) {
    return new Tuple(this, b);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@code boolean} appended as the last element.
   *
   * @param b the {@code boolean} to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(boolean b) {
    return new Tuple(this, b);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@link UUID} appended as the last element.
   *
   * @param uuid the {@link UUID} to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(UUID uuid) {
    return new Tuple(this, uuid);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@link BigInteger} appended as the last element.
   * As {@link Tuple}s cannot contain {@code null} numeric types, a {@link NullPointerException}
   * is raised if a {@code null} argument is passed.
   *
   * @param bi the {@link BigInteger} to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(BigInteger bi) {
    if (bi == null) {
      throw new NullPointerException("Number types in Tuple cannot be null");
    }
    return new Tuple(this, bi);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@code float} appended as the last element.
   *
   * @param f the {@code float} to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(float f) {
    return new Tuple(this, f);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@code double} appended as the last element.
   *
   * @param d the {@code double} to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(double d) {
    return new Tuple(this, d);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@code char} appended as the last element.
   * The char is stored as its numeric value (0-65535).
   *
   * @param c the {@code char} to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(char c) {
    return new Tuple(this, (long) c);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@code byte} appended as the last element.
   *
   * @param b the {@code byte} to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(byte b) {
    return new Tuple(this, (long) b);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@link List} appended as the last element.
   * This does not add the elements individually (for that, use {@link Tuple#addAll(List) Tuple.addAll}).
   * This adds the list as a single element nested within the outer {@code Tuple}.
   *
   * @param l the {@link List} to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(List<?> l) {
    return new Tuple(this, l);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@code Tuple} appended as the last element.
   * This does not add the elements individually (for that, use {@link Tuple#addAll(Tuple) Tuple.addAll}).
   * This adds the list as a single element nested within the outer {@code Tuple}.
   *
   * @param t the {@code Tuple} to append
   * @return a newly created {@code Tuple}
   */
  public Tuple add(Tuple t) {
    return new Tuple(this, t);
  }

  /**
   * Creates a copy of this {@code Tuple} with a {@code byte} array appended as the last element.
   *
   * @param b      the {@code byte}s to append
   * @param offset the starting index of {@code b} to add
   * @param length the number of elements of {@code b} to copy into this {@code Tuple}
   * @return a newly created {@code Tuple}
   */
  public Tuple add(byte[] b, int offset, int length) {
    return new Tuple(this, Arrays.copyOfRange(b, offset, offset + length));
  }

  /**
   * Create a copy of this {@code Tuple} with a list of items appended.
   *
   * @param o the list of objects to append. Elements must be {@link String}, {@code byte[]},
   *          {@link Number}s, or {@code null}.
   * @return a newly created {@code Tuple}
   */
  public Tuple addAll(List<?> o) {
    List<Object> merged = new ArrayList<>(o.size() + this.elements.size());
    merged.addAll(this.elements);
    merged.addAll(o);
    return new Tuple(merged);
  }

  /**
   * Create a copy of this {@code Tuple} with all elements from anther {@code Tuple} appended.
   *
   * @param other the {@code Tuple} whose elements should be appended
   * @return a newly created {@code Tuple}
   */
  public Tuple addAll(Tuple other) {
    List<Object> merged = new ArrayList<>(this.size() + other.size());
    merged.addAll(this.elements);
    merged.addAll(other.elements);
    Tuple t = new Tuple(merged);
    if (packed != null && other.packed != null) {
      t.packed = ByteArrayUtil.join(packed, other.packed);
    }
    if (memoizedPackedSize >= 0 && other.memoizedPackedSize >= 0) {
      t.memoizedPackedSize = memoizedPackedSize + other.memoizedPackedSize;
    }
    return t;
  }

  /**
   * Get an encoded representation of this {@code Tuple}. Each element is encoded to
   * {@code byte}s and concatenated. Note that once a {@code Tuple} has been packed, its
   * serialized representation is stored internally so that future calls to this function
   * are faster than the initial call.
   *
   * @return a packed representation of this {@code Tuple}
   */
  public byte[] pack() {
    return packInternal(null, true);
  }

  /**
   * Get an encoded representation of this {@code Tuple}. Each element is encoded to
   * {@code byte}s and concatenated, and then the prefix supplied is prepended to
   * the array. Note that once a {@code Tuple} has been packed, its serialized representation
   * is stored internally so that future calls to this function are faster than the
   * initial call.
   *
   * @param prefix additional byte-array prefix to prepend to the packed bytes
   * @return a packed representation of this {@code Tuple} prepended by the {@code prefix}
   */
  public byte[] pack(byte[] prefix) {
    return packInternal(prefix, true);
  }

  byte[] packInternal(byte[] prefix, boolean copy) {
    if (packed == null) {
      packed = TupleUtil.pack(elements, getPackedSize());
    }
    boolean hasPrefix = prefix != null && prefix.length > 0;
    if (hasPrefix) {
      return ByteArrayUtil.join(prefix, packed);
    } else if (copy) {
      return Arrays.copyOf(packed, packed.length);
    } else {
      return packed;
    }
  }

  /**
   * Pack an encoded representation of this {@code Tuple} onto the end of the given {@link ByteBuffer}.
   * It is up to the caller to ensure that there is enough space allocated within the buffer
   * to avoid {@link java.nio.BufferOverflowException}s. The client may call {@link #getPackedSize()}
   * to determine how large this {@code Tuple} will be once packed in order to allocate sufficient memory.
   * Note that unlike {@link #pack()}, the serialized representation of this {@code Tuple} is not stored, so
   * calling this function multiple times with the same {@code Tuple} requires serializing the {@code Tuple}
   * multiple times.
   *
   * @param dest the destination {@link ByteBuffer} for the encoded {@code Tuple}
   */
  public void packInto(ByteBuffer dest) {
    if (packed == null) {
      TupleUtil.pack(dest, elements);
    } else {
      dest.put(packed);
    }
  }

  byte[] packMaybe() {
    if (packed == null) {
      return packInternal(null, false);
    } else {
      return packed;
    }
  }

  /**
   * Gets the unserialized contents of this {@code Tuple}.
   *
   * @return the elements that make up this {@code Tuple}.
   */
  public List<Object> getItems() {
    return new ArrayList<>(elements);
  }

  /**
   * Gets the unserialized contents of this {@code Tuple} without copying into a new list.
   *
   * @return the elements that make up this {@code Tuple}.
   */
  List<Object> getRawItems() {
    return elements;
  }

  /**
   * Gets a {@link Stream} of the unserialized contents of this {@code Tuple}.
   *
   * @return a {@link Stream} of the elements that make up this {@code Tuple}.
   */
  public Stream<Object> stream() {
    return elements.stream();
  }

  /**
   * Gets an {@code Iterator} over the {@code Objects} in this {@code Tuple}. This {@code Iterator} is
   * unmodifiable and will throw an exception if {@link Iterator#remove() remove()} is called.
   *
   * @return an unmodifiable {@code Iterator} over the elements in the {@code Tuple}.
   */
  @Override
  public Iterator<Object> iterator() {
    return Collections.unmodifiableList(this.elements).iterator();
  }

  /**
   * Construct a new {@code Tuple} with elements decoded from a supplied {@code byte} array.
   * The passed byte array must not be {@code null}. This will throw an exception if the passed byte
   * array does not represent a valid {@code Tuple}. For example, this will throw an error if it
   * encounters an unknown type code or if there is a packed element that appears to be truncated.
   *
   * @param bytes encoded {@code Tuple} source
   * @return a new {@code Tuple} constructed by deserializing the provided {@code byte} array
   * @throws IllegalArgumentException if {@code bytes} does not represent a valid {@code Tuple}
   */
  public static Tuple fromBytes(byte[] bytes) {
    return fromBytes(bytes, 0, bytes.length);
  }

  /**
   * Construct a new {@code Tuple} with elements decoded from a supplied {@code byte} array.
   * The passed byte array must not be {@code null}. This will throw an exception if the specified slice of
   * the passed byte array does not represent a valid {@code Tuple}. For example, this will throw an error
   * if it encounters an unknown type code or if there is a packed element that appears to be truncated.
   *
   * @param bytes  encoded {@code Tuple} source
   * @param offset starting offset of byte array of encoded data
   * @param length length of encoded data within the source
   * @return a new {@code Tuple} constructed by deserializing the specified slice of the provided {@code byte} array
   * @throws IllegalArgumentException if {@code offset} or {@code length} are negative or would exceed the size of
   *                                  the array or if {@code bytes} does not represent a valid {@code Tuple}
   */
  public static Tuple fromBytes(byte[] bytes, int offset, int length) {
    if (offset < 0 || offset > bytes.length) {
      throw new IllegalArgumentException("Invalid offset for Tuple deserialization");
    }
    if (length < 0 || offset + length > bytes.length) {
      throw new IllegalArgumentException("Invalid length for Tuple deserialization");
    }
    byte[] packed = Arrays.copyOfRange(bytes, offset, offset + length);
    Tuple t = new Tuple(TupleUtil.unpack(packed));
    t.packed = packed;
    t.memoizedPackedSize = length;
    return t;
  }

  /**
   * Gets the number of elements in this {@code Tuple}.
   *
   * @return the number of elements in this {@code Tuple}
   */
  public int size() {
    return this.elements.size();
  }

  /**
   * Determine if this {@code Tuple} contains no elements.
   *
   * @return {@code true} if this {@code Tuple} contains no elements, {@code false} otherwise
   */
  public boolean isEmpty() {
    return this.elements.isEmpty();
  }

  /**
   * Gets an indexed item as a {@code long}. This function will not do type conversion
   * and so will throw a {@code ClassCastException} if the element is not a number type.
   * The element at the index may not be {@code null}.
   *
   * @param index the location of the item to return
   * @return the item at {@code index} as a {@code long}
   * @throws ClassCastException   if the element at {@code index} is not a {@link Number}
   * @throws NullPointerException if the element at {@code index} is {@code null}
   */
  public long getLong(int index) {
    Object o = this.elements.get(index);
    if (o == null) {
      throw new NullPointerException("Number types in Tuples may not be null");
    }
    return ((Number) o).longValue();
  }

  /**
   * Gets an indexed item as a {@code byte[]}. This function will not do type conversion
   * and so will throw a {@code ClassCastException} if the tuple element is not a
   * {@code byte} array.
   *
   * @param index the location of the element to return
   * @return the item at {@code index} as a {@code byte[]}
   * @throws ClassCastException if the element at {@code index} is not a {@link Number}
   */
  public byte[] getBytes(int index) {
    Object o = this.elements.get(index);
    // Check needed, since the null may be of type "Object" and may not be casted to byte[]
    if (o == null) {
      return null;
    }
    return (byte[]) o;
  }

  /**
   * Gets an indexed item as a {@code String}. This function will not do type conversion
   * and so will throw a {@code ClassCastException} if the tuple element is not of
   * {@code String} type.
   *
   * @param index the location of the element to return
   * @return the item at {@code index} as a {@code String}
   * @throws ClassCastException if the element at {@code index} is not a {@link String}
   */
  public String getString(int index) {
    Object o = this.elements.get(index);
    // Check needed, since the null may be of type "Object" and may not be casted to byte[]
    if (o == null) {
      return null;
    }
    return (String) o;
  }

  /**
   * Gets an indexed item as a {@link BigInteger}. This function will not do type conversion
   * and so will throw a {@code ClassCastException} if the tuple element is not of
   * a {@code Number} type. If the underlying type is a floating point value, this
   * will lead to a loss of precision. The element at the index may not be {@code null}.
   *
   * @param index the location of the element to return
   * @return the item at {@code index} as a {@link BigInteger}
   * @throws ClassCastException if the element at {@code index} is not a {@link Number}
   */
  public BigInteger getBigInteger(int index) {
    Object o = this.elements.get(index);
    if (o == null) {
      throw new NullPointerException("Number types in Tuples may not be null");
    }
    if (o instanceof BigInteger) {
      return (BigInteger) o;
    } else {
      return BigInteger.valueOf(((Number) o).longValue());
    }
  }

  /**
   * Gets an indexed item as a {@code float}. This function will not do type conversion
   * and so will throw a {@code ClassCastException} if the element is not a number type.
   * The element at the index may not be {@code null}.
   *
   * @param index the location of the item to return
   * @return the item at {@code index} as a {@code float}
   * @throws ClassCastException if the element at {@code index} is not a {@link Number}
   */
  public float getFloat(int index) {
    Object o = this.elements.get(index);
    if (o == null) {
      throw new NullPointerException("Number types in Tuples may not be null");
    }
    return ((Number) o).floatValue();
  }

  /**
   * Gets an indexed item as a {@code double}. This function will not do type conversion
   * and so will throw a {@code ClassCastException} if the element is not a number type.
   * The element at the index may not be {@code null}.
   *
   * @param index the location of the item to return
   * @return the item at {@code index} as a {@code double}
   * @throws ClassCastException if the element at {@code index} is not a {@link Number}
   */
  public double getDouble(int index) {
    Object o = this.elements.get(index);
    if (o == null) {
      throw new NullPointerException("Number types in Tuples may not be null");
    }
    return ((Number) o).doubleValue();
  }

  /**
   * Gets an indexed item as a {@code boolean}. This function will not do type conversion
   * and so will throw a {@code ClassCastException} if the element is not a {@code Boolean}.
   * The element at the index may not be {@code null}.
   *
   * @param index the location of the item to return
   * @return the item at {@code index} as a {@code boolean}
   * @throws ClassCastException   if the element at {@code index} is not a {@link Boolean}
   * @throws NullPointerException if the element at {@code index} is {@code null}
   */
  public boolean getBoolean(int index) {
    Object o = this.elements.get(index);
    if (o == null) {
      throw new NullPointerException("Boolean type in Tuples may not be null");
    }
    return (Boolean) o;

  }

  /**
   * Gets an indexed item as a {@link UUID}. This function will not do type conversion
   * and so will throw a {@code ClassCastException} if the element is not a {@code UUID}.
   * The element at the index may be {@code null}.
   *
   * @param index the location of the item to return
   * @return the item at {@code index} as a {@link UUID}
   * @throws ClassCastException if the element at {@code index} is not a {@link UUID}
   */
  public UUID getUUID(int index) {
    Object o = this.elements.get(index);
    if (o == null) {
      return null;
    }
    return (UUID) o;
  }

  /**
   * Gets an indexed item as a {@link List}. This function will not do type conversion
   * and so will throw a {@code ClassCastException} if the element is not a {@link List}
   * or {@code Tuple}. The element at the index may be {@code null}.
   *
   * @param index the location of the item to return
   * @return the item at {@code index} as a {@link List}
   * @throws ClassCastException if the element at {@code index} is not a {@link List}
   *                            or a {@code Tuple}
   */
  public List<Object> getNestedList(int index) {
    Object o = this.elements.get(index);
    if (o == null) {
      return null;
    } else if (o instanceof Tuple) {
      return ((Tuple) o).getItems();
    } else if (o instanceof List<?>) {
      return new ArrayList<>((List<?>) o);
    } else {
      throw new ClassCastException("Cannot convert item of type " + o.getClass() + " to list");
    }
  }

  /**
   * Gets an indexed item as a {@link Tuple}. This function will not do type conversion
   * and so will throw a {@code ClassCastException} if the element is not a {@link List}
   * or {@code Tuple}. The element at the index may be {@code null}.
   *
   * @param index the location of the item to return
   * @return the item at {@code index} as a {@link List}
   * @throws ClassCastException if the element at {@code index} is not a {@code Tuple}
   *                            or a {@link List}
   */
  public Tuple getNestedTuple(int index) {
    Object o = this.elements.get(index);
    if (o == null) {
      return null;
    } else if (o instanceof Tuple) {
      return (Tuple) o;
    } else if (o instanceof List<?>) {
      return Tuple.fromList((List<?>) o);
    } else {
      throw new ClassCastException("Cannot convert item of type " + o.getClass() + " to tuple");
    }
  }

  /**
   * Gets an indexed item without forcing a type.
   *
   * @param index the index of the item to return
   * @return an item from the list, without forcing type conversion
   */
  public Object get(int index) {
    return this.elements.get(index);
  }

  /**
   * Creates a new {@code Tuple} with the first item of this {@code Tuple} removed.
   *
   * @return a newly created {@code Tuple} without the first item of this {@code Tuple}
   * @throws IllegalStateException if this {@code Tuple} is empty
   */
  public Tuple popFront() {
    if (elements.isEmpty()) {
      throw new IllegalStateException("Tuple contains no elements");
    }

    return new Tuple(elements.subList(1, elements.size()));
  }

  /**
   * Creates a new {@code Tuple} with the last item of this {@code Tuple} removed.
   *
   * @return a newly created {@code Tuple} without the last item of this {@code Tuple}
   * @throws IllegalStateException if this {@code Tuple} is empty
   */
  public Tuple popBack() {
    if (elements.isEmpty()) {
      throw new IllegalStateException("Tuple contains no elements");
    }

    return new Tuple(elements.subList(0, elements.size() - 1));
  }

  /**
   * Returns a range representing all keys that encode {@code Tuple}s strictly starting
   * with this {@code Tuple}.
   * <br>
   * <br>
   * For example:
   * <pre>
   *   Tuple t = Tuple.from("a", "b");
   *   Range r = t.range();</pre>
   * {@code r} includes all tuples ("a", "b", ...)
   * <br>
   *
   * @return the range of keys containing all possible keys that have this {@code Tuple}
   * as a strict prefix
   */
  public Range range() {
    return range(null);
  }

  /**
   * Returns a range representing all keys that encode {@code Tuple}s strictly starting
   * with the given prefix followed by this {@code Tuple}.
   * <br>
   * <br>
   * For example:
   * <pre>
   *   Tuple t = Tuple.from("a", "b");
   *   Range r = t.range(Tuple.from("c").pack());</pre>
   * {@code r} contains all tuples ("c", "a", "b", ...)
   * <br>
   *
   * @param prefix a byte prefix to precede all elements in the range
   * @return the range of keys containing all possible keys that have {@code prefix}
   * followed by this {@code Tuple} as a strict prefix
   */
  public Range range(byte[] prefix) {
    byte[] p = packInternal(prefix, false);
    return new Range(ByteArrayUtil.join(p, new byte[]{0x0}),
        ByteArrayUtil.join(p, new byte[]{(byte) 0xff}));
  }

  /**
   * Get the number of bytes in the packed representation of this {@code Tuple}. This is done by summing
   * the serialized sizes of all of the elements of this {@code Tuple} and does not pack everything
   * into a single {@code Tuple}. The return value of this function is stored within this {@code Tuple}
   * after this function has been called so that subsequent calls on the same object are fast.
   *
   * @return the number of bytes in the packed representation of this {@code Tuple}
   */
  public int getPackedSize() {
    if (memoizedPackedSize < 0) {
      memoizedPackedSize = getPackedSize(false);
    }
    return memoizedPackedSize;
  }

  int getPackedSize(boolean nested) {
    if (memoizedPackedSize >= 0) {
      if (!nested) {
        return memoizedPackedSize;
      }
      int nullCount = 0;
      for (Object elem : elements) {
        if (elem == null) {
          nullCount++;
        }
      }
      return memoizedPackedSize + nullCount;
    } else {
      return TupleUtil.getPackedSize(elements, nested);
    }
  }

  /**
   * Compare the byte-array representation of this {@code Tuple} against another. This method
   * will sort {@code Tuple}s in the same order that they would be sorted as keys in
   * FoundationDB. Returns a negative integer, zero, or a positive integer when this object's
   * byte-array representation is found to be less than, equal to, or greater than the
   * specified {@code Tuple}.
   *
   * @param t the {@code Tuple} against which to compare
   * @return a negative integer, zero, or a positive integer when this {@code Tuple} is
   * less than, equal, or greater than the parameter {@code t}.
   */
  @Override
  public int compareTo(Tuple t) {
    // If either tuple has an incomplete versionstamp, then there is a possibility that the byte order
    // is not the semantic comparison order.
    if (packed != null && t.packed != null) {
      return ByteArrayUtil.compareUnsigned(packed, t.packed);
    } else {
      return comparator.compare(elements, t.elements);
    }
  }

  /**
   * Returns a hash code value for this {@code Tuple}. Computing the hash code is fairly expensive
   * as it involves packing the underlying {@code Tuple} to bytes. However, this value is memoized,
   * so for any given {@code Tuple}, it only needs to be computed once. This means that it is
   * generally safe to use {@code Tuple}s with hash-based data structures such as
   * {@link java.util.HashSet HashSet}s or {@link java.util.HashMap HashMap}s.
   * {@inheritDoc}
   *
   * @return a hash code for this {@code Tuple} that can be used by hash tables
   */
  @Override
  public int hashCode() {
    if (memoizedHash == 0) {
      memoizedHash = Arrays.hashCode(packMaybe());
    }
    return memoizedHash;
  }

  /**
   * Tests for equality with another {@code Tuple}. If the passed object is not a {@code Tuple}
   * this returns false. If the object is a {@code Tuple}, this returns true if
   * {@link Tuple#compareTo(Tuple) compareTo()} would return {@code 0}.
   *
   * @return {@code true} if {@code obj} is a {@code Tuple} and their binary representation
   * is identical
   */
  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (o == this) {
      return true;
    }
    if (o instanceof Tuple) {
      return compareTo((Tuple) o) == 0;
    }
    return false;
  }

  /**
   * Returns a string representing this {@code Tuple}. This contains human-readable
   * representations of all of the elements of this {@code Tuple}. For most elements,
   * this means using that object's default string representation. For byte-arrays,
   * this means using {@link ByteArrayUtil#printable(byte[]) ByteArrayUtil.printable()}
   * to produce a byte-string where most printable ASCII code points have been
   * rendered as characters.
   *
   * @return a human-readable {@link String} representation of this {@code Tuple}
   */
  @Override
  public String toString() {
    StringBuilder s = new StringBuilder("(");
    boolean first = true;

    for (Object o : elements) {
      if (!first) {
        s.append(", ");
      }

      first = false;
      if (o == null) {
        s.append("null");
      } else if (o instanceof String) {
        s.append("\"");
        s.append(o);
        s.append("\"");
      } else if (o instanceof byte[]) {
        s.append("b\"");
        s.append(ByteArrayUtil.printable((byte[]) o));
        s.append("\"");
      } else {
        s.append(o);
      }
    }

    s.append(")");
    return s.toString();
  }

  /**
   * Creates a new {@code Tuple} from a variable number of elements. The elements
   * must follow the type guidelines from {@link Tuple#addObject(Object) add}, and so
   * can only be {@link String}s, {@code byte[]}s, {@link Number}s, {@link UUID}s,
   * {@link Boolean}s, {@link List}s, {@code Tuple}s, or {@code null}s.
   *
   * @param items the elements from which to create the {@code Tuple}
   * @return a new {@code Tuple} with the given items as its elements
   */
  public static Tuple fromItems(Iterable<?> items) {
    if (items instanceof List<?>) {
      return Tuple.fromList((List<?>) items);
    }
    List<Object> elements = new ArrayList<>();
    for (Object o : items) {
      elements.add(o);
    }
    return new Tuple(elements);
  }

  /**
   * Efficiently creates a new {@code Tuple} from a list of objects. The elements
   * must follow the type guidelines from {@link Tuple#addObject(Object) add}, and so
   * can only be {@link String}s, {@code byte[]}s, {@link Number}s, {@link UUID}s,
   * {@link Boolean}s, {@link List}s, {@code Tuple}s, or {@code null}s.
   *
   * @param items the elements from which to create the {@code Tuple}.
   * @return a new {@code Tuple} with the given items as its elements
   */
  public static Tuple fromList(List<?> items) {
    List<Object> elements = new ArrayList<>(items);
    return new Tuple(elements);
  }

  /**
   * Efficiently creates a new {@code Tuple} from a {@link Stream} of objects. The
   * elements must follow the type guidelines from {@link Tuple#addObject(Object) add},
   * and so can only be {@link String}s, {@code byte[]}s, {@link Number}s, {@link UUID}s,
   * {@link Boolean}s, {@link List}s, {@code Tuple}s, or {@code null}s. Note that this
   * class will consume all elements from the {@link Stream}.
   *
   * @param items the {@link Stream} of items from which to create the {@code Tuple}
   * @return a new {@code Tuple} with the given items as its elements
   */
  public static Tuple fromStream(Stream<?> items) {
    return new Tuple(items.collect(Collectors.toList()));
  }

  /**
   * Creates a new {@code Tuple} from a variable number of elements. The elements
   * must follow the type guidelines from {@link Tuple#addObject(Object) add}, and so
   * can only be {@link String}s, {@code byte[]}s, {@link Number}s, {@link UUID}s,
   * {@link Boolean}s, {@link List}s, {@code Tuple}s, or {@code null}s.
   *
   * @param items the elements from which to create the {@code Tuple}
   * @return a new {@code Tuple} with the given items as its elements
   */
  public static Tuple from(Object... items) {
    return new Tuple(Arrays.asList(items));
  }

  /**
   * Packs zero or more elements directly into a byte array without creating a {@code Tuple} instance.
   * This is more efficient than creating a {@code Tuple} when you only need the packed representation.
   *
   * <p>Example usage:
   * <pre>{@code
   * byte[] packed = Tuple.packItems("hello", 42L, true);
   * // Equivalent to: new Tuple().add("hello").add(42L).add(true).pack()
   * }</pre>
   *
   * @param items the items to pack. Supports the same types as {@code Tuple}: strings, numbers,
   *              byte arrays, booleans, UUIDs, nested lists/tuples, and null values
   * @return a byte array containing the packed representation of the items
   */
  public static byte[] packItems(Object... items) {
    if (items.length == 0) {
      return EMPTY_BYTES;
    }
    List<Object> itemList = Arrays.asList(items);
    int packedSize = TupleUtil.getPackedSize(itemList, false);
    return TupleUtil.pack(itemList, packedSize);
  }

  /**
   * Packs zero or more elements directly into the provided {@code ByteBuffer} without creating
   * a {@code Tuple} instance. This is more efficient than creating a {@code Tuple} when you
   * only need to write the packed representation to an existing buffer.
   *
   * <p>The buffer's position will be advanced by the number of bytes written.
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.allocate(1024);
   * Tuple.packItemsInto(buffer, "hello", 42L, true);
   * // buffer now contains the packed data and position is advanced
   * }</pre>
   *
   * @param buffer the buffer to write the packed data into. Must have sufficient remaining capacity
   * @param items  the items to pack. Supports the same types as {@code Tuple}: strings, numbers,
   *               byte arrays, booleans, UUIDs, nested lists/tuples, and null values
   * @throws java.nio.BufferOverflowException if the buffer has insufficient remaining capacity
   */
  public static void packItemsInto(ByteBuffer buffer, Object... items) {
    if (items.length == 0) {
      return; // Nothing to pack
    }
    List<Object> itemList = Arrays.asList(items);
    TupleUtil.pack(buffer, itemList);
  }

  /**
   * Calculates the number of bytes required to pack the given items without actually packing them.
   * This is useful for pre-allocating buffers of the correct size.
   *
   * <p>Example usage:
   * <pre>{@code
   * int size = Tuple.getItemsPackedSize("hello", 42L, true);
   * ByteBuffer buffer = ByteBuffer.allocate(size);
   * Tuple.packItemsInto(buffer, "hello", 42L, true);
   * }</pre>
   *
   * @param items the items that would be packed. Supports the same types as {@code Tuple}
   * @return the number of bytes required to pack the items
   */
  public static int getItemsPackedSize(Object... items) {
    if (items.length == 0) {
      return 0;
    }
    return TupleUtil.getPackedSize(Arrays.asList(items), false);
  }

  /**
   * Reads a single string value from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems("hello"));
   * String value = Tuple.readString(buffer);
   * // value equals "hello"
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the string value read from the buffer, or {@code null} if a null value was encoded
   * @throws IllegalArgumentException if the buffer doesn't contain a valid string at the current position
   */
  public static String readString(ByteBuffer buffer) {
    Object value = readSingleItem(buffer);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String)) {
      throw new IllegalArgumentException("Expected string but found: " + value.getClass().getSimpleName());
    }
    return (String) value;
  }

  /**
   * Reads a single long value from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Only accepts integral number types (Long, BigInteger, Integer, etc.).
   * Floating-point types (Float, Double) will cause an exception.
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(42L));
   * long value = Tuple.readLong(buffer);
   * // value equals 42L
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the long value read from the buffer
   * @throws IllegalArgumentException if the buffer doesn't contain a valid integral number at the current position
   * @throws NullPointerException     if a null value was encoded (numbers cannot be null)
   */
  public static long readLong(ByteBuffer buffer) {
    return readSingleIntegralNumber(buffer).longValue();
  }

  /**
   * Reads a single int value from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Only accepts integral number types (Long, BigInteger, Integer, etc.).
   * Floating-point types (Float, Double) will cause an exception.
   * The value must fit within the range of int ({@code -2^31} to {@code 2^31-1}).
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(42L));
   * int value = Tuple.readInt(buffer);
   * // value equals 42
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the int value read from the buffer
   * @throws IllegalArgumentException if the buffer doesn't contain a valid integral number at the current position,
   *                                  or if the value doesn't fit in an int
   * @throws NullPointerException     if a null value was encoded (numbers cannot be null)
   */
  public static int readInt(ByteBuffer buffer) {
    long longValue = readSingleIntegralNumber(buffer).longValue();
    if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Value " + longValue + " cannot fit in an int (range: " +
                                         Integer.MIN_VALUE + " to " + Integer.MAX_VALUE + ")");
    }
    return (int) longValue;
  }

  private static Number readSingleIntegralNumber(ByteBuffer buffer) {
    Object value = readSingleItem(buffer);
    if (value == null) {
      throw new NullPointerException("Number types in Tuples may not be null");
    }
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException("Expected number but found: " + value.getClass().getSimpleName());
    }
    // Only accept integral types, not floating-point types
    if (value instanceof Float || value instanceof Double) {
      throw new IllegalArgumentException("Expected integral number but found floating-point: " +
                                         value.getClass().getSimpleName());
    }
    return (Number) value;
  }

  /**
   * Reads a single short value from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Only accepts integral number types (Long, BigInteger, Integer, etc.).
   * Floating-point types (Float, Double) will cause an exception.
   * The value must fit within the range of short ({@code -32768} to {@code 32767}).
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(42L));
   * short value = Tuple.readShort(buffer);
   * // value equals 42
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the short value read from the buffer
   * @throws IllegalArgumentException if the buffer doesn't contain a valid integral number at the current position,
   *                                  or if the value doesn't fit in a short
   * @throws NullPointerException     if a null value was encoded (numbers cannot be null)
   */
  public static short readShort(ByteBuffer buffer) {
    long longValue = readSingleIntegralNumber(buffer).longValue();
    if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
      throw new IllegalArgumentException("Value " + longValue + " cannot fit in a short (range: " +
                                         Short.MIN_VALUE + " to " + Short.MAX_VALUE + ")");
    }
    return (short) longValue;
  }

  /**
   * Reads a single byte value from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Only accepts integral number types (Long, BigInteger, Integer, etc.).
   * Floating-point types (Float, Double) will cause an exception.
   * The value must fit within the range of byte ({@code -128} to {@code 127}).
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(42L));
   * byte value = Tuple.readByte(buffer);
   * // value equals 42
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the byte value read from the buffer
   * @throws IllegalArgumentException if the buffer doesn't contain a valid integral number at the current position,
   *                                  or if the value doesn't fit in a byte
   * @throws NullPointerException     if a null value was encoded (numbers cannot be null)
   */
  public static byte readByte(ByteBuffer buffer) {
    long longValue = readSingleIntegralNumber(buffer).longValue();
    if (longValue < Byte.MIN_VALUE || longValue > Byte.MAX_VALUE) {
      throw new IllegalArgumentException("Value " + longValue + " cannot fit in a byte (range: " +
                                         Byte.MIN_VALUE + " to " + Byte.MAX_VALUE + ")");
    }
    return (byte) longValue;
  }

  /**
   * Reads a single char value from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Only accepts integral number types (Long, BigInteger, Integer, etc.).
   * Floating-point types (Float, Double) will cause an exception.
   * The value must fit within the range of char ({@code 0} to {@code 65535}).
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(65L)); // 'A'
   * char value = Tuple.readChar(buffer);
   * // value equals 'A'
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the char value read from the buffer
   * @throws IllegalArgumentException if the buffer doesn't contain a valid integral number at the current position,
   *                                  or if the value doesn't fit in a char
   * @throws NullPointerException     if a null value was encoded (numbers cannot be null)
   */
  public static char readChar(ByteBuffer buffer) {
    long longValue = readSingleIntegralNumber(buffer).longValue();
    if (longValue < Character.MIN_VALUE || longValue > Character.MAX_VALUE) {
      throw new IllegalArgumentException("Value " + longValue + " cannot fit in a char (range: " +
                                         (int) Character.MIN_VALUE + " to " + (int) Character.MAX_VALUE + ")");
    }
    return (char) longValue;
  }

  /**
   * Reads a single boolean value from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(true));
   * boolean value = Tuple.readBoolean(buffer);
   * // value equals true
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the boolean value read from the buffer
   * @throws IllegalArgumentException if the buffer doesn't contain a valid boolean at the current position
   * @throws NullPointerException     if a null value was encoded (booleans cannot be null)
   */
  public static boolean readBoolean(ByteBuffer buffer) {
    Object value = readSingleItem(buffer);
    if (value == null) {
      throw new NullPointerException("Boolean type in Tuples may not be null");
    }
    if (!(value instanceof Boolean)) {
      throw new IllegalArgumentException("Expected boolean but found: " + value.getClass().getSimpleName());
    }
    return (Boolean) value;
  }

  /**
   * Reads a single byte array from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(new byte[]{1, 2, 3}));
   * byte[] value = Tuple.readBytes(buffer);
   * // value equals {1, 2, 3}
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the byte array read from the buffer, or {@code null} if a null value was encoded
   * @throws IllegalArgumentException if the buffer doesn't contain a valid byte array at the current position
   */
  public static byte[] readBytes(ByteBuffer buffer) {
    Object value = readSingleItem(buffer);
    if (value == null) {
      return null;
    }
    if (!(value instanceof byte[])) {
      throw new IllegalArgumentException("Expected byte array but found: " + value.getClass().getSimpleName());
    }
    return (byte[]) value;
  }

  /**
   * Reads a single double value from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Accepts Double and Float types (float can be read as double).
   * Integral number types will cause an exception.
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14));
   * double value = Tuple.readDouble(buffer);
   * // value equals 3.14
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the double value read from the buffer
   * @throws IllegalArgumentException if the buffer doesn't contain a valid floating-point number at the
   *                                  current position
   * @throws NullPointerException     if a null value was encoded (numbers cannot be null)
   */
  public static double readDouble(ByteBuffer buffer) {
    Object value = readSingleItem(buffer);
    if (value == null) {
      throw new NullPointerException("Number types in Tuples may not be null");
    }
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException("Expected number but found: " + value.getClass().getSimpleName());
    }
    // Accept Double and Float (float can be read as double)
    if (!(value instanceof Double || value instanceof Float)) {
      throw new IllegalArgumentException("Expected floating-point number but found: " +
                                         value.getClass().getSimpleName());
    }
    return ((Number) value).doubleValue();
  }

  /**
   * Reads a single float value from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Only accepts Float type. Other number types (including Double) will cause an exception.
   *
   * <p>Example usage:
   * <pre>{@code
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(3.14f));
   * float value = Tuple.readFloat(buffer);
   * // value equals 3.14f
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the float value read from the buffer
   * @throws IllegalArgumentException if the buffer doesn't contain a valid float at the current position
   * @throws NullPointerException     if a null value was encoded (numbers cannot be null)
   */
  public static float readFloat(ByteBuffer buffer) {
    Object value = readSingleItem(buffer);
    if (value == null) {
      throw new NullPointerException("Number types in Tuples may not be null");
    }
    // Only accept Float, not other number types
    if (!(value instanceof Float)) {
      throw new IllegalArgumentException("Expected float but found: " + value.getClass().getSimpleName());
    }
    return (Float) value;
  }

  /**
   * Reads a single UUID from the given ByteBuffer at its current position.
   * The buffer's position will be advanced past the read value.
   *
   * <p>Example usage:
   * <pre>{@code
   * UUID original = UUID.randomUUID();
   * ByteBuffer buffer = ByteBuffer.wrap(Tuple.packItems(original));
   * UUID value = Tuple.readUUID(buffer);
   * // value equals original
   * }</pre>
   *
   * @param buffer the buffer to read from. Position will be advanced.
   * @return the UUID read from the buffer, or {@code null} if a null value was encoded
   * @throws IllegalArgumentException if the buffer doesn't contain a valid UUID at the current position
   */
  public static UUID readUUID(ByteBuffer buffer) {
    Object value = readSingleItem(buffer);
    if (value == null) {
      return null;
    }
    if (!(value instanceof UUID)) {
      throw new IllegalArgumentException("Expected UUID but found: " + value.getClass().getSimpleName());
    }
    return (UUID) value;
  }

  /**
   * Helper method to read a single item from the buffer and advance its position.
   * This uses TupleUtil.unpackSingleItem for efficient single-item unpacking.
   */
  public static Object readSingleItem(ByteBuffer buffer) {
    return TupleUtil.unpackSingleItem(buffer);
  }
}
