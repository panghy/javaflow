package io.github.panghy.javaflow.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for task child management restrictions.
 */
class TaskAddChildTest {

  @Test
  void testCannotAddChildToCancelledTask() {
    // Create parent task
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Create a potential child task
    Task child = new Task(2, TaskPriority.DEFAULT, () -> "child", null);
    
    // Cancel the parent
    parent.cancel();
    assertTrue(parent.isCancelled(), "Parent should be cancelled");
    
    // Attempt to add child to cancelled parent should throw
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> parent.addChild(child),
        "Should not be able to add child to cancelled task"
    );
    
    // Verify exception message
    assertTrue(exception.getMessage().contains("cancelled"),
        "Exception message should mention cancelled state");
  }
  
  @Test
  void testCannotAddChildToCompletedTask() {
    // Create parent task
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Create a potential child task
    Task child = new Task(2, TaskPriority.DEFAULT, () -> "child", null);
    
    // Complete the parent
    parent.setState(Task.TaskState.COMPLETED);
    
    // Attempt to add child to completed parent should throw
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> parent.addChild(child),
        "Should not be able to add child to completed task"
    );
    
    // Verify exception message
    assertTrue(exception.getMessage().contains("completed"),
        "Exception message should mention completed state");
  }
  
  @Test
  void testCannotAddChildToFailedTask() {
    // Create parent task
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Create a potential child task
    Task child = new Task(2, TaskPriority.DEFAULT, () -> "child", null);
    
    // Fail the parent
    parent.setState(Task.TaskState.FAILED);
    
    // Attempt to add child to failed parent should throw
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> parent.addChild(child),
        "Should not be able to add child to failed task"
    );
    
    // Verify exception message
    assertTrue(exception.getMessage().contains("failed"),
        "Exception message should mention failed state");
  }
  
  @Test
  void testCanAddChildToCreatedTask() {
    // Create parent task
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Create a child task
    Task child = new Task(2, TaskPriority.DEFAULT, () -> "child", null);
    
    // This should not throw - parent is in CREATED state
    parent.addChild(child);
  }
  
  @Test
  void testCanAddChildToRunningTask() {
    // Create parent task
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Set parent to RUNNING
    parent.setState(Task.TaskState.RUNNING);
    
    // Create a child task
    Task child = new Task(2, TaskPriority.DEFAULT, () -> "child", null);
    
    // This should not throw - parent is in RUNNING state
    parent.addChild(child);
  }
  
  @Test
  void testCanAddChildToSuspendedTask() {
    // Create parent task
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Set parent to SUSPENDED
    parent.setState(Task.TaskState.SUSPENDED);
    
    // Create a child task
    Task child = new Task(2, TaskPriority.DEFAULT, () -> "child", null);
    
    // This should not throw - parent is in SUSPENDED state
    parent.addChild(child);
  }
}