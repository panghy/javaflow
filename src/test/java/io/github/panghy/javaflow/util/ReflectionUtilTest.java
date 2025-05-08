package io.github.panghy.javaflow.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReflectionUtilTest {

  // Test class with private and public fields for testing reflection
  static class TestClass {
    private String privateField = "private";
    public String publicField = "public";
    
    // Parent class field (to test inheritance)
    private Integer parentField = 42;
  }
  
  // Parent class for inheritance testing
  static class ParentClass {
    private Integer parentField = 100;
  }
  
  // Child class for inheritance testing
  static class ChildClass extends ParentClass {
    private String childField = "child";
  }

  @Test
  void testGetField() {
    TestClass testObj = new TestClass();
    
    // Should be able to access a private field
    String privateValue = ReflectionUtil.getField(testObj, "privateField");
    assertEquals("private", privateValue);
    
    // Should be able to access a public field
    String publicValue = ReflectionUtil.getField(testObj, "publicField");
    assertEquals("public", publicValue);
  }
  
  @Test
  void testSetField() {
    TestClass testObj = new TestClass();
    
    // Set a private field
    ReflectionUtil.setField(testObj, "privateField", "modified");
    assertEquals("modified", testObj.privateField);
    
    // Set a public field
    ReflectionUtil.setField(testObj, "publicField", "modified-public");
    assertEquals("modified-public", testObj.publicField);
  }
  
  @Test
  void testGetFieldFromParentClass() {
    ChildClass childObj = new ChildClass();
    
    // Should be able to access a field from the parent class
    Integer parentValue = ReflectionUtil.getField(childObj, "parentField");
    assertEquals(100, parentValue);
  }
  
  @Test
  void testSetFieldInParentClass() {
    ChildClass childObj = new ChildClass();
    
    // Should be able to set a field in the parent class
    ReflectionUtil.setField(childObj, "parentField", 200);
    
    // Now verify it was set
    Integer parentValue = ReflectionUtil.getField(childObj, "parentField");
    assertEquals(200, parentValue);
  }
  
  @Test
  void testGetFieldNotFound() {
    TestClass testObj = new TestClass();
    
    // Try to access a non-existent field
    RuntimeException exception = assertThrows(RuntimeException.class,
        () -> ReflectionUtil.getField(testObj, "nonExistentField"));
    
    // Verify the error message mentions the field name
    assertEquals("Failed to access field: nonExistentField", exception.getMessage());
  }
  
  @Test
  void testSetFieldNotFound() {
    TestClass testObj = new TestClass();
    
    // Try to set a non-existent field
    RuntimeException exception = assertThrows(RuntimeException.class,
        () -> ReflectionUtil.setField(testObj, "nonExistentField", "value"));
    
    // Verify the error message mentions the field name
    assertEquals("Failed to set field: nonExistentField", exception.getMessage());
  }
  
  @Test
  void testGetFieldWithNullObject() {
    // Try to access a field on a null object
    assertThrows(RuntimeException.class,
        () -> ReflectionUtil.getField(null, "anyField"));
  }
  
  @Test
  void testSetFieldWithNullObject() {
    // Try to set a field on a null object
    assertThrows(RuntimeException.class,
        () -> ReflectionUtil.setField(null, "anyField", "value"));
  }
}