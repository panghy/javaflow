package io.github.panghy.javaflow.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for logging in the JavaFlow framework.
 * Provides consistent logging methods that respect JUL (java.util.logging) levels.
 */
public final class LoggingUtil {

  private LoggingUtil() {
    // Utility class should not be instantiated
  }

  /**
   * Logs a debug message if the logger's level permits it.
   *
   * @param logger  The logger to use
   * @param message The message to log
   */
  public static void debug(Logger logger, String message) {
    if (logger.isLoggable(Level.FINE)) {
      logger.fine(message);
    }
  }

  /**
   * Logs an info message if the logger's level permits it.
   *
   * @param logger  The logger to use
   * @param message The message to log
   */
  public static void info(Logger logger, String message) {
    if (logger.isLoggable(Level.INFO)) {
      logger.info(message);
    }
  }

  /**
   * Logs a warning message if the logger's level permits it.
   *
   * @param logger  The logger to use
   * @param message The message to log
   */
  public static void warn(Logger logger, String message) {
    if (logger.isLoggable(Level.WARNING)) {
      logger.warning(message);
    }
  }

  /**
   * Logs an error message if the logger's level permits it.
   *
   * @param logger  The logger to use
   * @param message The message to log
   */
  public static void error(Logger logger, String message) {
    if (logger.isLoggable(Level.SEVERE)) {
      logger.severe(message);
    }
  }

  /**
   * Logs an exception at the error level with full stack trace.
   *
   * @param logger    The logger to use
   * @param message   The message to log
   * @param throwable The exception to log
   */
  public static void error(Logger logger, String message, Throwable throwable) {
    if (logger.isLoggable(Level.SEVERE)) {
      logger.log(Level.SEVERE, message, throwable);
    }
  }

  /**
   * Logs an exception at the warning level with full stack trace.
   *
   * @param logger    The logger to use
   * @param message   The message to log
   * @param throwable The exception to log
   */
  public static void warn(Logger logger, String message, Throwable throwable) {
    if (logger.isLoggable(Level.WARNING)) {
      logger.log(Level.WARNING, message, throwable);
    }
  }

  /**
   * Logs an exception at the info level with full stack trace.
   *
   * @param logger    The logger to use
   * @param message   The message to log
   * @param throwable The exception to log
   */
  public static void info(Logger logger, String message, Throwable throwable) {
    if (logger.isLoggable(Level.INFO)) {
      logger.log(Level.INFO, message, throwable);
    }
  }

  /**
   * Logs an exception at the debug level with full stack trace.
   *
   * @param logger    The logger to use
   * @param message   The message to log
   * @param throwable The exception to log
   */
  public static void debug(Logger logger, String message, Throwable throwable) {
    if (logger.isLoggable(Level.FINE)) {
      logger.log(Level.FINE, message, throwable);
    }
  }
}
