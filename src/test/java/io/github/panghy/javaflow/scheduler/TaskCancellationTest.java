package io.github.panghy.javaflow.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests specifically for the Task cancellation behavior, focusing on
 * the array-based iteration of children during cancellation.
 */
class TaskCancellationTest {

  @Test
  void testCancelWithModifiedChildrenCollection() {
    // Create a parent task
    Task parentTask = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Track child task cancellations
    List<Integer> cancelOrder = Collections.synchronizedList(new ArrayList<>());
    
    // Create child tasks that will remove other children when cancelled
    Task childTask1 = new Task(2, TaskPriority.DEFAULT, () -> "child1", parentTask);
    Task childTask2 = new Task(3, TaskPriority.DEFAULT, () -> "child2", parentTask);
    Task childTask3 = new Task(4, TaskPriority.DEFAULT, () -> "child3", parentTask);
    Task childTask4 = new Task(5, TaskPriority.DEFAULT, () -> "child4", parentTask);
    Task childTask5 = new Task(6, TaskPriority.DEFAULT, () -> "child5", parentTask);
    
    // Set up cancellation callbacks that modify the parent's children collection
    childTask1.setCancellationCallback((timerIds) -> {
      cancelOrder.add(2);
      parentTask.removeChild(childTask3); // Remove a task that hasn't been processed yet
    });
    
    childTask2.setCancellationCallback((timerIds) -> {
      cancelOrder.add(3);
      parentTask.removeChild(childTask5); // Remove a task that hasn't been processed yet
    });
    
    childTask3.setCancellationCallback((timerIds) -> {
      cancelOrder.add(4);
      // This might not be called if childTask3 gets removed by childTask1's callback
    });
    
    childTask4.setCancellationCallback((timerIds) -> {
      cancelOrder.add(5);
    });
    
    childTask5.setCancellationCallback((timerIds) -> {
      cancelOrder.add(6);
      // This might not be called if childTask5 gets removed by childTask2's callback
    });
    
    // Add all children to the parent
    parentTask.addChild(childTask1);
    parentTask.addChild(childTask2);
    parentTask.addChild(childTask3);
    parentTask.addChild(childTask4);
    parentTask.addChild(childTask5);
    
    // Set up parent cancellation callback
    parentTask.setCancellationCallback((timerIds) -> cancelOrder.add(1));
    
    // Cancel the parent - this should safely iterate over the children even if they're modified
    parentTask.cancel();
    
    // Verify parent was cancelled
    assertTrue(parentTask.isCancelled(), "Parent task should be cancelled");
    
    // Verify all tasks that should be cancelled are cancelled
    assertTrue(childTask1.isCancelled(), "Child task 1 should be cancelled");
    assertTrue(childTask2.isCancelled(), "Child task 2 should be cancelled");
    assertTrue(childTask4.isCancelled(), "Child task 4 should be cancelled");
    
    // Check that parent's callback was called first
    assertEquals(1, cancelOrder.get(0), "Parent's cancellation callback should be called first");
    
    // Verify that the ConcurrentModificationException didn't occur
    // We can't assert exact order since those tasks might not be processed at all
    // But we know that cancelOrder should have at least the parent and the first 2 child tasks
    assertTrue(cancelOrder.size() >= 3, "Parent and non-removed children have callbacks");
  }
  
  @Test
  void testCannotAddChildrenDuringCancellation() throws Exception {
    // Create a parent task
    Task parentTask = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Track which tasks are cancelled
    AtomicInteger initialChildrenCancelled = new AtomicInteger(0);
    AtomicInteger addChildExceptions = new AtomicInteger(0);
    AtomicBoolean parentCancelled = new AtomicBoolean(false);
    
    // Create initial children
    List<Task> initialChildren = new ArrayList<>();
    for (int i = 2; i <= 4; i++) {
      final int childId = i;
      Task child = new Task(childId, TaskPriority.DEFAULT, () -> "child" + childId, parentTask);
      
      // Set cancellation callback that tries to add a new child (which should fail)
      child.setCancellationCallback((timerIds) -> {
        initialChildrenCancelled.incrementAndGet();
        
        // When cancelled, try to add a new child to the parent (should fail)
        final int newChildId = childId + 10;
        Task newChild = new Task(
            newChildId, 
            TaskPriority.DEFAULT, 
            () -> "dynamic-child" + newChildId, 
            parentTask);
        
        // Try to add the new child to the parent, which should fail
        try {
          parentTask.addChild(newChild);
        } catch (IllegalStateException e) {
          // Expected exception - parent is cancelled
          addChildExceptions.incrementAndGet();
        }
      });
      
      initialChildren.add(child);
      parentTask.addChild(child);
    }
    
    // Set parent cancellation callback
    parentTask.setCancellationCallback((timerIds) -> parentCancelled.set(true));
    
    // Cancel the parent - this should cancel all children
    parentTask.cancel();
    
    // Give a little time for all cancellations to complete
    Thread.sleep(100);
    
    // Verify parent is cancelled
    assertTrue(parentCancelled.get(), "Parent should be cancelled");
    assertTrue(parentTask.isCancelled(), "Parent task should be cancelled");
    
    // Verify all initial children were cancelled
    assertEquals(3, initialChildrenCancelled.get(), "All initial children should be cancelled");
    for (Task child : initialChildren) {
      assertTrue(child.isCancelled(), "Initial child should be cancelled");
    }
    
    // Verify that attempts to add children during cancellation failed
    assertEquals(3, addChildExceptions.get(), "All attempts to add children should have failed");
  }
  
  @Test
  void testCancelWithChildrenCallingParentRemoveChild() {
    // This test verifies the fix where a child's cancel method calls parent.removeChild(this)
    
    // Create test tasks
    Task parentTask = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Track cancellation counts to verify everything runs to completion
    AtomicInteger cancellationCallCount = new AtomicInteger(0);
    
    // Create some child tasks
    for (int i = 2; i <= 5; i++) {
      Task child = new Task(i, TaskPriority.DEFAULT, () -> "child", parentTask);
      
      // The child's cancel method will automatically call parent.removeChild(this)
      // due to the implementation in Task.cancel()
      
      // Add a cancellation callback to count completions
      child.setCancellationCallback((timerIds) -> cancellationCallCount.incrementAndGet());
      
      // Add the child to the parent
      parentTask.addChild(child);
    }
    
    // Also add a cancellation callback to the parent
    parentTask.setCancellationCallback((timerIds) -> cancellationCallCount.incrementAndGet());
    
    // Cancel the parent - this will cancel all children
    parentTask.cancel();
    
    // Verify cancellation callbacks were called for parent and all children
    assertEquals(5, cancellationCallCount.get(), "Callbacks called for parent and children");
    
    // Verify parent is marked as cancelled
    assertTrue(parentTask.isCancelled(), "Parent task should be marked as cancelled");
  }
  
  @Test
  void testCancelWithNestedChildren() {
    // Create a deep hierarchy:
    // - Parent
    //   - Child 1
    //     - Grandchild 1
    //       - Great-grandchild 1
    //       - Great-grandchild 2
    //     - Grandchild 2
    //   - Child 2
    
    // Track cancellation order
    List<Long> cancellationOrder = Collections.synchronizedList(new ArrayList<>());
    
    // Create the parent
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    parent.setCancellationCallback((timerIds) -> cancellationOrder.add(parent.getId()));
    
    // Create child 1
    Task child1 = new Task(2, TaskPriority.DEFAULT, () -> "child1", parent);
    child1.setCancellationCallback((timerIds) -> cancellationOrder.add(child1.getId()));
    parent.addChild(child1);
    
    // Create grandchild 1
    Task grandchild1 = new Task(3, TaskPriority.DEFAULT, () -> "grandchild1", child1);
    grandchild1.setCancellationCallback((timerIds) -> cancellationOrder.add(grandchild1.getId()));
    child1.addChild(grandchild1);
    
    // Create great-grandchild 1
    Task greatGrandchild1 = new Task(
        4, TaskPriority.DEFAULT, () -> "greatGrandchild1", grandchild1);
    greatGrandchild1.setCancellationCallback((timerIds) -> cancellationOrder.add(greatGrandchild1.getId()));
    grandchild1.addChild(greatGrandchild1);
    
    // Create great-grandchild 2
    Task greatGrandchild2 = new Task(
        5, TaskPriority.DEFAULT, () -> "greatGrandchild2", grandchild1);
    greatGrandchild2.setCancellationCallback((timerIds) -> cancellationOrder.add(greatGrandchild2.getId()));
    grandchild1.addChild(greatGrandchild2);
    
    // Create grandchild 2
    Task grandchild2 = new Task(6, TaskPriority.DEFAULT, () -> "grandchild2", child1);
    grandchild2.setCancellationCallback((timerIds) -> cancellationOrder.add(grandchild2.getId()));
    child1.addChild(grandchild2);
    
    // Create child 2
    Task child2 = new Task(7, TaskPriority.DEFAULT, () -> "child2", parent);
    child2.setCancellationCallback((timerIds) -> cancellationOrder.add(child2.getId()));
    parent.addChild(child2);
    
    // Cancel the parent - should propagate to all descendants
    parent.cancel();
    
    // Verify all tasks are cancelled
    assertTrue(parent.isCancelled(), "Parent should be cancelled");
    assertTrue(child1.isCancelled(), "Child 1 should be cancelled");
    assertTrue(child2.isCancelled(), "Child 2 should be cancelled");
    assertTrue(grandchild1.isCancelled(), "Grandchild 1 should be cancelled");
    assertTrue(grandchild2.isCancelled(), "Grandchild 2 should be cancelled");
    assertTrue(greatGrandchild1.isCancelled(), "Great-grandchild 1 should be cancelled");
    assertTrue(greatGrandchild2.isCancelled(), "Great-grandchild 2 should be cancelled");
    
    // Verify all callbacks were called (exact order may vary due to implementation)
    assertEquals(7, cancellationOrder.size(), "All 7 callbacks should have been called");
    assertTrue(cancellationOrder.contains(1L), "Parent callback should be called");
    assertTrue(cancellationOrder.contains(2L), "Child 1 callback should be called");
    assertTrue(cancellationOrder.contains(3L), "Grandchild 1 callback should be called");
    assertTrue(cancellationOrder.contains(4L), "Great-grandchild 1 callback should be called");
    assertTrue(cancellationOrder.contains(5L), "Great-grandchild 2 callback should be called");
    assertTrue(cancellationOrder.contains(6L), "Grandchild 2 callback should be called");
    assertTrue(cancellationOrder.contains(7L), "Child 2 callback should be called");
    
    // The parent should be first in the cancellation order
    assertEquals(1L, cancellationOrder.get(0), "Parent should be cancelled first");
  }
  
  @Test
  void testAddAndRemoveChildren() {
    // Create a parent task
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Create a child task
    Task child1 = new Task(2, TaskPriority.DEFAULT, () -> "child1", parent);
    Task child2 = new Task(3, TaskPriority.DEFAULT, () -> "child2", parent);
    
    // Add the child to the parent
    parent.addChild(child1);
    parent.addChild(child2);
    
    // Track cancellation callbacks
    AtomicInteger cancellationCallCount = new AtomicInteger(0);
    child1.setCancellationCallback((timerIds) -> cancellationCallCount.incrementAndGet());
    child2.setCancellationCallback((timerIds) -> cancellationCallCount.incrementAndGet());
    
    // Remove child1 from parent
    parent.removeChild(child1);
    
    // Cancel the parent - this should only cancel child2
    parent.cancel();
    
    // Verify only child2 was cancelled
    assertFalse(child1.isCancelled(), "Removed child should not be cancelled");
    assertTrue(child2.isCancelled(), "Child still in parent should be cancelled");
    
    // Verify only one cancellation callback was called
    assertEquals(1, cancellationCallCount.get(), "Only one cancellation callback should be called");
  }
  
  @Test
  void testRemoveChildWithNullChildren() {
    // Create a parent task
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Create a child task
    Task child = new Task(2, TaskPriority.DEFAULT, () -> "child", parent);
    
    // Try to remove a child from a parent that doesn't have children set
    // This should not throw exception
    parent.removeChild(child);
    
    // Now add the child and then remove it
    parent.addChild(child);
    parent.removeChild(child);
    
    // Add the child again
    parent.addChild(child);
  }
  
  @Test
  void testRemoveChildWhenCompleted() {
    // Create a parent task
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Create a child task
    Task child = new Task(2, TaskPriority.DEFAULT, () -> "child", parent);
    
    // Add the child to the parent
    parent.addChild(child);
    
    // Verify parent reference
    assertEquals(parent, child.getParent());
    
    // Complete the child - this should remove it from the parent
    child.setState(Task.TaskState.COMPLETED);
    
    // Cancel the parent - this should not affect the child that's already completed
    parent.cancel();
    
    // Verify parent is cancelled but child is not (since it's already completed)
    assertTrue(parent.isCancelled(), "Parent should be cancelled");
    assertFalse(child.isCancelled(), "Completed child should not be marked as cancelled");
  }
  
  @Test
  void testRemoveChildWhenFailed() {
    // Create a parent task
    Task parent = new Task(1, TaskPriority.DEFAULT, () -> "parent", null);
    
    // Create a child task
    Task child = new Task(2, TaskPriority.DEFAULT, () -> "child", parent);
    
    // Add the child to the parent
    parent.addChild(child);
    
    // Verify parent reference
    assertEquals(parent, child.getParent());
    
    // Fail the child - this should remove it from the parent
    child.setState(Task.TaskState.FAILED);
    
    // Cancel the parent - this should not affect the child that's already failed
    parent.cancel();
    
    // Verify parent is cancelled but child is not (since it's already failed)
    assertTrue(parent.isCancelled(), "Parent should be cancelled");
    assertFalse(child.isCancelled(), "Failed child should not be marked as cancelled");
  }
}