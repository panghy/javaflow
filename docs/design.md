# FlowJava: Software Requirements Document

## Introduction

**FlowJava** is a Java-based actor concurrency framework designed to support highly concurrent, asynchronous programming with **deterministic execution** for testing. It reimagines the core ideas of a proven C++ actor framework in idiomatic Java, leveraging modern JDK 25+ features (notably Project Loom’s virtual threads and structured concurrency) instead of any custom compiler or preprocessor. The goal is to combine the simplicity of writing sequential code with the performance of event-driven systems, all within pure Java and minimal third-party libraries. FlowJava will empower developers to write asynchronous *actors* (lightweight tasks) that communicate via **futures** and **promises**, run on a single-threaded cooperative **event loop**, and can be executed in a special *simulation mode* that reproduces complex distributed scenarios with **deterministic results**.

**Key Objectives:**

* **Actor Model & Futures:** Provide a lightweight *actor* abstraction (virtual threads) and a robust **Future/Promise** mechanism for inter-task communication. Writing an actor should feel like writing a normal sequential function using `await`-like operations, without explicit thread management.
* **Cooperative Scheduling:** Use a single-threaded **event loop** to schedule all actors in a **cooperative** manner. Avoid true parallel threads for core logic, ensuring that concurrency is achieved via interleaving tasks rather than multi-threading (which aids determinism).
* **Prioritized Execution:** Assign **priorities** to tasks so that time-critical actors run before lower-priority work. The scheduler must always run the highest-priority ready task next, preventing starvation and honoring task importance.
* **Non-blocking I/O Integration:** All I/O (network, disk, timers, etc.) will be integrated via asynchronous operations that yield futures. No actor will ever block a real OS thread on I/O; instead, I/O completions feed into the event loop as events, maintaining single-threadedness and enabling a *virtual clock* for simulation.
* **Deterministic Simulation:** Support a **simulation mode** where the entire system (multiple actors, network messages, disk events) can run in one thread with a controlled scheduler and clock. This mode allows testing with reproducible results, including **fault injection** (randomized failures) to harden the system.
* **Error Handling & Cancellation:** Integrate exceptions and cancellation deeply into the model. If an asynchronous operation fails, it should throw an exception at the await point (allowing try/catch around `await`). If an actor’s result is no longer needed, the framework should **automatically cancel** that actor and any of its dependent subtasks.
* **Logging & Debugging:** Provide rich **debugging and logging infrastructure** tailored for asynchronous actors. This includes structured event logs (with timestamps, actor identifiers, etc.), tools to trace actor call stacks at runtime, and the ability to replay or step through event sequences deterministically.
* **Idiomatic Java Implementation:** Implement all the above using pure Java (JDK 25 or later) features with minimal external dependencies. Leverage **Project Loom** for lightweight threads and **structured concurrency** for managing cancellation and scoping of tasks, rather than building a custom coroutine system from scratch. FlowJava should feel natural to Java developers and work with standard tools (profilers, debuggers, etc.) out-of-the-box.

This document details the requirements and design of FlowJava. It covers the core programming model (actors, futures, streams), the execution and scheduling model, integration with I/O and timers, the deterministic simulation capabilities, error propagation and cancellation semantics, and the debugging/logging facilities. Example Java APIs and pseudocode are included to illustrate how developers will use FlowJava to write asynchronous actor-based code.

## Core Design Principles and Architecture

FlowJava’s design centers on an **actor-based programming model** built atop a single-threaded scheduler. Key architectural principles include the actor/future abstraction, message-passing via promises, and deterministic task scheduling. This section outlines these core concepts and how they will appear in the FlowJava API.

* **Actors as Lightweight Tasks:** An *actor* in FlowJava represents an independent logical task or coroutine that runs concurrently with others. Internally, each actor will execute on a Java **virtual thread** (from Project Loom) to keep it lightweight. Actors do not share mutable state by default; they communicate by exchanging messages (values or signals) asynchronously. An actor function typically returns a `FlowFuture<T>` (FlowJava’s future type) representing a result that will be delivered later. Developers write actor code as straightforward sequential logic that can **suspend** at await points without blocking the whole program. Many actors can be in progress at once, but thanks to the single-threaded execution model, at most one will be actively running at any given moment (concurrency without parallelism).

* **Futures and Promises:** FlowJava’s primary concurrency primitives are **futures** and **promises**. A `FlowFuture<T>` is a placeholder for a result of type `T` that may not yet be available. A `FlowPromise<T>` is the completable handle for that future – it can be fulfilled with a value (or an error) exactly once. This decouples producers from consumers: an actor can return a future immediately, and some other actor (or an I/O event) will later set the corresponding promise. Actors can *wait* on futures to get results, which causes the actor to pause until the future is resolved. All actor functions in FlowJava will return `FlowFuture` instead of returning values directly; this enforces asynchronous, non-blocking behavior. For example, an actor might create a `FlowPromise<ByteBuffer>` and pass it to a disk I/O component; when the disk read completes, that component fulfills the promise with data, which in turn makes the waiting actor’s future ready with the data.

* **Message Passing via Futures:** By using futures/promises, FlowJava enables an **event-driven message-passing** style. One actor can send information to another by simply setting a promise that the other is awaiting. This is effectively a thread-safe single-use channel. The important aspect is location transparency: the sender and receiver might be on different components or even different simulated nodes, but the code for waiting and sending is identical. For instance, an actor can call another actor on a remote service by sending a request (filling a promise in that service’s incoming queue) and then waiting on a future for the response. FlowJava should allow this pattern seamlessly – if remote messaging is built on top of FlowJava, the future will be resolved when the network reply arrives, but from the actor’s perspective it’s just waiting on a local future. This principle (present in the original design) ensures the same actor logic can handle local or distributed cases uniformly. **In summary, actors don’t call each other directly; they orchestrate by creating and consuming futures.**

* **Streams for Continuous Messages:** In addition to single-value futures, FlowJava will support **streaming channels** for sequences of values. The API will include a `FlowStream<T>` (with complementary `FlowPublisher<T>` or `FlowPromiseStream<T>`) to represent a stream of messages that one actor produces and another consumes. This acts like an asynchronous queue. For example, a server actor could expose a `FlowStream<Request>` representing incoming client requests; multiple client actors can push requests into it via a `FlowPublisher<Request>`, and the server actor pulls requests one-by-one from the stream. The consumer actor uses an operation like `Flow.awaitNext(stream)` (or an iterator-style API) to wait for the **next item** on the stream. If the stream is empty, the actor will suspend until a new message arrives, then resume with that message. Streams are essential for modeling continuous event sources or actor mailboxes in a convenient way. FlowJava must ensure that stream consumption is also deterministic and that if multiple streams or events are awaited, the selection of which event to handle next is well-defined.

* **Waiting on Multiple Events (Select/Choose):** A powerful pattern in concurrent programming is waiting for *one of many* events to occur and handling whichever happens first. FlowJava will provide a construct to wait on **multiple futures or stream-next events simultaneously**, akin to a `select` or **choose** operation. In the original model, a `choose { when(F1) {...} or when(F2) {...} }` syntax allowed an actor to be suspended on both F1 and F2 and react to whichever becomes ready first. In FlowJava, we can achieve a similar outcome by offering a static API like `Flow.select(future1, future2, ...)` that returns a descriptor of which future completed first, or by allowing a lambda-based builder for choose blocks. The key requirement is **deterministic tie-breaking**: if two or more of the awaited events are ready at the same time, the one with the highest priority or the one listed first should be chosen predictably. This determinism ensures that the outcome doesn’t depend on race conditions. For example, if an actor is waiting for either a new request or a timeout to occur, and both become ready, the framework might always favor the request first (or whichever was registered first), making behavior reproducible. FlowJava’s API should make it easy to express these “wait for any of these” scenarios. A possible design is:

  ```java
  // Pseudo-code example of waiting for multiple events:
  FlowFuture<Request> nextA = streamA.next();   // future for next item from streamA
  FlowFuture<Request> nextB = streamB.next();   // future for next item from streamB
  FlowSelector<Request> selector = FlowSelector.create()
      .when(nextA, req -> { handleRequestA(req); })
      .when(nextB, req -> { handleRequestB(req); });
  selector.select(); // waits until either nextA or nextB is ready, then runs the corresponding handler
  ```

  In this example, `FlowSelector` is a conceptual helper: it registers two futures and associated actions, and its `select()` call will block the actor until one future resolves, then execute the matching action. Internally, this could be implemented by completing a promise or using `CompletableFuture.anyOf`, but with added deterministic ordering. This is just one illustrative approach – the exact API can be refined – but the requirement is that FlowJava must support **waiting on multiple futures** in one step, with a clear, deterministic resolution order. This enables patterns like concurrently fetching data from two sources and using whichever responds first, or handling multiple types of incoming events in a single actor loop.

* **Actor-Local State:** In traditional coroutine systems, when an actor yields (awaits), it might lose the current stack context unless special measures are taken. FlowJava, by virtue of using true Java threads as actors, will naturally preserve local variables across suspensions. This means an actor’s method variables and state are intact when it resumes after an `await`. In the original C++ Flow, developers had to mark certain variables as `state` to ensure they persisted, but in FlowJava this complexity is eliminated – any local variable in an actor function behaves like it would in normal synchronous code. For example:

  ```java
  FlowFuture<Void> counterActor() {
      return Flow.start(() -> {  // start a new actor (returns FlowFuture<Void>)
          int count = 0;
          while (true) {
              // Wait for a tick or event (just as a placeholder for yielding)
              Flow.await(Flow.delay(1.0));
              count++;  // this state persists across each await
              System.out.println("Tick " + count);
          }
      });
  }
  ```

  In this pseudocode, `count` will correctly increment on each loop iteration, even though the actor suspends on `Flow.delay(1.0)` each time. FlowJava ensures actor-local state continuity by using the standard thread stack to store state. This greatly simplifies actor programming (no special keywords or wrappers needed for state). However, it is still important in documentation to caution developers: any state that should not be reset on re-entry should be kept in local variables outside of callbacks. FlowJava will not reset local variables on `await` – it behaves just like a thread pausing – so developers get a straightforward mental model: **what happens within one actor’s function stays within that actor’s stack and scope, across waits**.

With these core concepts – actors & futures, promises for message passing, streams, multi-wait selects, and preserved state – FlowJava establishes its programming model. Next, we detail how these actors are executed and scheduled on a single thread to achieve concurrency without parallelism, as well as how I/O and timing are incorporated.

## Execution Model and Task Scheduling

FlowJava will employ a **single-threaded, cooperative scheduling** model to run all actor tasks. This means that, although there may be many actors (hundreds or thousands of virtual threads) alive and ready to do work, only one is ever running on the CPU at a time, and context switches occur only at well-defined yield points (such as awaiting a future or explicitly yielding). This design is crucial for simplifying reasoning about concurrency and enabling deterministic simulations. Below we outline the execution model requirements, including the event loop, task prioritization, yielding behavior, and how Project Loom is utilized under the hood.

* **Single-Threaded Event Loop:** At the heart of FlowJava is an **event loop** that continually selects a ready task (actor) and runs it for a slice of execution, then repeats. All actors run on the same OS thread (the *FlowJava main thread*) by default. This avoids the nondeterminism of preemptive multithreading – no two actor contexts truly run in parallel, so interleaving of operations is controlled by FlowJava. The event loop will be implemented either by using a dedicated Java thread that schedules other virtual threads, or by constraining the scheduler of all virtual threads to a single carrier thread. Project Loom allows custom schedulers for virtual threads, so FlowJava can create all actor threads with a custom `Executor` that uses only one underlying thread. This ensures that even though actors are *conceptually* separate threads, they are executed one-by-one in a deterministic order on a single OS thread. **No global locks** are needed for actor coordination because they don’t run concurrently; shared data is protected by design since only one actor touches the CPU at a time.

* **Task Prioritization:** Every actor (or discrete task posted to the event loop) in FlowJava will have an associated **priority level** (likely an integer or enum). Lower numeric values can indicate higher priority, for example. The scheduler must always pick the highest-priority ready task to run next. This allows critical operations (e.g. heartbeats, coordination messages) to preempt less important work (like background cleanup). FlowJava should define a set of priority levels for common categories (for example: critical, default, low, idle) and allow tasks to specify a priority when scheduled. If not specified, a default priority is used. The system should also prevent starvation of low-priority tasks: if a low-priority task never gets to run because higher ones keep coming, FlowJava can implement **priority aging** – gradually raising the priority of tasks that have been waiting longer. This ensures fairness over time. The scheduler’s priority queue will likely be the core data structure: tasks ready to run are kept in a structure sorted by priority (and possibly FIFO order within the same priority).

* **Cooperative Multitasking and Yields:** Actors yield control in FlowJava by awaiting on futures or by explicitly yielding. Since the scheduling is cooperative, an actor will continue to run until it either: (a) awaits a `FlowFuture` that isn’t ready (at which point it **blocks** its virtual thread and the scheduler will switch to another task), or (b) explicitly calls a yield operation, or (c) completes. FlowJava will provide a utility future `Flow.yield()` (of type `FlowFuture<Void>`) that an actor can await to voluntarily give up the CPU. For example, if an actor is performing a long computation or loop, inserting `Flow.await(Flow.yield())` periodically will suspend the actor and let others run, resuming it in the next loop cycle. This prevents any single actor from monopolizing the event loop. FlowJava should encourage (or even automatically detect) long-running tasks to yield. We may implement a diagnostic that if an actor runs for too long without an await, we log a warning (similar to the original system’s "SlowTask" log). In summary, **await** calls (on I/O, on timers, or on yields) are the *only* points where context switches occur. There is no preemptive timeslicing by the OS; thus, developers must ensure their actor code reaches an await regularly. The benefit is that scheduling is entirely deterministic and under the framework’s control – no random preemption mid-calculation.

* **Event Loop Mechanics:** The main event loop will intermix **actor execution** and **I/O event polling** in each iteration. In pseudocode, the loop might look like this:

  ```java
  while (!shutdown) {
      // 1. Run one ready actor task (highest priority first)
      ActorTask task = readyQueue.poll();  // get next task to run
      if (task != null) {
          task.resume();  // resume the actor's execution for a time-slice
      }

      // 2. Process one pending I/O or timer event, if any
      Event event = ioEventQueue.poll();  // get next completed I/O or timer event
      if (event != null) {
          event.completePromise();  // fulfill the promise associated with that I/O
      }

      // 3. If no actors are ready, wait for the next I/O event (blocking the loop briefly)
      if (readyQueue.isEmpty()) {
          waitForNextIOEvent();  // e.g., block on a selector or sleep until next timer
      }
  }
  ```

  This structure ensures that CPU-bound actor tasks and external events (like network or disk readiness) are processed in a controlled, alternating manner. In practice, the implementation might be more complex (to batch multiple actor runs before checking I/O, etc.), but the requirement is that **all sources of wake-ups funnel through the FlowJava scheduler**. For instance, when a socket becomes readable or a timer expires, that triggers an event which enqueues a corresponding promise’s completion; the actual handling of that event (resuming whatever actor was waiting on it) happens via the event loop. This way, even external events don’t interrupt running actors arbitrarily – they wait their turn. This approach closely mirrors the proven design where an external I/O library was polled for at most one event per loop iteration, giving the framework control over interleaving. FlowJava should use Java’s own facilities (see I/O section) to achieve the same effect.

* **Integration with Loom (Virtual Threads):** FlowJava will create each actor as a Loom **virtual thread** but ensure they execute cooperatively on one carrier thread. The scheduler design will likely involve a custom `Executor` for virtual threads that never runs two tasks in parallel. One strategy is: create a single-threaded executor (with an unbounded queue) and configure `Thread.ofVirtual().scheduler(executor)` when launching actor threads. This way, when an actor calls `Flow.await(someFuture)`, the Loom runtime will park that virtual thread and free the carrier (the single executor thread) to run another task from its queue. That other task could be another actor that’s ready. When the awaited future completes, its promise fulfillment will enqueue the continuation of the waiting actor back into the executor’s queue. This effectively implements our event loop using Loom’s scheduling. **Requirement:** The implementation must confirm that only one carrier thread is active (for determinism), except possibly for separate threads handling actual OS I/O waits (discussed later) which will communicate back to this scheduler in a synchronized way. By leveraging Loom, we avoid writing our own context-switching or stack management – the JVM handles pausing and resuming the actors as virtual threads, which simplifies the implementation enormously (and avoids any need for bytecode instrumentation or code generation). The execution model described (single-thread, cooperative) is thus enforced by the combination of using one carrier thread and the natural blocking points of virtual threads.

* **Handling Slow Tasks:** FlowJava should include mechanisms to detect and handle actors that overrun their time. Since there’s no timer preemption, an actor that forgets to yield could stall the system. Borrowing from prior art, if an iteration of the event loop takes longer than a certain threshold (e.g. 100ms) without returning to the loop, FlowJava can log a **Slow Task** warning. This warning might include which actor or operation was running and possibly a snapshot of its stack trace. Developers can use this to pinpoint performance issues. The requirement is to have some monitoring of loop latency and the option to record diagnostics when it gets too high. Additionally, FlowJava could support an emergency mechanism: for example, if in debug mode, allow an admin to interrupt a stuck actor (though normally cancellation is cooperative too). Logging of slow tasks is further discussed under Debugging.

* **Task Lifetime and Implicit Cancellation:** An important aspect of scheduling is how tasks terminate and how the system stops tasks that are no longer needed. FlowJava will implement **automatic cancellation propagation**. If a `FlowFuture` is abandoned (garbage-collected or explicitly cancelled), the system should stop the corresponding actor task from continuing, to avoid doing useless work. In the original design, dropping the last reference to a future would trigger an exception (`actor_cancelled`) inside the actor to unwind it. In Java, garbage collection doesn’t immediately notify us of lost references, so FlowJava may need an explicit API to cancel futures or use a structured concurrency approach. The requirement is: **If actor A spawns actor B and then A no longer needs B’s result, it should be easy to cancel B**. Ideally, if A simply doesn’t await B (e.g., goes out of scope), B should get cancelled. We can achieve something similar by using **structured concurrency** – e.g., if A starts B within a `StructuredTaskScope` and then A’s scope closes, B is automatically interrupted. FlowJava should explore using `StructuredTaskScope` under the hood for grouping parent/child tasks. At the very least, we will provide an API like `FlowFuture.cancel()` that signals cancellation, and ensure that actors regularly check for cancellation (via `InterruptedException` or a flag) at await points. When a cancellation is detected, FlowJava will unwind the actor’s stack (for example, by throwing a `FlowCancelledException` that the actor might catch or, if uncaught, will simply terminate the actor). The scheduler must then remove the cancelled task from the ready queue if it was waiting. Furthermore, any futures that the cancelled actor was going to set should be marked as cancelled or error, propagating the cancellation downstream. This cascading cancellation feature is critical for building timeouts and bounding resource use. For instance, if a client request times out, the entire chain of actors handling that request should be torn down promptly. In summary, **the scheduler and future system must cooperate to remove cancelled tasks and propagate cancellation events**, all without requiring a lot of manual code in the actors themselves.

In essence, FlowJava’s execution model combines a **deterministic event loop** with priority scheduling and cooperative multitasking. By using one thread of execution and explicit yield points, it guarantees that given the same sequence of events, tasks will interleave in a predictable way every time. This lays the foundation for the deterministic simulation mode described later. Next, we discuss how external I/O and timers are folded into this single-threaded model.

## Asynchronous I/O and Timers Integration

For a concurrency framework to be practical, it must interface with real-world I/O – network sockets, file reads/writes, timers, etc. FlowJava will adopt a fully asynchronous, non-blocking approach to all I/O, so that even though we have a single-threaded core, I/O operations do not stall the event loop. Instead, all I/O operations will return futures that complete when the I/O is done. This section describes how FlowJava will integrate with Java’s I/O capabilities and manage timers, both in real mode and simulation mode.

* **Non-Blocking I/O via Futures:** In FlowJava, any network or disk operation should be initiated asynchronously and represented by a `FlowFuture`. For example, reading from a file might be done with an API like `FlowFileHandle.read(offset, length)` returning `FlowFuture<ByteBuffer>`. An actor can `Flow.await()` that future to get the data once the read completes. Under the hood, there are a few ways to implement this in Java:

    * Using Java NIO (Non-blocking I/O) with selectors: e.g., for sockets, use a single `Selector` on the main thread that monitors multiple channels, similar to how Boost.ASIO is used in the C++ version. When a channel is readable or writable, the selector wakes up, and FlowJava then completes the corresponding promise and resumes the waiting actor.
    * Using Java’s asynchronous channels or completable futures: e.g., `AsynchronousSocketChannel` or `CompletableFuture.supplyAsync` with a thread pool. However, careful: using a thread pool could violate single-thread determinism if results come back concurrently. A safe approach is to perform actual blocking I/O on separate helper threads (or use OS async APIs), but funnel the completion back into the **FlowJava event loop thread**.
    * For disk I/O, Java has `AsynchronousFileChannel` which can signal completion via a callback or Future. FlowJava would wrap that in a `FlowFuture`. If using synchronous File I/O, one might dedicate a small thread pool to do blocking reads/writes, but any callback from those threads must *enqueue* a completion event into the FlowJava scheduler rather than immediately executing actor code.

  The requirement is that **FlowJava’s main loop remains responsive and never blocks on I/O**. Instead, it uses either OS-level async I/O or background threads. When data is ready, a **promise fulfillment event** is queued. By controlling how many such events are processed per loop iteration (for example, one at a time), FlowJava maintains deterministic ordering of I/O events. Two network packets arriving at the same time will be handled one after the other in a defined order, not in parallel. This determinism extends to file I/O completions as well. To implement this, FlowJava might maintain an internal **I/O event queue** (as seen in the pseudocode earlier). The sources feeding this queue could be:

    * A dedicated I/O monitor thread that waits on a Java `Selector` (for sockets) and posts events.
    * Callback handlers for `AsynchronousFileChannel` that put completion events into the queue.
    * Timer events from a scheduler (detailed below).

  Ultimately, in real-world mode, FlowJava likely uses a combination of Java NIO and scheduled tasks. The design should hide this from the actor developer: to them, it appears that calling `FlowSocket.send()` gives a future they can wait on, which completes when the send is done (or errors if the socket closed), etc. **All I/O APIs in FlowJava should return a `FlowFuture` rather than blocking**. This uniform approach means the same `Flow.await()` mechanism handles both internal waits (between actors) and external waits (on device I/O).

* **Timers and Delays:** Timers are essential for timeouts, periodic tasks, and simulation of delays. FlowJava will include a utility like `Flow.delay(double seconds)` returning `FlowFuture<Void>` which becomes ready after the specified duration. In production mode, implementing `delay` could use Java’s `ScheduledExecutorService` or `Timer` to schedule a task that completes a promise after the given time. However, to keep consistency, we would likely **not** run the scheduled task’s callback directly on a different thread; instead, we schedule a timer event that will be picked up by the main event loop. Concretely, FlowJava can maintain a **min-heap of timers** (ordered by next expiration time) within the main thread. Each iteration of the loop can check the head of this heap to see if the earliest timer is due to fire. If so, it completes that timer’s promise and pops it. Additionally, when the event loop would otherwise go to sleep waiting for I/O, it should calculate how long to sleep based on the next timer deadline. This way, timers integrate cleanly with the event loop without needing separate threads per timer. The `Flow.delay` future in simulation mode will use a virtual clock (discussed later), but in real mode it uses the system clock. FlowJava should provide a way to get the current time (`Flow.now()` perhaps) which will give either real wall-clock time or simulated time depending on mode. One subtlety: ensure that time-based wakes and I/O wakes are coordinated. In our event loop pseudocode, `waitForNextIOEvent()` could actually be implemented as something like `selector.select(timeout)` where `timeout` is the difference between now and the next timer event, so that the thread sleeps until either I/O or the timer occurs, whichever first.

* **Ensuring Deterministic Order of Events:** A core requirement for FlowJava is that the order in which events (I/O completions, timers, actor resumes) are processed is deterministic or at least controllable. By running a single thread and pulling at most one external event per loop iteration, we impose an order. If multiple events (say two sockets readable) are ready at once, the one our code polls first will be handled first. We can define that ordering (for instance, always handle at most one network event then one timer, etc.) to avoid race conditions. The original system emphasizes that by controlling event injection, we prevent the OS from invoking callbacks concurrently or unpredictably. FlowJava should adhere to this: for example, if using `Selector`, call `selector.selectNow()` or similar within the loop and handle each selected key individually rather than letting multiple callbacks queue up simultaneously. This deterministic event handling is especially crucial for the simulation mode, where we will often simulate I/O.

* **Example – Network Receive:** Suppose an actor is waiting on data from a socket via `Flow.await(socket.receive())`, where `socket.receive()` returns a `FlowFuture<ByteBuffer>`. Under the hood, FlowJava registers that socket with a selector for read events. The actor’s virtual thread is suspended. When the socket actually has data (OS signals readability), our I/O monitor (which might actually be the same thread doing `selector.select()` if no actor is running, or a separate helper thread that then notifies the main loop) will enqueue an event. The event will carry the data (or indicate error/closure) and link to the promise inside that `FlowFuture`. In the event loop, when we `processNextIOEvent`, we fulfill the promise with the read data. This automatically marks the `FlowFuture` as ready. Loom’s scheduler then can resume the actor’s virtual thread, but note: we will ensure that resumption happens via our executor (so it doesn’t preempt the current loop iteration). Essentially, after processing the I/O event, the waiting actor is placed back on the ready queue. In the next iteration of the loop, the scheduler will see that actor now has a result and is ready to run; it will then resume the actor code after the `await` call, now with the data available. All of this happens on one thread in a controlled sequence.

* **Disk I/O and Thread Pools:** For file operations, if using asynchronous channels isn’t feasible or if simplicity is desired, FlowJava might use a small thread pool for disk I/O. The threads in this pool perform blocking reads/writes and then schedule completions on the main loop. The requirement is to **serialize those completions on the main thread**. This means even if multiple file operations finish in parallel, we queue each result and the event loop will handle them one by one. The trade-off is a slight loss of determinism because two disk ops finishing at nearly the same time could be enqueued in either order depending on thread scheduling. However, we can mitigate that by controlling the thread pool (e.g., using a single I/O thread for all disk ops, or using a lock to ensure insertion order by request time). In practice, as long as we document that true simultaneity is rare and provide consistent tie-break rules (maybe order by request initiation), this should be acceptable.

* **Minimal Dependencies:** To keep the implementation lean, FlowJava will primarily rely on JDK classes for I/O (like `java.nio.channels.Selector`, `SocketChannel`, `ServerSocketChannel`, `AsynchronousFileChannel`, etc.) rather than pulling in a large external library. This satisfies the requirement of minimal third-party dependencies. One possible external library could be something like Netty or an event-loop library if we wanted, but since Java’s NIO is quite capable, we aim to use that. The design should be abstracted enough that if a different mechanism is needed (say, using epoll directly or a custom JNI library for performance), it could be slotted in behind the same `INetwork` or `IFileIO` interface. But initially, the JDK should suffice.

In summary, FlowJava will wrap all networking and disk operations in futures that integrate into the single-threaded event loop. Timers are treated similarly as scheduled events. The system will ensure that only one such event is processed at a time, preserving the deterministic, cooperative nature of execution. This design not only makes concurrency safe and predictable, but also sets the stage for the **Deterministic Simulation Mode**, where these real-world interfaces are replaced with simulated ones.

## Deterministic Simulation Mode and Fault Injection

One of FlowJava’s flagship features is a **Deterministic Simulation Mode**. In this mode, the framework can simulate an entire distributed system (multiple actors representing different processes/nodes, networking between them, disk operations, etc.) in a single-threaded environment with a **virtual clock**. By doing so, it becomes possible to run thousands of randomized test scenarios and get bit-for-bit reproducible results given the same random seed. This capability is crucial for testing and debugging complex systems. The following are the requirements and design for FlowJava’s simulation mode:

* **Single-Process Simulation of Multiple Nodes:** In simulation mode, FlowJava should be able to model a cluster of nodes (processes) within one OS process (one Java VM) and one thread. To accomplish this, all external interfaces (network, disk, timers, threads) will be replaced by **simulation stubs**. For example, instead of real network sockets, we use in-memory queues to represent network links; instead of actual disk I/O, we use an in-memory data store or a controlled file abstraction; instead of wall-clock time, we use a manually advanced **virtual clock**. The event loop in simulation mode does **not** call any OS I/O waits at all – it purely manages a priority queue of scheduled events (like messages to deliver at a certain time, timers set to go off, etc.). When an actor in simulation does something like `Flow.delay(5)`, the framework will schedule a wake-up event for that actor 5 seconds later *in simulated time*, placing that event into the priority queue. The event loop will then proceed to run whichever event is next in simulated time order.

* **Virtual Clock and Time Jumping:** Instead of using real time, simulation mode uses a **virtual clock** that starts at 0 and only advances as the simulation scheduler dictates. The scheduler looks at the pending event queue (which contains things like “actor X resumes at time T” or “message arrival at time T”) and always executes the next event in chronological order. If the next event is at time T and the current time is t (t < T) and no other work can happen before T, the simulator will advance the clock forward to T instantly. This means the simulation can skip idle periods quickly. If two events are scheduled for the exact same timestamp T, we define a deterministic tie-break (e.g., FIFO order of scheduling or a fixed priority among event types). We might also avoid scheduling two events at exactly the same time when possible by adding a tiny epsilon difference to ordering. The **key** is that given the same sequence of actions and random choices, the timeline is fully deterministic; there’s no dependence on real timing or thread races.

* **Simulated Networking:** FlowJava should include a **simulated network layer** in which each simulated node or component can send messages to others through in-memory constructs. For example, if an actor on Node A “sends” a message to Node B, instead of a real socket, the send can be modeled as enqueuing the message into Node B’s receive queue (which could be a `FlowStream` of incoming messages for B). We can then schedule a future event for the message “arrival” after some simulated network delay. This delay can be drawn from a distribution or constants and is subject to fault injection (see below). The network simulation should support:

    * **Latency and Bandwidth simulation:** e.g., if a message is X bytes, we might simulate it arriving after `baseLatency + (X / bandwidth)` seconds.
    * **Packet loss/reorder:** The simulator should sometimes drop messages or deliver them out of order (if such faults are being tested).
    * **Partitioning:** We might simulate network partitions by disabling message delivery between certain nodes for a period.

  Each simulated node will presumably run its own set of actors (representing that process’s logic) but they all share the one thread. We distinguish nodes by an identifier (like an integer node ID). The simulated network will tag messages with source and destination IDs and only deliver if currently allowed. FlowJava could implement this via an interface like `NetworkInterface` with a real implementation (for actual sockets) and a simulated one (for which you can call `network.send(src, dest, message)` and it schedules a receive on dest). The requirement is that **the same actor code should work in both simulation and real modes** – meaning we abstract the transport. If an application uses a `FlowSocket` or `FlowConnection` API, in real mode it might wrap a TCP socket, and in simulation mode it’s backed by the in-memory network. This approach mirrors having an `INetwork` interface with two implementations.

* **Simulated Disk:** Similar to network, disk operations can be simulated. A simulated disk might be a simple in-memory map or a file on the host that doesn’t actually obey normal timing. We will intercept calls like `open file`, `read file`, etc., and instead of performing them immediately, we can choose to simulate delays. For example, a simulated read could immediately retrieve data from an in-memory structure (since we want correctness), but we then delay the completion of the future to simulate disk seek and read time. We can parameterize disk latency (maybe randomize it a bit). We can also simulate disk errors (like a read that fails or data corruption) using the fault injection mechanisms. The requirement is to have a **controllable disk I/O simulation** such that tests can include scenarios of slow or failing disks. In practice, we might have a global flag that if simulation mode is on, any call to the real file I/O layer is replaced with calls to a `SimulatedFileSystem` singleton.

* **Multiple Virtual Processes:** In simulation, FlowJava will effectively multiplex many virtual “processes” onto one thread. To keep things logically separated, the framework may associate each actor with a context of which simulated process it belongs to. This context could include things like an identifier, a separate set of state (like each process might have its own memory space or data store segment). When sending messages, the network simulation will deliver only to actors in the target process context. We might simulate process failures by halting all actors in that context and clearing their state (to mimic a crash). The design should allow creating N instances of a service within one JVM and isolate them except via the simulated network. An example API could be:

  ```java
  SimulationContext ctx1 = FlowSimulation.createContext("Node1");
  SimulationContext ctx2 = FlowSimulation.createContext("Node2");
  FlowSimulation.runInContext(ctx1, () -> {
      // start some actors for Node1
  });
  FlowSimulation.runInContext(ctx2, () -> {
      // start some actors for Node2
  });
  ```

  Internally, contexts might just tag the actors and provide separate networking endpoints. The scheduler would still manage one event queue but would respect context boundaries when needed (like for network routing or failure injection).

* **Deterministic Randomness:** In simulation tests, any random choice must be derived from a single **pseudorandom number generator (PRNG)** with a known seed. FlowJava will use a global simulation PRNG when in simulation mode. All components that need randomness (network delay variation, deciding whether a fault occurs, random client actions, etc.) must draw from this PRNG. By seeding it with a specific value at the start of a run, we ensure repeatability. For example, if a test fails with seed 42, running the simulation again with seed 42 should reproduce the exact same sequence of events, down to timing and faults. This is a non-negotiable requirement for effective debugging. FlowJava should make it easy to set the simulation seed and perhaps log it. Additionally, consider providing convenience methods like `FlowSimulation.nextRandomDouble()` for components to use, to centralize usage of the PRNG (or pass the PRNG via context).

* **Fault Injection Framework:** To truly test robustness, simulation mode will include a **fault injection** system. Inspired by the original’s BUGGIFY macro, FlowJava can have strategically placed checks that randomly induce failures when enabled. For instance, there could be a hook in the disk read code that says “if fault injection is on and PRNG roll < 5%, simulate an I/O error here”. Fault injection points might include:

    * Randomly throw exceptions in certain actor routines (to simulate rare errors).
    * Introduce extra delays in messages or operations.
    * Drop or duplicate network messages occasionally.
    * Corrupt data (flip bits) in memory or disk.
    * Cause timers to fire late or early.
    * Simulate a node crash (e.g., at some point, stop all actors in a context).

  The framework could have an API like `FlowFault.inject(String faultName)` that returns true with some probability if that fault site should trigger. The probability and activation of faults would be controlled by the global seed and possibly scenario configuration. We should allow enabling/disabling fault injection easily (e.g., off in production, on in simulation). The requirement is to make it **straightforward to sprinkle fault triggers throughout the code** and to ensure those triggers behave deterministically under a given seed. Over many simulation runs with different seeds, we expect different combinations of faults to occur, providing a broad test coverage. Logging is important: whenever a fault triggers, FlowJava should log it (so that in a failure trace we know which faults happened).

* **Testing Workloads in Simulation:** Beyond just the infrastructure, FlowJava’s simulation mode should support running special **test actors or workloads** that simulate client behavior or orchestrate events. For example, one could write an actor that periodically kills and restarts a simulated node (to test recovery), or an actor that generates random transactions to run against a database service built on FlowJava and then verifies invariants. FlowJava should not hardcode any particular workload, but it should make it easy to launch additional actors that act as “test drivers” in simulation. We might include some utility to combine multiple scenarios, or at least guidelines on structuring test actors. At the end of a simulation run, these test actors can produce a result or log outputs that indicate success/failure of the test conditions. The requirement here is mostly to ensure that simulation mode is flexible enough to host not just the system under test, but also the test logic itself, all within one run. Since everything is deterministic, if a test actor finds an inconsistency, we have the seed to reproduce it.

* **Seamless Mode Switching:** FlowJava must be architected such that the *same code* can run in real mode or simulation mode. This implies strong abstraction boundaries. Concretely, define interfaces or classes for key environmental functions:

    * `FlowRuntime` or `FlowEnvironment` which provides methods like `currentTime()`, `scheduleTimer()`, `openSocket()`, `openFile()`, etc. In real mode, `FlowRuntime` is implemented with real I/O, and in simulation, with the fake implementations. Possibly implemented as a singleton or a context object.
    * The selection of mode could be global (a static flag) or explicit (passing a context to actor startup functions). A global toggle is simpler: e.g., `FlowJava.setSimulationMode(true, seed)`.
    * All underlying subsystems (network, disk, scheduling) check this mode and delegate accordingly.

  The requirement is that **no application-level actor code needs to change** to run under simulation. They will call the same APIs like `FlowSocket.connect()` or `Flow.delay()` regardless, and depending on mode, they either do real stuff or simulated stuff. This is a powerful feature as it lets one write a distributed algorithm and then test it thoroughly in one process.

* **Performance in Simulation:** Although simulation is single-threaded, it should be efficient. The design with time-jumping means we don't waste time waiting on actual delays. Also, by avoiding actual network and disk operations (unless we choose to simulate some actions by actually doing file I/O for realism), we reduce overhead. However, simulation will execute a lot of events and context switches, so FlowJava should be optimized for that scenario. For example, the overhead of creating a virtual thread per actor might be fine, but if simulation constantly creates and destroys actors, that could be heavy – we might consider actor pool reuse or lighter weight coroutine handling for simulation if needed. These are optimizations; the requirement at the high level is to be able to run **many** simulation iterations (potentially thousands of seeds, each simulating many “seconds” of virtual time and many events) in an automated fashion. Logging and debugging features (next section) will tie in by providing ways to capture what happened in a failing simulation run for analysis.

In conclusion, FlowJava’s simulation mode will recreate the “world in a box” concept: all interactions are controlled, and with a given random seed, the execution is purely deterministic. This enables exhaustive testing and is a core reason for building FlowJava. Properly implementing simulation mode requires up-front design of pluggable interfaces and careful attention to ordering of events, but it will yield a highly robust system. Next, we discuss how errors are handled in this framework and how cancellation and exceptions propagate through futures.

## Error Handling and Cancellation Semantics

Robust error handling is vital in an asynchronous system. FlowJava must handle exceptions and cancellations in a way that is intuitive for developers writing sequential-looking code, yet consistent with the future-based model. This means making sure that if a `FlowFuture` completes exceptionally, any actor waiting on it receives an exception, and that exceptions propagate up and down chains of actors appropriately. Similarly, when an operation is cancelled (perhaps due to a timeout or the caller giving up), that cancellation should cascade to any sub-operations. Below are the requirements and design for FlowJava’s error and cancellation model:

* **Exceptions Propagate via Futures:** In FlowJava, if an asynchronous operation fails, it should be represented as a **failed future** rather than a normal return. This is analogous to how in many promise/future frameworks a failure is not a returned error code but an exceptional completion. We will likely define a class `FlowException` (or use Java’s `Exception`) to represent errors. When a `FlowPromise` is set, it can either be fulfilled with a value or completed with an exception. If an actor awaits a future that has completed with an exception, the `Flow.await()` call should throw that exception, allowing the actor to catch it with a standard Java try/catch block. For example:

  ```java
  FlowFuture<Data> f = fetchDataAsync(); 
  try {
      Data result = Flow.await(f);  // if f completed exceptionally, this throws
      process(result);
  } catch (FlowException e) {
      // handle the error
      log.error("Failed to fetch data: " + e.getMessage());
      // possibly perform compensation or retry
  }
  ```

  The above pattern is critical: it lets asynchronous code handle errors in a straight-line manner, very much like synchronous code would. Under the hood, what happens is: if `fetchDataAsync()` encountered an error (say a network failure), it would have completed its promise with an exception. `Flow.await` sees the future is ready but marked as failure, so it doesn’t return a Data; instead it throws the exception that was stored. We will ensure that `FlowException` can wrap specific error codes or types (e.g., a subclass `FlowTimeoutException` for a timeout). This model follows the original’s approach where `wait(f)` throws if f had an error. It means **the error handling site is at the await point, not inside the async function that produced the future**. That is a shift from typical Java where a method might throw an exception directly. Here the method returns a future and later the awaiting code throws. FlowJava must document this clearly so developers know to catch exceptions around `Flow.await` calls.

* **Structured `try/catch` in Actors:** FlowJava actors will use normal Java `try/catch/finally` for error handling. Because the actor code runs in a normal thread context (virtual thread), standard exception mechanisms apply. We require that exceptions thrown inside an actor that are not caught within that actor will propagate out and complete that actor’s `FlowFuture` exceptionally. For instance, if an actor function does not catch an IOException thrown during an await, then the `FlowFuture` returned by that actor will be completed with that IOException as the cause. Any other actor waiting on that future will then experience an exception in its await, as described. We should encourage patterns like retry loops using exceptions. For example, a transaction actor might look like:

  ```java
  FlowFuture<Void> doTransaction() {
    return Flow.start(() -> {
      while (true) {
        try {
          // ... perform some operations
          Flow.await(commit());  // commit returns FlowFuture<Void>
          return null;  // success
        } catch (TransientFailure e) {
          Flow.await(delay(0.1));  // wait a bit before retry
          // loop and retry
        }
      }
    });
  }
  ```

  In this pseudocode, if `commit()` fails with a `TransientFailure`, the await throws it. The catch catches it and the actor decides to retry after a delay. This pattern should be seamless in FlowJava, making use of exceptions as signals. One thing to clarify: Java’s `CompletableFuture` by default wraps exceptions in `ExecutionException`. FlowJava will likely avoid that by managing its own future type where the exception can be thrown directly (or perhaps by using `join()` which throws unchecked exceptions). We may implement `FlowFuture.get()` to throw the original exception to avoid double wrapping. The requirement is that the **developer sees the original exception in their catch**, not some wrapper (except maybe an `FlowException` base class if we choose).

* **Cancellation Propagation:** As discussed in the scheduling section, cancellation is a first-class concept. When an operation is cancelled, we want to tear down the whole chain of subtasks. In FlowJava, cancellation might be triggered by:

    * An explicit call to `future.cancel()` by user code (for example, a client gives up on a request).
    * A structured concurrency scope closing (if we integrate with `StructuredTaskScope`).
    * A timeout expiring (which under the covers might just call cancel on a future).

  When an actor is cancelled, how do we implement it? One approach is to use thread interruption: interrupt the virtual thread running that actor. Loom allows interrupts which will flag the thread and can cause blocking operations to throw `InterruptedException`. We could make `Flow.await` check `Thread.interrupted()` as well and throw a `FlowCancelledException` if set. Alternatively, we directly complete the actor’s future with a special cancellation exception and schedule the actor to resume only to immediately get that exception. The original did it by injecting an exception at the wait point. In Java, maybe simpler: call `actorThread.stop()` is not safe (deprecated). Instead, maybe we rely on cooperative cancellation: each time an actor goes to await or does a yield, it should check a flag. If cancelled, throw. We will implement `FlowFuture.cancel()` such that:

    * It marks the corresponding promise/future as cancelled (which could just be an exceptional completion with `CancelledException`).
    * If the future corresponds to an actor that’s currently waiting, that actor’s `Flow.await` will throw `CancelledException`.
    * If the actor isn’t waiting (maybe executing CPU work), we rely on it periodically checking `Flow.isCancelled()` or performing yields where the cancellation can be noticed. In practice, since most of the time actors are either waiting or will eventually wait for something, they’ll catch it soon.
    * If we adopt structured concurrency (`StructuredTaskScope`), then cancellation can be integrated: e.g., using `scope.shutdownNow()` will interrupt all tasks in that scope. Our actor tasks would then be interrupted, causing their blocking operations (which includes `Flow.await` internally maybe using `Future.join()`) to throw.

  The requirement is that **cancelling a future stops the corresponding actor and all its children**. Children here means if actor A was waiting on B (or spawned B), and A is cancelled, then B should be cancelled too. In structured concurrency terms, that means tasks in a scope are all tied together. FlowJava can model parent-child relationships by tracking, when an actor starts another actor, we register the parent as a dependent. Then if parent cancels, traverse to cancel the child. We could maintain this in a map of Futures to children futures. Alternatively, lean on structured tasks so the JVM does that tracking for us. Many design options exist, but from the requirements perspective:

    * Provide a `Flow.cancel(Future<?> f)` operation (and maybe `future.cancel()` method) to initiate cancellation.
    * Document that not awaiting a future (letting it drop) *may* result in cancellation, but because of GC unpredictability, it's safer to explicitly cancel or use try-with-resources scopes.
    * Use a specific exception type for cancellation (so it can be identified). The original used an error code for `actor_cancelled`. We can use a `FlowCancelledException` (possibly a subclass of `FlowException` or even reuse `java.util.concurrent.CancellationException`).
    * Ensure that cancellation is not considered an “error” in logs by default (since it can be routine, like a request timeout).

  One nice-to-have: if an actor wants to explicitly check cancellation (to clean up early), we can provide `Flow.isCurrentActorCancelled()` or simply rely on `Thread.currentThread().isInterrupted()`.

* **Timeouts as Cancellation:** A common pattern is waiting with a timeout. FlowJava can support this by combining futures: e.g., a `Flow.withTimeout(future, duration)` that returns a future which completes with either the original future’s result or a timeout exception if the time passes first. Under the hood, that could start a timer and use the choose mechanism to race the two. If the timer “wins”, it cancels the original future (propagating cancellation to that actor). This shows how the pieces (timers, choose, cancellation) integrate: **the requirements all work together to allow higher-level patterns** like timeouts, retries, etc., to be built cleanly.

* **Error Codes and Typed Exceptions:** We should consider defining a set of common error conditions (as the original had specific error codes for things like “transaction\_cancelled” or “timed\_out”). In Java, using different exception classes (or subclasses of FlowException) might be more idiomatic than numeric codes. For example: `FlowTimeoutException`, `FlowNetworkError`, `FlowDiskIOError`, etc. These can carry context or error codes if needed. The framework can provide these, and developers using FlowJava can catch specific ones if they want to implement alternate logic. For instance, an actor might catch `FlowTimeoutException` to trigger a retry, or catch a generic `FlowException` to log and abort. The exact list of errors should be designed once we know the domain (for a database, you’d include things like commit\_failed, etc., but FlowJava as a library might keep it generic). The requirement is simply that **the error model is well-defined** – i.e., how to represent a failure, how it propagates, and how user code should handle it.

* **No Silent Failures:** If an exception in an actor is never caught at any level, we must decide what happens. In a long-running server, an unhandled exception in an actor could just mean that actor’s future completes with an error that no one ever awaited. Ideally, nothing should just vanish silently. If an actor’s error bubbles up to no handler, FlowJava should log it at least. Possibly, if it’s a critical actor (like main), it might shut down the process or simulation. The original system treated uncaught exceptions as usually fatal or test failures. For FlowJava, we require:

    * Unhandled exceptions in actors are captured and logged (with the actor identity and stack trace) to aid debugging.
    * In simulation mode, an unhandled exception could cause the simulation run to be marked as failed (maybe even throw an assertion so the test stops).
    * Optionally, allow registering a global exception handler for actors, so applications can decide (like a callback if any actor fails catastrophically).

  But by default, since every actor returns a future, if the user ignores that future, they are essentially saying "I don't care if it fails". It might be okay to drop it, but at least a debug log helps. We likely will adopt the stance that properly written actor code should join all important futures or use scopes so that nothing important is lost.

To summarize, FlowJava’s error handling model uses **exceptions as the mechanism for failure signaling**, making async code resemble sync code in structure. Cancellation is treated as a controlled form of exception that can propagate through chains of tasks automatically, preventing wasted work. These features combined will make it easier to write resilient systems: actors can catch exceptions and retry or compensate as needed, and if something truly goes wrong, it’s propagated predictably to whoever is waiting for that result. Next, we turn to the tools and infrastructure FlowJava will provide for debugging, tracing, and logging in such an asynchronous environment.

## Debugging, Logging, and Monitoring

Developing and operating an actor-based asynchronous system can be challenging, so FlowJava must include robust **logging, tracing, and debugging tools** to help understand system behavior. Key requirements are structured logging of events, the ability to trace actor relationships (which actor spawned or is waiting on which), deterministic replay of issues, and integration with standard Java debugging tools. We also consider metrics and monitoring for production insights.

* **Structured Event Logging:** FlowJava will provide a built-in logging facility (or integrate with existing logging frameworks) to record important events with rich context. Each log entry (often called a **Trace Event**) should include fields such as timestamp (or simulated time), actor or component identifier, severity level, and message. For example, when the event loop detects a slow task (actor running too long without yield), it should log an event like `SlowTask` with details of which actor and how long. Another example: when an unexpected error is caught, log an event with the exception details. We should favor a structured format (e.g., key-value pairs) so that logs can be machine-parsed if needed. FlowJava can offer a `FlowLogger.trace(String eventName, Map<String,Object> details)` API or similar for actors to emit their own events. In line with minimal dependencies, we might implement a simple logger or build atop `java.util.logging` or SLF4J. The log output could be configured to go to console or file. Importantly, logs from simulation runs should include the random seed and possibly a unique run ID for cross-reference.

* **Actor Identifiers and Context:** Each actor (and perhaps each simulation context node) should have an **identifier** that appears in logs. This could be an auto-generated ID or a name given by the developer. For better debugging, we can allow naming certain important actors. The framework will include the actor ID in any log messages generated within that actor’s context. E.g., a log might show “\[Actor 42: BackupWorker] SlowTask took 250ms”. This helps filter logs by actor or correlate events related to a specific actor.

* **Actor Call Stack Tracing:** One difficulty in asynchronous code is reconstructing the logical call sequence. If actor A calls actor B (awaits B’s result), and B calls C, by the time an error occurs in C, the actual thread stack is only C (because A and B are suspended). FlowJava should implement an **actor stack trace** mechanism akin to the original’s *AcAC (Actor Callstack)* tool. We can achieve this by recording, whenever an actor awaits another, a link from the child future back to the parent actor. For example, if A does `Flow.await(BFuture)`, we note that “A is waiting on B”. We maintain a thread-local or structure that tracks the chain of awaits. If an error occurs or on demand, we can produce a trace like: A -> B -> C (like “A waited on B, B waited on C”). We might store a lightweight representation of this call chain (maybe just actor IDs) in each future or in a central registry when awaits happen. Then we provide a method `Flow.currentActorStack()` that returns the chain for debugging, and we include this in exception logs. For instance, if an unhandled exception bubbles out of C, the log can include something like `ActorStack: A <- B <- C`. This is immensely useful for diagnosing issues because it shows the high-level flow that led to the error. Implementing this in Java is easier than C++ because we can use try/finally or intercept Flow\.await calls to push/pop context. We should be careful to avoid large overhead in production – maybe enable detailed tracing only when debug logging is on. But the requirement is to have **some facility to trace the chain of actor calls** across futures. Even a simple logging of parent-child relationships when an actor starts another would help.

* **Deterministic Replay:** One of the strongest debugging aids we have is the deterministic simulation. FlowJava must make it easy to reproduce a scenario exactly. To this end, whenever we run a simulation test, we should log the **random seed** used. Ideally, any nondeterministic decision (like which fault injected) is tied to that seed. If a bug is found, the developer can rerun with the same seed to get the same behavior. We might integrate with the testing framework such that if an assertion fails, it prints the seed. FlowJava might even allow recording a **trace log** of all events (though that could be huge) to step through. At minimum, the combination of structured logs and known seed should allow offline analysis. We can enhance this by allowing the simulation to run in a step-by-step mode: e.g., an interactive shell or debug mode where each event loop iteration is printed or can be stepped through (this is more of a tool on top, not required for initial version). The requirement: **Given a simulation failure, it must be straightforward to reproduce it and examine the sequence of events leading to it**. Logging every event (like every message send, every actor start/stop) in simulation mode could be toggled on to facilitate this (though it’s verbose). Possibly provide a verbosity level.

* **Fault Injection Logging:** When a fault injection triggers in simulation, log it clearly. For example, “FaultInjection: DiskReadFailure at time X on Node3” or “FaultInjection: Induced delay of 2s on message Y”. This way, when analyzing logs, one knows which anomalies were intentional by the test. This ties in with determinism: those should correspond to PRNG decisions that can be repeated.

* **Metrics and Monitoring:** FlowJava can treat certain log events as **metrics**. For instance, we can have actors periodically log their queue lengths, or log a summary every minute of how many actors are active, how many messages sent, etc.. We could integrate with JDK JMX or another monitoring system to expose internal stats (like current number of ready tasks, longest event loop iteration time, etc.). At the requirements level, it’s enough to say *the framework should provide hooks or built-in counters for key performance metrics.* These might include:

    * Number of actors created, running, and cancelled.
    * Average and max latency of the event loop iterations.
    * I/O operations counts and durations.
    * Message counts, bytes sent/received (in real or simulated network).
    * etc.

  We can implement this with atomic counters incremented at relevant points, which can be queried or dumped to logs. The purpose is to help in tuning and ensuring the system’s health (especially in production use of FlowJava, one could track these metrics).

* **Integration with Java Debuggers:** Since FlowJava actors are real Java threads (virtual threads), standard debugging tools (like IDE debuggers or thread dump utilities) can be used. A developer could, for example, take a thread dump and see all the virtual threads (which might have names corresponding to actor IDs) and their stack traces. This is a benefit over frameworks that use custom bytecode transformations (the original Flow required a special “IDE mode” to ease debugging). In our documentation, we should note that one can debug FlowJava code by setting breakpoints in actor functions as usual, or by pausing the program to inspect what each actor thread is doing. One caveat: because of our single-thread scheduling, if you pause one actor thread in a debugger, you effectively pause the whole system since the others share the carrier thread (if using one carrier). In simulation, that’s fine. In a live system, pausing the event loop will freeze things. So, debugging in production mode might be tricky if everything is tied to one carrier thread; we might allow a special debug mode with multiple carriers just for IDE use, but that's speculative. The main point: **FlowJava uses standard Java concurrency mechanisms, so existing tooling (profilers, debuggers, logging frameworks) are compatible**. This is an improvement over a custom language or preprocessor approach and should be highlighted to users for confidence.

* **Logging of Lifecycle Events:** FlowJava should log significant lifecycle events if debugging is enabled: actor start, actor completion, actor cancellation, etc.. For example, when an actor is created, log `ActorCreated id=42 parent=10 type=MyActor`. When it finishes, log `ActorDone id=42 success/failed`. This can be very useful to see the flow of tasks, and to catch if any actor is hanging (if we see created but not done). We can keep these at a debug level to avoid performance impact when not needed.

* **Configuration of Logging/Debugging:** Provide a way to turn on/off these debugging features. Perhaps an environment setting or a config object passed to FlowJava initialization. For production, you might run with minimal logging (just errors). For simulation testing, you might enable verbose logs and actor stack tracking. We might also allow dynamic enabling (via a command or JMX) if feasible.

By incorporating these debugging and logging facilities, FlowJava will make it far easier to troubleshoot asynchronous code. Developers will be able to follow what happened in the system using logs and even reconstruct causal chains of events. The **deterministic replay** aspect is especially powerful: a bug that might be heisenbugs in other systems becomes reproducible, allowing thorough analysis and fixes. Combined with structured concurrency and exceptions, FlowJava aims to significantly reduce the mental burden of asynchronous programming.

## API Design Examples

Finally, to illustrate how a Java developer would actually use FlowJava, this section provides a few short examples of the API in action. These examples demonstrate defining actors, waiting on futures, using choose/selection, and managing errors and cancellation. (Note: These are illustrative; the actual class and method names in the final API might differ, but the concepts remain.)

### Example 1: Simple Actor Function with Await

```java
// Define an actor that fetches a value from a database and processes it
public FlowFuture<Integer> fetchAndProcess(Key key) {
    return Flow.start(() -> {  // Flow.start begins an actor on a new virtual thread, returns FlowFuture<T>
        Database db = ...;  // assume we have a database handle
        Transaction tr = db.createTransaction();
        try {
            Optional<ByteString> val = Flow.await(tr.getAsync(key));
            // tr.getAsync returns FlowFuture<Optional<ByteString>>
            if (val.isEmpty()) {
                return 0;  // key not found, return a default value
            }
            int number = decodeInt(val.get());
            Flow.await(tr.commit());  // commit transaction (returns FlowFuture<Void>)
            return process(number);   // return processed result (will be wrapped in FlowFuture)
        } catch (DbException e) {
            tr.rollback();  // if we got a database-specific exception, rollback
            throw e;        // propagate error to caller (future completes exceptionally)
        }
    });
}

// Using the actor:
FlowFuture<Integer> resultFuture = fetchAndProcess(someKey);
// ... possibly do other work ...
try {
    Integer result = resultFuture.get();  // or Flow.await(resultFuture) if inside another actor
    System.out.println("Result = " + result);
} catch (DbException e) {
    System.err.println("Operation failed: " + e.getMessage());
}
```

**Explanation:** Here `fetchAndProcess` is an actor function that starts a new actor via `Flow.start()`. Inside the lambda, we call `Flow.await` on other futures (`tr.getAsync` and `tr.commit`). This causes the actor to suspend until those operations complete. If `getAsync` or `commit` fails, they will throw `DbException` which we catch to rollback. The actor returns an `Integer` result, which is conveyed by returning that value (FlowJava will complete the future with that value). The caller gets a `FlowFuture<Integer>` immediately and can later get the result either by awaiting in another actor or by calling `get()` (which will block the thread, so generally outside actors you might want to avoid blocking too many threads, but with Loom it's fine on virtual threads). The code reads in a straightforward sequential way, but under the hood it is non-blocking and concurrent.

### Example 2: Waiting on Multiple Futures (Choose)

```java
// Define an actor that waits for the first response among two services:
public FlowFuture<String> getFirstAvailableData(String query) {
    return Flow.start(() -> {
        FlowFuture<String> resp1 = serviceA.queryAsync(query);
        FlowFuture<String> resp2 = serviceB.queryAsync(query);
        // Wait for whichever response comes first:
        int winner = Flow.select(resp1, resp2);  // suppose select returns 0 if resp1 finished first, 1 if resp2 first
        if (winner == 0) {
            // Ensure the slower one is cancelled to save work (if not already done)
            resp2.cancel();
            return Flow.await(resp1);
        } else {
            resp1.cancel();
            return Flow.await(resp2);
        }
    });
}
```

**Explanation:** The `getFirstAvailableData` actor sends the same query to two services in parallel and uses `Flow.select` to wait for the first to respond. We assume `Flow.select` returns an index indicating which future completed first. The actor then cancels the other future (since its result is no longer needed) – FlowJava's cancellation will propagate to stop whatever work that other future was doing. Finally, it awaits the winner future to get the actual data and returns it. The result is that this actor itself finishes with the data from the fastest service. This demonstrates racing two operations and cancellation of the loser, a common pattern in resilient systems.

### Example 3: Stream Processing Actor

```java
// Suppose we have a FlowStream<Request> incomingRequests provided to this actor
public FlowFuture<Void> handleRequests(FlowStream<Request> requestStream) {
    return Flow.start(() -> {
        while (true) {
            Request req;
            try {
                req = Flow.await(requestStream.next());  // wait for next incoming request
            } catch (FlowEndOfStreamException e) {
                // Stream closed, no more requests
                break;
            }
            // process the request (could spawn other actors or do I/O)
            FlowFuture<Response> respFuture = processRequest(req);
            // Optionally, wait for processing to complete if we need to enforce order or send reply here
            Flow.await(respFuture);
            // loop back to get next request
        }
        return null;  // indicate completion
    });
}
```

**Explanation:** This actor `handleRequests` continuously pulls from a `FlowStream<Request>`. `requestStream.next()` returns a `FlowFuture<Request>` for the next item, or throws an `FlowEndOfStreamException` (conceptually) if the stream is closed (i.e., no more requests will come). The actor uses an infinite loop to handle one request at a time. For each request, it calls `processRequest(req)` which could itself be an async operation or actor, returning a future `respFuture`. We show an await on `respFuture` just to illustrate waiting for some asynchronous processing (maybe writing to a database) before going back to receive the next request. In a real scenario, the actor might send a reply or do other actions with the response. This example highlights the use of `FlowStream` and how an actor can consume it with a simple loop and `Flow.await()` calls. The actor will suspend when there are no requests and automatically resume when a new request is published into the stream by some other component.

### Example 4: Simulation Mode Usage (Pseudo-code)

```java
// Configure simulation mode
FlowJava.setSimulationMode(true, seed=12345);
NetworkInterface net = FlowSimulation.getNetwork();
// Set up two simulated nodes
SimulationContext node1 = FlowSimulation.createContext("Node1");
SimulationContext node2 = FlowSimulation.createContext("Node2");
// Within each context, start some actors representing servers
FlowSimulation.runInContext(node1, () -> {
    startServerActors(...);  // user-defined function to start actors for Node1's server
});
FlowSimulation.runInContext(node2, () -> {
    startServerActors(...);  // for Node2
});
// Also start some test workload actors
FlowSimulation.runInContext(node1, () -> {
    startTestClientActors(node2Address, ...);
});
// Run the simulation for a certain virtual time or number of events
FlowSimulation.runUntil(time=60.0);  // run simulation for 60s of virtual time
FlowJava.setSimulationMode(false, 0);  // exit simulation mode (if continuing normal execution)
```

**Explanation:** This pseudo-code outlines how one might use FlowJava’s simulation capabilities. We set the simulation mode with a seed, create contexts for two nodes, and start actors in each context. We also start some test actors (e.g., clients on Node1 sending requests to Node2’s server). The `FlowSimulation.runUntil` might drive the simulated event loop until 60 seconds of virtual time have elapsed (or one could run until no more events). During this simulation, all interactions between Node1 and Node2 happen via the simulated network (`FlowSimulation.getNetwork()` provides an interface to simulate message passing, etc.). After the simulation, we turn off simulation mode perhaps to allow the program to exit or do other real-world tasks. In practice, one might not toggle back to real mode in the same run, but this shows that the mode is a runtime setting. The key point is that user code for `startServerActors` and `startTestClientActors` is written against FlowJava’s normal APIs (like sending messages via some `FlowEndpoint.send()` which under simulation uses the fake network). The deterministic nature means if we run this with seed 12345, we can run again with 12345 and get identical behavior. This example is highly abstract; the actual simulation control API might differ, but it demonstrates the concepts of contexts and controlling execution.

## Conclusion

FlowJava is a comprehensive reimplementation of an actor-framework geared toward high concurrency and rigorous correctness, delivered in pure Java. By marrying the **actor model** (with futures, promises, and streams) to Java’s **Project Loom** capabilities, it allows developers to write asynchronous code that looks and feels synchronous, without needing any custom language extensions. The single-threaded, prioritized **scheduler** ensures consistent ordering and eliminates data races in the core logic, while the **deterministic simulation mode** provides an unparalleled testing ground for distributed algorithms, complete with controllable fault injection and reproducibility.

This software requirements document has detailed the key features FlowJava must provide: from the core API primitives like `FlowFuture`, `FlowPromise`, and `FlowStream`, to the inner workings of the event loop and integration with I/O, to advanced aspects like error propagation, automatic cancellation, and debugging tools. Each requirement is grounded in making the system both **powerful** (able to handle real-world demands of I/O and parallelism) and **predictable** (so that developers can trust the system’s behavior and easily debug it).

In implementing FlowJava, we will adhere to using Java 25+ standard features, minimizing external dependencies. Virtual threads will handle concurrency, structured concurrency will manage lifecycles, and the entire framework will remain friendly to standard Java tooling and practices. Logging and monitoring will be built-in to ensure that even a complex web of actors can be understood and monitored in production.

Ultimately, FlowJava aims to bring to the Java ecosystem the proven benefits of the Flow-like approach – highly concurrent performance, simpler async code, and rock-solid reliability through simulation testing – all while staying idiomatic to Java and leveraging its latest advancements. By following the requirements and design outlined here, we will create a tool that can serve as the foundation for building robust distributed systems and services in Java, with confidence in their behavior and correctness.
