package io.github.panghy.javaflow.scheduler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for task state transitions and their effect on the parent-child relationship.
 */
class TaskStateTransitionTest {

  @Test
  void testStateTransitionsRemoveFromParent() {
    // Create parent and child tasks
    Task parentTask = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    Task childTask = new Task(2, TaskPriority.DEFAULT, () -> "child", parentTask);
    
    // Add child to parent
    parentTask.addChild(childTask);
    
    // Create a flag to track when the child is removed
    AtomicBoolean childRemoved = new AtomicBoolean(false);
    
    // Create a custom parent task that tracks when removeChild is called
    Task customParent = new Task(3, TaskPriority.DEFAULT, () -> "custom-parent", null) {
      @Override
      public void removeChild(Task child) {
        childRemoved.set(true);
        super.removeChild(child);
      }
    };
    
    Task customChild = new Task(4, TaskPriority.DEFAULT, () -> "custom-child", customParent);
    customParent.addChild(customChild);
    
    // Initial states
    assertEquals(Task.TaskState.CREATED, childTask.getState());
    assertEquals(Task.TaskState.CREATED, customChild.getState());
    assertFalse(childRemoved.get(), "Child should not be removed yet");
    
    // State transition: CREATED -> RUNNING (should not remove from parent)
    customChild.setState(Task.TaskState.RUNNING);
    assertEquals(Task.TaskState.RUNNING, customChild.getState());
    assertFalse(childRemoved.get(), "Child should not be removed for RUNNING state");
    
    // State transition: RUNNING -> SUSPENDED (should not remove from parent)
    customChild.setState(Task.TaskState.SUSPENDED);
    assertEquals(Task.TaskState.SUSPENDED, customChild.getState());
    assertFalse(childRemoved.get(), "Child should not be removed for SUSPENDED state");
    
    // State transition: SUSPENDED -> COMPLETED (should remove from parent)
    customChild.setState(Task.TaskState.COMPLETED);
    assertEquals(Task.TaskState.COMPLETED, customChild.getState());
    assertTrue(childRemoved.get(), "Child should be removed for COMPLETED state");
    
    // Reset the flag
    childRemoved.set(false);
    
    // Add the child back to parent
    customParent.addChild(customChild);
    
    // State transition: COMPLETED -> CREATED (should have no effect since already COMPLETED)
    customChild.setState(Task.TaskState.CREATED);
    assertEquals(Task.TaskState.COMPLETED, customChild.getState(), 
        "Cannot change state from COMPLETED");
    assertFalse(childRemoved.get(), "No state change, so child should not be removed");
    
    // Create a new child for testing FAILED state
    Task failedChild = new Task(5, TaskPriority.DEFAULT, () -> "failed-child", customParent);
    customParent.addChild(failedChild);
    childRemoved.set(false);
    
    // State transition: CREATED -> FAILED (should remove from parent)
    failedChild.setState(Task.TaskState.FAILED);
    assertEquals(Task.TaskState.FAILED, failedChild.getState());
    assertTrue(childRemoved.get(), "Child should be removed for FAILED state");
  }
  
  @Test
  void testRepeatedStateTransitionsNoEffect() {
    // Create parent and child tasks
    Task parentTask = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    Task childTask = new Task(2, TaskPriority.DEFAULT, () -> "child", parentTask);
    
    // Add child to parent
    parentTask.addChild(childTask);
    
    // Initial state
    assertEquals(Task.TaskState.CREATED, childTask.getState());
    
    // Set state to the same value (should have no effect)
    childTask.setState(Task.TaskState.CREATED);
    assertEquals(Task.TaskState.CREATED, childTask.getState());
    
    // Change to RUNNING
    childTask.setState(Task.TaskState.RUNNING);
    assertEquals(Task.TaskState.RUNNING, childTask.getState());
    
    // Set state to the same value again (should have no effect)
    childTask.setState(Task.TaskState.RUNNING);
    assertEquals(Task.TaskState.RUNNING, childTask.getState());
    
    // Change to COMPLETED (this should remove from parent)
    childTask.setState(Task.TaskState.COMPLETED);
    assertEquals(Task.TaskState.COMPLETED, childTask.getState());
    
    // Try to change state after COMPLETED (should have no effect)
    childTask.setState(Task.TaskState.RUNNING);
    assertEquals(Task.TaskState.COMPLETED, childTask.getState(), 
        "Cannot change state from COMPLETED");
    
    // Create a new child for testing FAILED state
    Task failedChild = new Task(3, TaskPriority.DEFAULT, () -> "failed-child", parentTask);
    parentTask.addChild(failedChild);
    
    // Change to FAILED
    failedChild.setState(Task.TaskState.FAILED);
    assertEquals(Task.TaskState.FAILED, failedChild.getState());
    
    // Try to change state after FAILED (should have no effect)
    failedChild.setState(Task.TaskState.COMPLETED);
    assertEquals(Task.TaskState.FAILED, failedChild.getState(),
        "Cannot change state from FAILED");
  }
  
  @Test
  void testChildWithNoParent() {
    // Create a task with no parent
    Task task = new Task(1, TaskPriority.DEFAULT, () -> "no-parent", null);
    
    // Initial state
    assertEquals(Task.TaskState.CREATED, task.getState());
    
    // Transition to COMPLETED (should not throw exception even without parent)
    task.setState(Task.TaskState.COMPLETED);
    assertEquals(Task.TaskState.COMPLETED, task.getState());
    
    // Create another task with no parent
    Task failedTask = new Task(2, TaskPriority.DEFAULT, () -> "no-parent-failed", null);
    
    // Transition to FAILED (should not throw exception even without parent)
    failedTask.setState(Task.TaskState.FAILED);
    assertEquals(Task.TaskState.FAILED, failedTask.getState());
  }
}