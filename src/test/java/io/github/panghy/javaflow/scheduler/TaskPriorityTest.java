package io.github.panghy.javaflow.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskPriorityTest {

  @Test
  public void testValidateUserPriority() {
    // Valid priority
    TaskPriority.validateUserPriority(10);
    TaskPriority.validateUserPriority(0);

    // Invalid priority
    assertThrows(IllegalArgumentException.class, () -> TaskPriority.validateUserPriority(-1));
  }
}