package io.github.panghy.javaflow.rpc.serialization;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive tests for {@link TypeDescription}.
 */
public class TypeDescriptionTest {

  @Test
  public void testSimpleClassConstructor() {
    TypeDescription desc = new TypeDescription(String.class);
    assertEquals("java.lang.String", desc.getClassName());
    assertEquals(0, desc.getTypeArguments().length);
  }

  @Test
  public void testPrimitiveClassConstructor() {
    TypeDescription desc = new TypeDescription(int.class);
    assertEquals("int", desc.getClassName());
    assertEquals(0, desc.getTypeArguments().length);
  }

  @Test
  public void testArrayClassConstructor() {
    TypeDescription desc = new TypeDescription(String[].class);
    assertEquals("[Ljava.lang.String;", desc.getClassName());
    assertEquals(0, desc.getTypeArguments().length);
  }

  @Test
  public void testParameterizedConstructor() {
    TypeDescription innerDesc = new TypeDescription(String.class);
    TypeDescription desc = new TypeDescription("java.util.List", innerDesc);
    
    assertEquals("java.util.List", desc.getClassName());
    assertEquals(1, desc.getTypeArguments().length);
    assertEquals("java.lang.String", desc.getTypeArguments()[0].getClassName());
  }

  @Test
  public void testNestedParameterizedConstructor() {
    TypeDescription stringDesc = new TypeDescription(String.class);
    TypeDescription intDesc = new TypeDescription(Integer.class);
    TypeDescription listDesc = new TypeDescription("java.util.List", intDesc);
    TypeDescription mapDesc = new TypeDescription("java.util.Map", stringDesc, listDesc);
    
    assertEquals("java.util.Map", mapDesc.getClassName());
    assertEquals(2, mapDesc.getTypeArguments().length);
    assertEquals("java.lang.String", mapDesc.getTypeArguments()[0].getClassName());
    assertEquals("java.util.List", mapDesc.getTypeArguments()[1].getClassName());
    assertEquals("java.lang.Integer", 
        mapDesc.getTypeArguments()[1].getTypeArguments()[0].getClassName());
  }

  @Test
  public void testFromTypeWithSimpleClass() {
    TypeDescription desc = TypeDescription.fromType(String.class);
    assertEquals("java.lang.String", desc.getClassName());
    assertEquals(0, desc.getTypeArguments().length);
  }

  @Test
  public void testFromTypeWithParameterizedType() {
    // Create a parameterized type for List<String>
    Type listType = new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return new Type[]{String.class};
      }

      @Override
      public Type getRawType() {
        return List.class;
      }

      @Override
      public Type getOwnerType() {
        return null;
      }
    };

    TypeDescription desc = TypeDescription.fromType(listType);
    assertEquals("java.util.List", desc.getClassName());
    assertEquals(1, desc.getTypeArguments().length);
    assertEquals("java.lang.String", desc.getTypeArguments()[0].getClassName());
  }

  @Test
  public void testFromTypeWithNestedParameterizedType() {
    // Create a parameterized type for Map<String, List<Integer>>
    Type listType = new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return new Type[]{Integer.class};
      }

      @Override
      public Type getRawType() {
        return List.class;
      }

      @Override
      public Type getOwnerType() {
        return null;
      }
    };

    Type mapType = new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return new Type[]{String.class, listType};
      }

      @Override
      public Type getRawType() {
        return Map.class;
      }

      @Override
      public Type getOwnerType() {
        return null;
      }
    };

    TypeDescription desc = TypeDescription.fromType(mapType);
    assertEquals("java.util.Map", desc.getClassName());
    assertEquals(2, desc.getTypeArguments().length);
    assertEquals("java.lang.String", desc.getTypeArguments()[0].getClassName());
    assertEquals("java.util.List", desc.getTypeArguments()[1].getClassName());
    assertEquals("java.lang.Integer", 
        desc.getTypeArguments()[1].getTypeArguments()[0].getClassName());
  }

  @Test
  public void testFromTypeWithUnsupportedType() {
    // Create a custom Type that's not a Class or ParameterizedType
    Type customType = new Type() {
      @Override
      public String getTypeName() {
        return "CustomType";
      }
    };

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> TypeDescription.fromType(customType));
    assertTrue(exception.getMessage().contains("Unsupported type"));
  }

  @Test
  public void testToTypeSimpleClass() throws ClassNotFoundException {
    TypeDescription desc = new TypeDescription(String.class);
    Type type = desc.toType();
    assertEquals(String.class, type);
  }

  @Test
  public void testToTypeParameterized() throws ClassNotFoundException {
    TypeDescription stringDesc = new TypeDescription(String.class);
    TypeDescription listDesc = new TypeDescription("java.util.List", stringDesc);
    
    Type type = listDesc.toType();
    assertInstanceOf(ParameterizedType.class, type);
    
    ParameterizedType paramType = (ParameterizedType) type;
    assertEquals(List.class, paramType.getRawType());
    assertArrayEquals(new Type[]{String.class}, paramType.getActualTypeArguments());
    assertNull(paramType.getOwnerType());
  }

  @Test
  public void testToTypeNestedParameterized() throws ClassNotFoundException {
    TypeDescription stringDesc = new TypeDescription(String.class);
    TypeDescription intDesc = new TypeDescription(Integer.class);
    TypeDescription listDesc = new TypeDescription("java.util.List", intDesc);
    TypeDescription mapDesc = new TypeDescription("java.util.Map", stringDesc, listDesc);
    
    Type type = mapDesc.toType();
    assertInstanceOf(ParameterizedType.class, type);
    
    ParameterizedType mapType = (ParameterizedType) type;
    assertEquals(Map.class, mapType.getRawType());
    assertEquals(2, mapType.getActualTypeArguments().length);
    assertEquals(String.class, mapType.getActualTypeArguments()[0]);
    
    assertInstanceOf(ParameterizedType.class, mapType.getActualTypeArguments()[1]);
    ParameterizedType listType = (ParameterizedType) mapType.getActualTypeArguments()[1];
    assertEquals(List.class, listType.getRawType());
    assertArrayEquals(new Type[]{Integer.class}, listType.getActualTypeArguments());
  }

  @Test
  public void testToTypeWithInvalidClassName() {
    TypeDescription desc = new TypeDescription("com.invalid.NonExistentClass");
    assertThrows(ClassNotFoundException.class, desc::toType);
  }

  @Test
  public void testGetTypeArgumentsAsTypes() throws ClassNotFoundException {
    TypeDescription stringDesc = new TypeDescription(String.class);
    TypeDescription intDesc = new TypeDescription(Integer.class);
    TypeDescription listDesc = new TypeDescription("java.util.List", stringDesc, intDesc);
    
    Type[] types = listDesc.getTypeArgumentsAsTypes();
    assertEquals(2, types.length);
    assertEquals(String.class, types[0]);
    assertEquals(Integer.class, types[1]);
  }

  @Test
  public void testGetTypeArgumentsAsTypesEmpty() throws ClassNotFoundException {
    TypeDescription desc = new TypeDescription(String.class);
    Type[] types = desc.getTypeArgumentsAsTypes();
    assertEquals(0, types.length);
  }

  @Test
  public void testGetTypeArgumentsAsTypesWithInvalidClass() {
    TypeDescription invalidDesc = new TypeDescription("com.invalid.NonExistentClass");
    TypeDescription listDesc = new TypeDescription("java.util.List", invalidDesc);
    
    assertThrows(ClassNotFoundException.class, listDesc::getTypeArgumentsAsTypes);
  }

  @Test
  public void testEquals() {
    TypeDescription desc1 = new TypeDescription(String.class);
    TypeDescription desc2 = new TypeDescription(String.class);
    TypeDescription desc3 = new TypeDescription(Integer.class);
    
    assertEquals(desc1, desc1); // Same instance
    assertEquals(desc1, desc2); // Equal instances
    assertNotEquals(desc1, desc3); // Different class
    assertNotEquals(desc1, null); // Null comparison
    assertNotEquals(desc1, "String"); // Different type
    
    // Test with type arguments
    TypeDescription paramDesc1 = new TypeDescription("java.util.List", desc1);
    TypeDescription paramDesc2 = new TypeDescription("java.util.List", desc2);
    TypeDescription paramDesc3 = new TypeDescription("java.util.List", desc3);
    TypeDescription paramDesc4 = new TypeDescription("java.util.Set", desc1);
    
    assertEquals(paramDesc1, paramDesc2); // Same type arguments
    assertNotEquals(paramDesc1, paramDesc3); // Different type arguments
    assertNotEquals(paramDesc1, paramDesc4); // Different class name
  }

  @Test
  public void testHashCode() {
    TypeDescription desc1 = new TypeDescription(String.class);
    TypeDescription desc2 = new TypeDescription(String.class);
    TypeDescription desc3 = new TypeDescription(Integer.class);
    
    assertEquals(desc1.hashCode(), desc2.hashCode());
    assertNotEquals(desc1.hashCode(), desc3.hashCode());
    
    // Test with type arguments
    TypeDescription paramDesc1 = new TypeDescription("java.util.List", desc1);
    TypeDescription paramDesc2 = new TypeDescription("java.util.List", desc2);
    TypeDescription paramDesc3 = new TypeDescription("java.util.List", desc3);
    
    assertEquals(paramDesc1.hashCode(), paramDesc2.hashCode());
    assertNotEquals(paramDesc1.hashCode(), paramDesc3.hashCode());
  }

  @Test
  public void testToString() {
    TypeDescription desc = new TypeDescription(String.class);
    assertEquals("java.lang.String", desc.toString());
    
    TypeDescription listDesc = new TypeDescription("java.util.List", desc);
    assertEquals("java.util.List<java.lang.String>", listDesc.toString());
    
    TypeDescription intDesc = new TypeDescription(Integer.class);
    TypeDescription mapDesc = new TypeDescription("java.util.Map", desc, intDesc);
    assertEquals("java.util.Map<java.lang.String, java.lang.Integer>", mapDesc.toString());
    
    // Test nested types
    TypeDescription nestedListDesc = new TypeDescription("java.util.List", intDesc);
    TypeDescription complexDesc = new TypeDescription("java.util.Map", desc, nestedListDesc);
    assertEquals("java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>", 
        complexDesc.toString());
  }

  @Test
  public void testSerialization() throws Exception {
    // Create a complex TypeDescription
    TypeDescription stringDesc = new TypeDescription(String.class);
    TypeDescription intDesc = new TypeDescription(Integer.class);
    TypeDescription listDesc = new TypeDescription("java.util.List", intDesc);
    TypeDescription mapDesc = new TypeDescription("java.util.Map", stringDesc, listDesc);
    
    // Serialize
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(mapDesc);
    oos.close();
    
    // Deserialize
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(bais);
    TypeDescription deserializedDesc = (TypeDescription) ois.readObject();
    ois.close();
    
    // Verify
    assertEquals(mapDesc, deserializedDesc);
    assertEquals(mapDesc.getClassName(), deserializedDesc.getClassName());
    assertEquals(mapDesc.getTypeArguments().length, deserializedDesc.getTypeArguments().length);
  }

  @Test
  public void testParameterizedTypeImplEquals() throws ClassNotFoundException {
    TypeDescription listDesc1 = new TypeDescription("java.util.List", 
        new TypeDescription(String.class));
    TypeDescription listDesc2 = new TypeDescription("java.util.List", 
        new TypeDescription(String.class));
    TypeDescription listDesc3 = new TypeDescription("java.util.List", 
        new TypeDescription(Integer.class));
    
    ParameterizedType type1 = (ParameterizedType) listDesc1.toType();
    ParameterizedType type2 = (ParameterizedType) listDesc2.toType();
    ParameterizedType type3 = (ParameterizedType) listDesc3.toType();
    
    assertEquals(type1, type1); // Same instance
    assertEquals(type1, type2); // Equal instances
    assertNotEquals(type1, type3); // Different type arguments
    assertNotEquals(type1, null); // Null comparison
    assertNotEquals(type1, "String"); // Different type
    
    // Test against standard ParameterizedType
    assertTrue(type1.equals(type2));
    assertFalse(type1.equals(type3));
  }

  @Test
  public void testParameterizedTypeImplHashCode() throws ClassNotFoundException {
    TypeDescription listDesc1 = new TypeDescription("java.util.List", 
        new TypeDescription(String.class));
    TypeDescription listDesc2 = new TypeDescription("java.util.List", 
        new TypeDescription(String.class));
    TypeDescription listDesc3 = new TypeDescription("java.util.List", 
        new TypeDescription(Integer.class));
    
    ParameterizedType type1 = (ParameterizedType) listDesc1.toType();
    ParameterizedType type2 = (ParameterizedType) listDesc2.toType();
    ParameterizedType type3 = (ParameterizedType) listDesc3.toType();
    
    assertEquals(type1.hashCode(), type2.hashCode());
    assertNotEquals(type1.hashCode(), type3.hashCode());
  }

  @Test
  public void testParameterizedTypeImplToString() throws ClassNotFoundException {
    TypeDescription stringDesc = new TypeDescription(String.class);
    TypeDescription listDesc = new TypeDescription("java.util.List", stringDesc);
    
    ParameterizedType type = (ParameterizedType) listDesc.toType();
    assertEquals("java.util.List<java.lang.String>", type.toString());
    
    // Test nested type
    TypeDescription intDesc = new TypeDescription(Integer.class);
    TypeDescription innerListDesc = new TypeDescription("java.util.List", intDesc);
    TypeDescription mapDesc = new TypeDescription("java.util.Map", stringDesc, innerListDesc);
    
    ParameterizedType mapType = (ParameterizedType) mapDesc.toType();
    assertEquals("java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>", 
        mapType.toString());
    
    // Test empty type arguments (shouldn't happen in practice, but for coverage)
    TypeDescription emptyDesc = new TypeDescription("java.util.List");
    Type emptyType = emptyDesc.toType();
    assertEquals(List.class, emptyType);
  }

  @Test
  public void testParameterizedTypeImplSerialization() throws Exception {
    TypeDescription listDesc = new TypeDescription("java.util.List", 
        new TypeDescription(String.class));
    ParameterizedType type = (ParameterizedType) listDesc.toType();
    
    // Serialize
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(type);
    oos.close();
    
    // Deserialize
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(bais);
    ParameterizedType deserializedType = (ParameterizedType) ois.readObject();
    ois.close();
    
    // Verify
    assertEquals(type.getRawType(), deserializedType.getRawType());
    assertArrayEquals(type.getActualTypeArguments(), deserializedType.getActualTypeArguments());
    assertEquals(type.getOwnerType(), deserializedType.getOwnerType());
  }

  @Test
  public void testRoundTripConversion() throws ClassNotFoundException {
    // Simple class
    TypeDescription simpleDesc = new TypeDescription(String.class);
    Type simpleType = simpleDesc.toType();
    TypeDescription roundTripSimple = TypeDescription.fromType(simpleType);
    assertEquals(simpleDesc, roundTripSimple);
    
    // Parameterized type
    TypeDescription listDesc = new TypeDescription("java.util.List", 
        new TypeDescription(String.class));
    Type listType = listDesc.toType();
    TypeDescription roundTripList = TypeDescription.fromType(listType);
    assertEquals(listDesc.getClassName(), roundTripList.getClassName());
    assertEquals(listDesc.getTypeArguments().length, roundTripList.getTypeArguments().length);
    assertEquals(listDesc.getTypeArguments()[0], roundTripList.getTypeArguments()[0]);
    
    // Complex nested type
    TypeDescription stringDesc = new TypeDescription(String.class);
    TypeDescription intDesc = new TypeDescription(Integer.class);
    TypeDescription innerListDesc = new TypeDescription("java.util.List", intDesc);
    TypeDescription mapDesc = new TypeDescription("java.util.Map", stringDesc, innerListDesc);
    
    Type mapType = mapDesc.toType();
    TypeDescription roundTripMap = TypeDescription.fromType(mapType);
    assertEquals(mapDesc.getClassName(), roundTripMap.getClassName());
    assertEquals(mapDesc.getTypeArguments().length, roundTripMap.getTypeArguments().length);
    assertEquals(mapDesc.getTypeArguments()[0], roundTripMap.getTypeArguments()[0]);
    assertEquals(mapDesc.getTypeArguments()[1].getClassName(), 
        roundTripMap.getTypeArguments()[1].getClassName());
  }

  @Test
  public void testMultipleTypeParameters() throws ClassNotFoundException {
    // Test with 3 type parameters
    TypeDescription t1 = new TypeDescription(String.class);
    TypeDescription t2 = new TypeDescription(Integer.class);
    TypeDescription t3 = new TypeDescription(Boolean.class);
    TypeDescription multiDesc = new TypeDescription("com.example.Triple", t1, t2, t3);
    
    assertEquals("com.example.Triple", multiDesc.getClassName());
    assertEquals(3, multiDesc.getTypeArguments().length);
    assertEquals("java.lang.String", multiDesc.getTypeArguments()[0].getClassName());
    assertEquals("java.lang.Integer", multiDesc.getTypeArguments()[1].getClassName());
    assertEquals("java.lang.Boolean", multiDesc.getTypeArguments()[2].getClassName());
    
    String expected = "com.example.Triple<java.lang.String, java.lang.Integer, java.lang.Boolean>";
    assertEquals(expected, multiDesc.toString());
  }

  @Test
  public void testVarargsConstructor() {
    // Test varargs with no arguments
    TypeDescription desc1 = new TypeDescription("java.util.List");
    assertEquals("java.util.List", desc1.getClassName());
    assertEquals(0, desc1.getTypeArguments().length);
    
    // Test varargs with multiple arguments
    TypeDescription t1 = new TypeDescription(String.class);
    TypeDescription t2 = new TypeDescription(Integer.class);
    TypeDescription desc2 = new TypeDescription("java.util.Map", t1, t2);
    assertEquals("java.util.Map", desc2.getClassName());
    assertEquals(2, desc2.getTypeArguments().length);
  }

  @Test
  public void testGetters() {
    TypeDescription stringDesc = new TypeDescription(String.class);
    TypeDescription listDesc = new TypeDescription("java.util.List", stringDesc);
    
    // Test getClassName
    assertEquals("java.util.List", listDesc.getClassName());
    
    // Test getTypeArguments
    TypeDescription[] typeArgs = listDesc.getTypeArguments();
    assertNotNull(typeArgs);
    assertEquals(1, typeArgs.length);
    assertEquals(stringDesc, typeArgs[0]);
  }
}