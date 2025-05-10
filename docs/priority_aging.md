# Priority Aging in JavaFlow Scheduler

This document describes the priority aging mechanism in the JavaFlow scheduler, which helps prevent task starvation by gradually boosting the priority of tasks that have been waiting for a long time.

## Overview

In a concurrent scheduling system with priority-based execution, there's a risk that lower-priority tasks might never get a chance to run if the system is continuously flooded with higher-priority tasks. This is known as "starvation." The priority aging mechanism addresses this issue by gradually increasing the priority of waiting tasks over time, ensuring that even low-priority tasks eventually get executed.

## Implementation Details

The priority aging mechanism works as follows:

1. **Task Priority Tracking**:
   - Each task has an `originalPriority` (assigned at creation) and an `effectivePriority` (used for scheduling).
   - The `effectivePriority` starts equal to the `originalPriority` but can be boosted over time.

2. **Priority Boosting**:
   - The scheduler periodically checks waiting tasks.
   - If a task has been waiting longer than a configured interval, its priority is boosted by a configurable amount.
   - Each boost is tracked with a timestamp to control the frequency of boosts.

3. **Boosting Limits**:
   - A task's priority can only be boosted up to a maximum amount from its original priority.
   - The priority will never be boosted beyond the system's CRITICAL priority level.

4. **Default Configuration**:
   - Priority aging is enabled by default with sensible parameters:
   - Priority aging interval: 10 seconds
   - Priority boost per aging: 1 level
   - Maximum boost: 10 levels

## Benefits

The priority aging mechanism provides several benefits:

1. **Prevents Starvation**: Low-priority tasks will eventually execute, even in systems with a continuous stream of high-priority tasks.

2. **Maintains Responsiveness**: The system still prioritizes genuinely high-priority tasks while ensuring fairness over time.

3. **Automatic Operation**: The priority aging mechanism works automatically without requiring any code changes in tasks or actors.

## Example Scenario

Consider a system with continuous high-priority tasks (priority 20) and occasional low-priority tasks (priority 40). Without priority aging, the low-priority tasks might never run. With priority aging:

1. After waiting for 10 seconds, a low-priority task's effective priority becomes 39.
2. After 20 seconds, it becomes 38.
3. Eventually (after 200 seconds with default settings), it reaches priority 30, which is still lower than high-priority tasks but has a much better chance of execution.
4. If it continues to wait, it eventually reaches priority 20, equal to high-priority tasks, ensuring it gets scheduled fairly.

This mechanism ensures that no task waits indefinitely while preserving the general intent of the priority system.

## Implementation Notes

- Priority aging is applied when finding the next task to execute in the scheduler.
- The implementation requires reordering tasks in the priority queue when their priorities change.
- The priority aging calculation is deterministic, ensuring consistency in tests.
- In simulation mode with a virtual clock, priority aging still works as expected, using the simulated time for aging intervals.