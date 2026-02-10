package edu.northeastern.cs6650.client.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the MetricsAnalyzer class.
 */
class MetricsAnalyzerTest {

  @Test
  void testMeanCalculation() throws Exception {
    // Use reflection to access private method
    Method meanMethod = MetricsAnalyzer.class.getDeclaredMethod("meanMs", List.class);
    meanMethod.setAccessible(true);

    List<Long> latencies = Arrays.asList(10L, 20L, 30L, 40L, 50L);
    double mean = (double) meanMethod.invoke(null, latencies);

    assertEquals(30.0, mean, 0.001);
  }

  @Test
  void testMeanWithEmptyList() throws Exception {
    Method meanMethod = MetricsAnalyzer.class.getDeclaredMethod("meanMs", List.class);
    meanMethod.setAccessible(true);

    List<Long> latencies = Arrays.asList();
    double mean = (double) meanMethod.invoke(null, latencies);

    assertEquals(0.0, mean, 0.001);
  }

  @Test
  void testMeanWithSingleValue() throws Exception {
    Method meanMethod = MetricsAnalyzer.class.getDeclaredMethod("meanMs", List.class);
    meanMethod.setAccessible(true);

    List<Long> latencies = Arrays.asList(42L);
    double mean = (double) meanMethod.invoke(null, latencies);

    assertEquals(42.0, mean, 0.001);
  }

  @Test
  void testPercentileMedian() throws Exception {
    Method percentileMethod = MetricsAnalyzer.class.getDeclaredMethod(
        "percentileMs", List.class, int.class);
    percentileMethod.setAccessible(true);

    List<Long> latencies = Arrays.asList(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);
    double median = (double) percentileMethod.invoke(null, latencies, 50);

    assertEquals(50.0, median, 1.0);
  }

  @Test
  void testPercentile95() throws Exception {
    Method percentileMethod = MetricsAnalyzer.class.getDeclaredMethod(
        "percentileMs", List.class, int.class);
    percentileMethod.setAccessible(true);

    List<Long> latencies = Arrays.asList(
        10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L
    );
    double p95 = (double) percentileMethod.invoke(null, latencies, 95);

    // 95th percentile of 10 values should be around 95
    assertTrue(p95 >= 90 && p95 <= 100);
  }

  @Test
  void testPercentile99() throws Exception {
    Method percentileMethod = MetricsAnalyzer.class.getDeclaredMethod(
        "percentileMs", List.class, int.class);
    percentileMethod.setAccessible(true);

    List<Long> latencies = Arrays.asList(
        10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L
    );
    double p99 = (double) percentileMethod.invoke(null, latencies, 99);

    // 99th percentile of 10 values should be close to 100
    assertTrue(p99 >= 95 && p99 <= 100);
  }

  @Test
  void testPercentileWithEmptyList() throws Exception {
    Method percentileMethod = MetricsAnalyzer.class.getDeclaredMethod(
        "percentileMs", List.class, int.class);
    percentileMethod.setAccessible(true);

    List<Long> latencies = Arrays.asList();
    double percentile = (double) percentileMethod.invoke(null, latencies, 95);

    assertEquals(0.0, percentile, 0.001);
  }

  @Test
  void testPercentileWithSingleValue() throws Exception {
    Method percentileMethod = MetricsAnalyzer.class.getDeclaredMethod(
        "percentileMs", List.class, int.class);
    percentileMethod.setAccessible(true);

    List<Long> latencies = Arrays.asList(42L);
    double percentile = (double) percentileMethod.invoke(null, latencies, 95);

    assertEquals(42.0, percentile, 0.001);
  }

  @Test
  void testAnalyzeAndPrintWithValidCSV(@TempDir Path tempDir) throws Exception {
    // Create a test CSV file
    Path csvPath = tempDir.resolve("test_metrics.csv");
    String csvContent = "timestamp,messageType,latencyMs,statusCode,roomId\n" +
        "1000,TEXT,50,OK,1\n" +
        "2000,JOIN,60,OK,2\n" +
        "3000,TEXT,40,OK,1\n" +
        "4000,LEAVE,55,OK,3\n" +
        "5000,TEXT,-1,FAILED,1\n";
    Files.writeString(csvPath, csvContent);

    Path bucketsPath = tempDir.resolve("buckets.csv");

    MetricsAnalyzer analyzer = new MetricsAnalyzer();

    // Should not throw exception
    assertDoesNotThrow(() -> analyzer.analyzeAndPrint(csvPath, bucketsPath));

    // Verify buckets file was created
    assertTrue(Files.exists(bucketsPath));
  }

  @Test
  void testAnalyzeAndPrintCreatesOutput(@TempDir Path tempDir) throws Exception {
    Path csvPath = tempDir.resolve("metrics.csv");
    String csvContent = "timestamp,messageType,latencyMs,statusCode,roomId\n" +
        "10000,TEXT,30,OK,1\n" +
        "20000,TEXT,40,OK,2\n" +
        "30000,TEXT,35,OK,1\n";
    Files.writeString(csvPath, csvContent);

    Path bucketsPath = tempDir.resolve("throughput.csv");

    MetricsAnalyzer analyzer = new MetricsAnalyzer();
    analyzer.analyzeAndPrint(csvPath, bucketsPath);

    // Verify output file exists and has content
    assertTrue(Files.exists(bucketsPath));
    List<String> lines = Files.readAllLines(bucketsPath);
    assertTrue(lines.size() > 1, "Buckets file should have header and data");
    assertTrue(lines.get(0).contains("bucketStartMillis"));
  }
}