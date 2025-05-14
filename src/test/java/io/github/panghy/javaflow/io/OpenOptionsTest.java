package io.github.panghy.javaflow.io;

import org.junit.jupiter.api.Test;

import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the OpenOptions enum.
 */
class OpenOptionsTest {

  @Test
  void testToStandardOptionsEmpty() {
    EnumSet<StandardOpenOption> result = OpenOptions.toStandardOptions();
    assertTrue(result.isEmpty());
  }

  @Test
  void testToStandardOptionsSingleOption() {
    EnumSet<StandardOpenOption> result = OpenOptions.toStandardOptions(OpenOptions.READ);
    assertEquals(1, result.size());
    assertTrue(result.contains(StandardOpenOption.READ));
  }

  @Test
  void testToStandardOptionsMultipleOptions() {
    EnumSet<StandardOpenOption> result = OpenOptions.toStandardOptions(
        OpenOptions.READ, OpenOptions.WRITE, OpenOptions.CREATE);
    
    assertEquals(3, result.size());
    assertTrue(result.contains(StandardOpenOption.READ));
    assertTrue(result.contains(StandardOpenOption.WRITE));
    assertTrue(result.contains(StandardOpenOption.CREATE));
  }

  @Test
  void testToStandardOptionsAllOptions() {
    EnumSet<StandardOpenOption> result = OpenOptions.toStandardOptions(
        OpenOptions.READ,
        OpenOptions.WRITE,
        OpenOptions.CREATE,
        OpenOptions.CREATE_NEW,
        OpenOptions.TRUNCATE_EXISTING,
        OpenOptions.APPEND,
        OpenOptions.DELETE_ON_CLOSE,
        OpenOptions.SYNC
    );
    
    assertEquals(8, result.size());
    assertTrue(result.contains(StandardOpenOption.READ));
    assertTrue(result.contains(StandardOpenOption.WRITE));
    assertTrue(result.contains(StandardOpenOption.CREATE));
    assertTrue(result.contains(StandardOpenOption.CREATE_NEW));
    assertTrue(result.contains(StandardOpenOption.TRUNCATE_EXISTING));
    assertTrue(result.contains(StandardOpenOption.APPEND));
    assertTrue(result.contains(StandardOpenOption.DELETE_ON_CLOSE));
    assertTrue(result.contains(StandardOpenOption.SYNC));
  }
}