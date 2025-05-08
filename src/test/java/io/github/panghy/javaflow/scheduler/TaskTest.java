package io.github.panghy.javaflow.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTest {

  @Test
  void testTaskCreation() {
    Callable<String> callable = () -> "test";
    
    Task task = new Task(1, TaskPriority.DEFAULT, callable);
    
    assertEquals(1, task.getId());
    assertEquals(TaskPriority.DEFAULT, task.getPriority());
    assertEquals(callable, task.getCallable());
    assertEquals(Task.TaskState.CREATED, task.getState());
    assertTrue(task.getCreationTime() > 0);
    assertTrue(task.getSequence() >= 0);
  }
  
  @Test
  void testTaskState() {
    Task task = new Task(1, TaskPriority.DEFAULT, () -> "test");
    
    assertEquals(Task.TaskState.CREATED, task.getState());
    
    task.setState(Task.TaskState.RUNNING);
    assertEquals(Task.TaskState.RUNNING, task.getState());
    
    task.setState(Task.TaskState.SUSPENDED);
    assertEquals(Task.TaskState.SUSPENDED, task.getState());
    
    task.setState(Task.TaskState.COMPLETED);
    assertEquals(Task.TaskState.COMPLETED, task.getState());
    
    task.setState(Task.TaskState.FAILED);
    assertEquals(Task.TaskState.FAILED, task.getState());
    
    task.setState(Task.TaskState.CANCELLED);
    assertEquals(Task.TaskState.CANCELLED, task.getState());
  }
  
  @Test
  void testNullCallable() {
    assertThrows(NullPointerException.class, () -> new Task(1, TaskPriority.DEFAULT, null));
  }
  
  @Test
  void testCompareToByPriority() {
    Task highPriorityTask = new Task(1, TaskPriority.HIGH, () -> "high");
    Task mediumPriorityTask = new Task(2, TaskPriority.DEFAULT, () -> "medium");
    Task lowPriorityTask = new Task(3, TaskPriority.LOW, () -> "low");
    
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
    Task firstTask = new Task(1, TaskPriority.DEFAULT, () -> "first");
    Thread.sleep(10); // Ensure creation time is different
    Task secondTask = new Task(2, TaskPriority.DEFAULT, () -> "second");
    
    // Earlier creation time should come before later creation time
    assertTrue(firstTask.compareTo(secondTask) < 0);
    assertTrue(secondTask.compareTo(firstTask) > 0);
  }
  
  @Test
  void testCompareToBySequence() {
    // Create tasks with same priority and manipulate creation time to be the same
    Task task1 = new Task(1, TaskPriority.DEFAULT, () -> "first");
    Task task2 = new Task(2, TaskPriority.DEFAULT, () -> "second");
    
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
    Task highPriorityTask = new Task(1, TaskPriority.HIGH, () -> "high");
    Task mediumPriorityTask = new Task(2, TaskPriority.DEFAULT, () -> "medium");
    Task lowPriorityTask = new Task(3, TaskPriority.LOW, () -> "low");
    
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
    Task task = new Task(42, TaskPriority.DEFAULT, () -> "test");
    task.setState(Task.TaskState.RUNNING);
    
    String str = task.toString();
    
    assertNotNull(str);
    assertTrue(str.contains("42"));
    assertTrue(str.contains(String.valueOf(TaskPriority.DEFAULT)));
    assertTrue(str.contains("RUNNING"));
  }
  
  @Test
  void testEqualsAndHashCode() {
    Task task1 = new Task(42, TaskPriority.DEFAULT, () -> "test");
    Task task2 = new Task(42, TaskPriority.HIGH, () -> "different"); // Same id, different priority
    Task task3 = new Task(99, TaskPriority.DEFAULT, () -> "test"); // Different id
    
    // Same instance should be equal to itself
    assertTrue(task1.equals(task1));
    
    // Null and different types should not be equal
    assertFalse(task1.equals(null));
    assertFalse(task1.equals("not a task"));
    
    // Tasks with same id should be equal even if other attributes differ
    assertTrue(task1.equals(task2));
    assertTrue(task2.equals(task1));
    
    // Tasks with different id should not be equal
    assertFalse(task1.equals(task3));
    assertFalse(task3.equals(task1));
    
    // Hash code should be consistent with equals
    assertEquals(task1.hashCode(), task2.hashCode());
    assertFalse(task1.hashCode() == task3.hashCode());
  }
}