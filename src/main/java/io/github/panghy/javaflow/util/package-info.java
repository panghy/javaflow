/*
 * Copyright 2024 The JavaFlow Project Authors
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

/**
 * Utility classes for the JavaFlow project.
 *
 * <p>This package contains various utility classes for common operations such as:
 * <ul>
 *   <li>Byte array manipulation and comparison</li>
 *   <li>String utilities including UTF-8/UTF-16 handling</li>
 *   <li>Tuple data structures for ordered collections</li>
 *   <li>Range operations for key-value boundaries</li>
 *   <li>I/O and logging utilities</li>
 * </ul>
 *
 * <h2>Attribution</h2>
 * <p>Several classes in this package ({@link io.github.panghy.javaflow.util.ByteArrayUtil},
 * {@link io.github.panghy.javaflow.util.FastByteComparisons},
 * {@link io.github.panghy.javaflow.util.IterableComparator},
 * {@link io.github.panghy.javaflow.util.Range},
 * {@link io.github.panghy.javaflow.util.StringUtil},
 * {@link io.github.panghy.javaflow.util.Tuple}, and
 * {@link io.github.panghy.javaflow.util.TupleUtil}) are derived from the
 * <a href="https://github.com/apple/foundationdb">FoundationDB open source project</a>
 * and are subject to the original Apache License 2.0 copyright by Apple Inc. and the
 * FoundationDB project authors (2013-2024).
 *
 * <p>These components have been adapted for use in the JavaFlow project while maintaining
 * compatibility with their original licensing terms and design principles.
 */
package io.github.panghy.javaflow.util;