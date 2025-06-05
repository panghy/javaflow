package io.github.panghy.javaflow.rpc.util;

import io.github.panghy.javaflow.util.Pair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the {@link Pair} utility class.
 */
public class PairTest {

  @Test
  public void testConstructor() {
    String first = "first";
    Integer second = 42;
    Pair<String, Integer> pair = new Pair<>(first, second);
    
    assertEquals(first, pair.first());
    assertEquals(second, pair.second());
  }
  
  @Test
  public void testFactoryMethod() {
    String first = "first";
    Integer second = 42;
    Pair<String, Integer> pair = Pair.of(first, second);
    
    assertEquals(first, pair.first());
    assertEquals(second, pair.second());
  }
  
  @Test
  public void testWithNullValues() {
    Pair<String, Integer> pair = new Pair<>(null, null);
    
    assertNull(pair.first());
    assertNull(pair.second());
  }
  
  @Test
  public void testEqualsAndHashCode() {
    Pair<String, Integer> pair1 = new Pair<>("test", 42);
    Pair<String, Integer> pair2 = new Pair<>("test", 42);
    Pair<String, Integer> pair3 = new Pair<>("different", 42);
    Pair<String, Integer> pair4 = new Pair<>("test", 43);
    
    assertEquals(pair1, pair2);
    assertEquals(pair1.hashCode(), pair2.hashCode());
    
    assertNotEquals(pair1, pair3);
    assertNotEquals(pair1, pair4);
  }
  
  @Test
  public void testToString() {
    Pair<String, Integer> pair = new Pair<>("test", 42);
    String expected = "Pair{first=test, second=42}";
    
    assertEquals(expected, pair.toString());
  }
  
  @Test
  public void testWithDifferentTypes() {
    Pair<Integer, Double> pair = new Pair<>(1, 2.5);
    
    assertEquals(Integer.valueOf(1), pair.first());
    assertEquals(Double.valueOf(2.5), pair.second());
  }
  
  @Test
  public void testWithComplexObjects() {
    class TestObject {
      private final String value;
      
      TestObject(String value) {
        this.value = value;
      }
      
      @Override
      public String toString() {
        return "TestObject(" + value + ")";
      }
    }
    
    TestObject obj1 = new TestObject("one");
    TestObject obj2 = new TestObject("two");
    Pair<TestObject, TestObject> pair = new Pair<>(obj1, obj2);
    
    assertEquals(obj1, pair.first());
    assertEquals(obj2, pair.second());
    
    String expected = "Pair{first=TestObject(one), second=TestObject(two)}";
    assertEquals(expected, pair.toString());
  }
}