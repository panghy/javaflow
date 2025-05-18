package io.github.panghy.javaflow.util;

/**
 * Utility class for handling I/O operations.
 */
public class IOUtil {

  private IOUtil() {
    // Utility class should not be instantiated
  }

  /**
   * Closes the given AutoCloseable, ignoring any exceptions.
   *
   * @param closeable The AutoCloseable to close
   */
  public static void closeQuietly(AutoCloseable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception ignored) {
      }
    }
  }
}
