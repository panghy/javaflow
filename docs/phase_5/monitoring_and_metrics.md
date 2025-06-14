# Phase 5: Simulation Monitoring and Metrics

## Overview

This document describes the monitoring and metrics collection capabilities for JavaFlow's simulation mode. These features help developers understand simulation behavior, debug issues, and ensure test quality.

## Core Metrics

### SimulationMetrics Class

```java
public class SimulationMetrics {
    // Execution metrics
    private final AtomicLong tasksExecuted = new AtomicLong();
    private final AtomicLong randomTasksSelected = new AtomicLong();
    private final AtomicLong priorityInversions = new AtomicLong();
    
    // Fault injection metrics
    private final Map<String, AtomicLong> bugTriggerCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalFaultsInjected = new AtomicLong();
    
    // Network metrics
    private final AtomicLong packetsDropped = new AtomicLong();
    private final AtomicLong packetsReordered = new AtomicLong();
    private final AtomicLong connectionsFaile = new AtomicLong();
    private final AtomicLong networkPartitions = new AtomicLong();
    
    // Storage metrics
    private final AtomicLong diskFailures = new AtomicLong();
    private final AtomicLong dataCorruptions = new AtomicLong();
    private final AtomicLong slowDiskOperations = new AtomicLong();
    
    // Timing metrics
    private final long simulationStartTime = System.currentTimeMillis();
    private double simulatedTime = 0.0;
    private final AtomicLong clockJumps = new AtomicLong();
    
    // Resource metrics
    private final AtomicLong memoryPressureEvents = new AtomicLong();
    private final AtomicLong resourceExhaustionEvents = new AtomicLong();
}
```

## Monitoring Features

### 1. Real-time Metrics Collection

```java
public class SimulationMonitor {
    private final SimulationMetrics metrics;
    private final List<MetricsListener> listeners = new ArrayList<>();
    
    public interface MetricsListener {
        void onTaskExecuted(Task task);
        void onFaultInjected(String bugId);
        void onNetworkEvent(NetworkEvent event);
        void onStorageEvent(StorageEvent event);
    }
    
    // Periodic metrics snapshot
    public void startPeriodicReporting(Duration interval) {
        scheduledExecutor.scheduleAtFixedRate(
            this::reportMetrics, 
            interval.toMillis(), 
            interval.toMillis(), 
            TimeUnit.MILLISECONDS
        );
    }
}
```

### 2. Determinism Verification

```java
public class DeterminismMonitor {
    private final Map<Long, SimulationTrace> traces = new HashMap<>();
    
    public class SimulationTrace {
        private final long seed;
        private final List<TraceEvent> events = new ArrayList<>();
        
        public void recordEvent(String type, Object data) {
            events.add(new TraceEvent(
                FlowSimulation.getCurrentTime(),
                type,
                data,
                Thread.currentThread().getStackTrace()
            ));
        }
    }
    
    public boolean verifyDeterminism(long seed1, long seed2) {
        SimulationTrace trace1 = traces.get(seed1);
        SimulationTrace trace2 = traces.get(seed2);
        
        return trace1.equals(trace2);
    }
}
```

### 3. Coverage Analysis

```java
public class SimulationCoverage {
    // Track which BUGGIFY points were hit
    private final Set<String> executedBuggifyPoints = new HashSet<>();
    private final Set<String> allBuggifyPoints = new HashSet<>();
    
    // Track code paths taken
    private final Map<String, Integer> pathExecutionCounts = new HashMap<>();
    
    // Track fault combinations tested
    private final Set<Set<String>> testedFaultCombinations = new HashSet<>();
    
    public double getBuggifyCoverage() {
        return (double) executedBuggifyPoints.size() / allBuggifyPoints.size();
    }
    
    public void generateCoverageReport() {
        System.out.println("=== Simulation Coverage Report ===");
        System.out.printf("BUGGIFY Coverage: %.1f%% (%d/%d points)%n", 
            getBuggifyCoverage() * 100,
            executedBuggifyPoints.size(),
            allBuggifyPoints.size());
        
        System.out.println("\nUntested BUGGIFY points:");
        Sets.difference(allBuggifyPoints, executedBuggifyPoints)
            .forEach(point -> System.out.println("  - " + point));
    }
}
```

## Reporting and Visualization

### 1. Console Reporter

```java
public class ConsoleMetricsReporter {
    public void report(SimulationMetrics metrics) {
        System.out.println("\n=== Simulation Metrics ===");
        System.out.printf("Seed: %d%n", FlowRandom.getCurrentSeed());
        System.out.printf("Real time: %.2fs, Simulated time: %.2fs%n",
            metrics.getRealDuration().toMillis() / 1000.0,
            metrics.getSimulatedTime());
        
        System.out.println("\nExecution:");
        System.out.printf("  Tasks executed: %,d%n", metrics.getTasksExecuted());
        System.out.printf("  Random selections: %,d (%.1f%%)%n",
            metrics.getRandomTasksSelected(),
            metrics.getRandomSelectionRate() * 100);
        
        System.out.println("\nFaults Injected:");
        metrics.getBugTriggerCounts().entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .forEach(e -> System.out.printf("  %s: %,d%n", e.getKey(), e.getValue()));
        
        System.out.println("\nNetwork:");
        System.out.printf("  Packets dropped: %,d%n", metrics.getPacketsDropped());
        System.out.printf("  Connections failed: %,d%n", metrics.getConnectionsFailed());
        
        System.out.println("\nStorage:");
        System.out.printf("  Disk failures: %,d%n", metrics.getDiskFailures());
        System.out.printf("  Data corruptions: %,d%n", metrics.getDataCorruptions());
    }
}
```

### 2. JSON Reporter

```java
public class JsonMetricsReporter {
    private final ObjectMapper mapper = new ObjectMapper();
    
    public void exportMetrics(SimulationMetrics metrics, Path outputPath) {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.seed = FlowRandom.getCurrentSeed();
        snapshot.timestamp = Instant.now();
        snapshot.simulatedTime = metrics.getSimulatedTime();
        snapshot.tasksExecuted = metrics.getTasksExecuted();
        snapshot.faults = metrics.getBugTriggerCounts();
        // ... populate other fields
        
        mapper.writeValue(outputPath.toFile(), snapshot);
    }
}
```

### 3. HTML Report Generator

```java
public class HtmlReportGenerator {
    public void generateReport(List<SimulationRun> runs, Path outputPath) {
        // Generate HTML with charts and tables
        String html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>JavaFlow Simulation Report</title>
                <script src="https://cdn.plot.ly/plotly-latest.min.js"></script>
            </head>
            <body>
                <h1>Simulation Test Results</h1>
                <div id="fault-distribution"></div>
                <div id="performance-metrics"></div>
                <div id="coverage-analysis"></div>
                <!-- Charts and tables generated here -->
            </body>
            </html>
            """;
        
        Files.write(outputPath, html.getBytes());
    }
}
```

## Integration with CI/CD

### 1. JUnit Integration

```java
public class SimulationTestListener extends RunListener {
    private final List<SimulationResult> results = new ArrayList<>();
    
    @Override
    public void testFinished(Description description) {
        if (FlowSimulation.isSimulated()) {
            SimulationResult result = new SimulationResult();
            result.testName = description.getMethodName();
            result.seed = FlowRandom.getCurrentSeed();
            result.metrics = FlowSimulation.getMetrics().snapshot();
            result.passed = true;
            results.add(result);
        }
    }
    
    @Override
    public void testFailure(Failure failure) {
        if (FlowSimulation.isSimulated()) {
            results.get(results.size() - 1).passed = false;
            results.get(results.size() - 1).failureMessage = failure.getMessage();
            
            // Log seed for reproduction
            System.err.printf("[FAILURE] Test %s failed with seed %d%n",
                failure.getDescription().getMethodName(),
                FlowRandom.getCurrentSeed());
        }
    }
}
```

### 2. Simulation Quality Gates

```java
public class SimulationQualityGates {
    public static class QualityReport {
        public boolean passed;
        public List<String> violations = new ArrayList<>();
    }
    
    public QualityReport evaluate(SimulationMetrics metrics) {
        QualityReport report = new QualityReport();
        report.passed = true;
        
        // Check minimum fault injection
        if (metrics.getTotalFaultsInjected() < 10) {
            report.violations.add("Insufficient fault injection (< 10 faults)");
            report.passed = false;
        }
        
        // Check task execution diversity
        double randomRate = metrics.getRandomSelectionRate();
        if (randomRate < 0.05) {
            report.violations.add("Low random task selection rate (< 5%)");
            report.passed = false;
        }
        
        // Check simulation duration
        if (metrics.getSimulatedTime() < 60.0) {
            report.violations.add("Simulation too short (< 60 seconds)");
            report.passed = false;
        }
        
        return report;
    }
}
```

## Usage Examples

### Basic Monitoring

```java
@Test
public void testWithMonitoring() {
    // Enable metrics collection
    SimulationMonitor monitor = new SimulationMonitor();
    monitor.startPeriodicReporting(Duration.ofSeconds(10));
    
    // Run simulation
    FlowFuture<Void> result = runSimulation();
    result.get();
    
    // Generate report
    ConsoleMetricsReporter reporter = new ConsoleMetricsReporter();
    reporter.report(FlowSimulation.getMetrics());
}
```

### CI Pipeline Integration

```yaml
# .github/workflows/simulation-tests.yml
- name: Run Simulation Tests
  run: ./gradlew test -Psimulation=true
  
- name: Generate Simulation Report
  run: ./gradlew generateSimulationReport
  
- name: Upload Simulation Artifacts
  uses: actions/upload-artifact@v2
  with:
    name: simulation-reports
    path: build/reports/simulation/
    
- name: Check Quality Gates
  run: ./gradlew checkSimulationQuality
```

### Comparative Analysis

```java
public class SimulationComparison {
    public void compareRuns(List<Long> seeds) {
        Map<Long, SimulationMetrics> results = new HashMap<>();
        
        for (long seed : seeds) {
            FlowSimulation.start(SimulationConfig.builder()
                .withSeed(seed)
                .build());
            
            runTestScenario();
            results.put(seed, FlowSimulation.getMetrics().snapshot());
            
            FlowSimulation.stop();
        }
        
        // Analyze variance
        analyzeVariance(results);
        
        // Find outliers
        findOutliers(results);
        
        // Generate comparison report
        generateComparisonReport(results);
    }
}
```

## Best Practices

1. **Always Log Seeds**: Every test run should log its seed prominently
2. **Monitor Key Metrics**: Track task execution, fault injection, and timing
3. **Set Quality Gates**: Define minimum thresholds for simulation quality
4. **Archive Results**: Store simulation results for historical analysis
5. **Regular Reviews**: Periodically review untested BUGGIFY points
6. **Performance Tracking**: Monitor simulation performance over time
7. **Failure Analysis**: Maintain a database of failure seeds and their fixes