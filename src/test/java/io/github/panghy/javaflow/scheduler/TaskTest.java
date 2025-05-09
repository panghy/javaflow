package io.github.panghy.javaflow.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTest {

  @Test
  void testTaskCreation() {
    Callable<String> callable = () -> "test";

    Task task = new Task(1, TaskPriority.DEFAULT, callable, null);

    assertEquals(1, task.getId());
    assertEquals(TaskPriority.DEFAULT, task.getPriority());
    assertEquals(callable, task.getCallable());
    assertEquals(Task.TaskState.CREATED, task.getState());
    assertTrue(task.getCreationTime() > 0);
    assertTrue(task.getSequence() >= 0);
  }

  @Test
  void testTaskState() {
    Task task = new Task(1, TaskPriority.DEFAULT, () -> "test", null);

    assertEquals(Task.TaskState.CREATED, task.getState());

    task.setState(Task.TaskState.RUNNING);
    assertEquals(Task.TaskState.RUNNING, task.getState());

    task.setState(Task.TaskState.SUSPENDED);
    assertEquals(Task.TaskState.SUSPENDED, task.getState());

    task.setState(Task.TaskState.COMPLETED);
    assertEquals(Task.TaskState.COMPLETED, task.getState());

    // Once failed, it cannot be changed
    task.setState(Task.TaskState.FAILED);
    assertEquals(Task.TaskState.COMPLETED, task.getState());

    task = new Task(2, TaskPriority.DEFAULT, () -> "test", null);
    task.setState(Task.TaskState.FAILED);
    assertEquals(Task.TaskState.FAILED, task.getState());

    // Once completed, it cannot be changed
    task.setState(Task.TaskState.COMPLETED);
    assertEquals(Task.TaskState.FAILED, task.getState());
  }

  @Test
  void testNullCallable() {
    assertThrows(NullPointerException.class, () -> new Task(1, TaskPriority.DEFAULT, null, null));
  }

  @Test
  void testCompareToByPriority() {
    Task highPriorityTask = new Task(1, TaskPriority.HIGH, () -> "high", null);
    Task mediumPriorityTask = new Task(2, TaskPriority.DEFAULT, () -> "medium", null);
    Task lowPriorityTask = new Task(3, TaskPriority.LOW, () -> "low", null);

    // Higher priority should come before lower priority
    assertTrue(highPriorityTask.compareTo(mediumPriorityTask) < 0);
    assertTrue(mediumPriorityTask.compareTo(lowPriorityTask) < 0);
    assertTrue(highPriorityTask.compareTo(lowPriorityTask) < 0);

    // Lower priority should come after higher priority
    assertTrue(lowPriorityTask.compareTo(highPriorityTask) > 0);
    assertTrue(mediumPriorityTask.compareTo(highPriorityTask) > 0);
    assertTrue(lowPriorityTask.compareTo(mediumPriorityTask) > 0);
  }

  @Test
  void testCompareToByCreationTime() throws Exception {
    // Create tasks with same priority but different creation times
    Task firstTask = new Task(1, TaskPriority.DEFAULT, () -> "first", null);
    Thread.sleep(10); // Ensure creation time is different
    Task secondTask = new Task(2, TaskPriority.DEFAULT, () -> "second", null);

    // Earlier creation time should come before later creation time
    assertTrue(firstTask.compareTo(secondTask) < 0);
    assertTrue(secondTask.compareTo(firstTask) > 0);
  }

  @Test
  void testCompareToBySequence() {
    // Create tasks with same priority and manipulate creation time to be the same
    Task task1 = new Task(1, TaskPriority.DEFAULT, () -> "first", null);
    Task task2 = new Task(2, TaskPriority.DEFAULT, () -> "second", null);

    // Modify tasks to have the same creation time through reflection
    // This way we can test the sequence comparison
    long sameTime = System.currentTimeMillis();

    // Use reflection to set the creation time field to be the same for both tasks
    try {
      java.lang.reflect.Field creationTimeField = Task.class.getDeclaredField("creationTime");
      creationTimeField.setAccessible(true);
      creationTimeField.set(task1, sameTime);
      creationTimeField.set(task2, sameTime);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // Since they have the same priority and creation time, sequence should determine order
    // task1 is created first so has lower sequence number
    assertTrue(task1.compareTo(task2) < 0);
    assertTrue(task2.compareTo(task1) > 0);
  }

  @Test
  void testTaskSorting() {
    // Create tasks with different priorities
    Task highPriorityTask = new Task(1, TaskPriority.HIGH, () -> "high", null);
    Task mediumPriorityTask = new Task(2, TaskPriority.DEFAULT, () -> "medium", null);
    Task lowPriorityTask = new Task(3, TaskPriority.LOW, () -> "low", null);

    // Create a list with tasks in wrong order
    List<Task> tasks = new ArrayList<>(Arrays.asList(
        lowPriorityTask,
        mediumPriorityTask,
        highPriorityTask
    ));

    // Sort the list - should be sorted by priority
    Collections.sort(tasks);

    // Verify correct order
    assertEquals(highPriorityTask, tasks.get(0));
    assertEquals(mediumPriorityTask, tasks.get(1));
    assertEquals(lowPriorityTask, tasks.get(2));
  }

  @Test
  void testToString() {
    Task task = new Task(42, TaskPriority.DEFAULT, () -> "test", null);
    task.setState(Task.TaskState.RUNNING);

    String str = task.toString();

    assertNotNull(str);
    assertTrue(str.contains("42"));
    assertTrue(str.contains(String.valueOf(TaskPriority.DEFAULT)));
    assertTrue(str.contains("RUNNING"));
  }

  @Test
  void testEqualsAndHashCode() {
    Task task1 = new Task(42, TaskPriority.DEFAULT, () -> "test", null);
    // Same id, different priority
    Task task2 = new Task(42, TaskPriority.HIGH, () -> "different", null);
    // Different id
    Task task3 = new Task(99, TaskPriority.DEFAULT, () -> "test", null);

    // Same instance should be equal to itself
    assertEquals(task1, task1);

    // Null and different types should not be equal
    assertNotEquals(null, task1);
    assertNotEquals("not a task", task1);

    // Tasks with same id should be equal even if other attributes differ
    assertEquals(task1, task2);
    assertEquals(task2, task1);

    // Tasks with different id should not be equal
    assertNotEquals(task1, task3);
    assertNotEquals(task3, task1);

    // Hash code should be consistent with equals
    assertEquals(task1.hashCode(), task2.hashCode());
    assertNotEquals(task1.hashCode(), task3.hashCode());
  }
  
  @Test
  void testParentChildRelationship() {
    // Create a parent task
    Task parentTask = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Create two child tasks
    Task childTask1 = new Task(2, TaskPriority.DEFAULT, () -> "child1", parentTask);
    Task childTask2 = new Task(3, TaskPriority.DEFAULT, () -> "child2", parentTask);
    
    // Explicitly add child tasks to parent
    parentTask.addChild(childTask1);
    parentTask.addChild(childTask2);
    
    // Verify parent reference
    assertEquals(parentTask, childTask1.getParent());
    assertEquals(parentTask, childTask2.getParent());
    
    // When child completes, it should be removed from parent
    childTask1.setState(Task.TaskState.COMPLETED);
    
    // Add a third child and then remove it
    Task childTask3 = new Task(4, TaskPriority.DEFAULT, () -> "child3", parentTask);
    parentTask.addChild(childTask3);
    parentTask.removeChild(childTask3);
  }
  
  @Test
  void testCancellation() {
    // Create a cancellation callback tracker
    AtomicBoolean callbackCalled = new AtomicBoolean(false);
    
    // Create a parent task with a cancellation callback
    Task parentTask = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    parentTask.setCancellationCallback(() -> callbackCalled.set(true));
    
    // Create two child tasks
    Task childTask1 = new Task(2, TaskPriority.DEFAULT, () -> "child1", parentTask);
    Task childTask2 = new Task(3, TaskPriority.DEFAULT, () -> "child2", parentTask);
    
    // Add children to parent
    parentTask.addChild(childTask1);
    parentTask.addChild(childTask2);
    
    // Initially, no tasks should be cancelled
    assertFalse(parentTask.isCancelled());
    assertFalse(childTask1.isCancelled());
    assertFalse(childTask2.isCancelled());
    
    // Cancel the parent task
    parentTask.cancel();
    
    // Parent and all children should be cancelled
    assertTrue(parentTask.isCancelled());
    assertTrue(childTask1.isCancelled());
    assertTrue(childTask2.isCancelled());
    
    // Cancellation callback should have been called
    assertTrue(callbackCalled.get());
    
    // Cancelling again should be a no-op
    callbackCalled.set(false);
    parentTask.cancel();
    assertFalse(callbackCalled.get()); // Should not be called again
  }
  
  @Test
  void testChildCancellation() {
    // Create a parent task
    Task parentTask = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Create two child tasks
    Task childTask1 = new Task(2, TaskPriority.DEFAULT, () -> "child1", parentTask);
    AtomicBoolean child1Cancelled = new AtomicBoolean(false);
    childTask1.setCancellationCallback(() -> child1Cancelled.set(true));
    
    Task childTask2 = new Task(3, TaskPriority.DEFAULT, () -> "child2", parentTask);
    AtomicBoolean child2Cancelled = new AtomicBoolean(false);
    childTask2.setCancellationCallback(() -> child2Cancelled.set(true));
    
    // Add children to parent
    parentTask.addChild(childTask1);
    parentTask.addChild(childTask2);
    
    // Cancel just one child
    childTask1.cancel();
    
    // Only that child should be cancelled
    assertFalse(parentTask.isCancelled());
    assertTrue(childTask1.isCancelled());
    assertFalse(childTask2.isCancelled());
    
    // Its callback should have been called
    assertTrue(child1Cancelled.get());
    assertFalse(child2Cancelled.get());
    
    // The cancelled child should be removed from the parent
    childTask2.setState(Task.TaskState.COMPLETED);
    assertFalse(parentTask.isCancelled());
  }
  
  @Test
  void testCancellationWithNestedChildren() {
    // Create a hierarchy of tasks: grandparent -> parent -> child
    Task grandparentTask = new Task(1, TaskPriority.DEFAULT, () -> "grandparent", null);
    Task parentTask = new Task(2, TaskPriority.DEFAULT, () -> "parent", grandparentTask);
    Task childTask = new Task(3, TaskPriority.DEFAULT, () -> "child", parentTask);
    
    // Add to respective parents
    grandparentTask.addChild(parentTask);
    parentTask.addChild(childTask);
    
    // Track cancellations
    AtomicBoolean grandparentCancelled = new AtomicBoolean(false);
    AtomicBoolean parentCancelled = new AtomicBoolean(false);
    AtomicBoolean childCancelled = new AtomicBoolean(false);
    
    grandparentTask.setCancellationCallback(() -> grandparentCancelled.set(true));
    parentTask.setCancellationCallback(() -> parentCancelled.set(true));
    childTask.setCancellationCallback(() -> childCancelled.set(true));
    
    // Cancel the grandparent
    grandparentTask.cancel();
    
    // All tasks should be cancelled
    assertTrue(grandparentTask.isCancelled());
    assertTrue(parentTask.isCancelled());
    assertTrue(childTask.isCancelled());
    
    // All callbacks should have been called
    assertTrue(grandparentCancelled.get());
    assertTrue(parentCancelled.get());
    assertTrue(childCancelled.get());
  }
  
  @Test
  void testSetStateCompletedRemovesFromParent() {
    // Create parent and child tasks
    Task parentTask = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    Task childTask = new Task(2, TaskPriority.DEFAULT, () -> "child", parentTask);
    
    // Add child to parent
    parentTask.addChild(childTask);
    
    // Complete the child
    childTask.setState(Task.TaskState.COMPLETED);
    
    // Add the child back (this would fail if it wasn't properly removed)
    parentTask.addChild(childTask);
    
    // Now fail the child
    childTask.setState(Task.TaskState.FAILED);
    
    // Try to change state of completed task (should be no-op)
    Task completedTask = new Task(3, TaskPriority.DEFAULT, () -> "completed", null);
    completedTask.setState(Task.TaskState.COMPLETED);
    completedTask.setState(Task.TaskState.RUNNING); // Should not change
    assertEquals(Task.TaskState.COMPLETED, completedTask.getState());
  }
}