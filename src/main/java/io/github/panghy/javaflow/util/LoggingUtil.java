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
   * Gets the caller information from the stack trace.
   * Skips LoggingUtil frames to find the actual caller.
   */
  private static StackTraceElement getCaller() {
    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    // Skip: 0=getStackTrace, 1=getCaller, 2=debug/info/warn/error method
    for (int i = 3; i < stack.length; i++) {
      StackTraceElement element = stack[i];
      if (!element.getClassName().equals(LoggingUtil.class.getName())) {
        return element;
      }
    }
    // Fallback if we can't find the caller
    return stack.length > 3 ? stack[3] : stack[stack.length - 1];
  }

  /**
   * Logs a debug message if the logger's level permits it.
   *
   * @param logger  The logger to use
   * @param message The message to log
   */
  public static void debug(Logger logger, String message) {
    if (logger.isLoggable(Level.FINE)) {
      StackTraceElement caller = getCaller();
      logger.logp(Level.FINE, caller.getClassName(), caller.getMethodName(), message);
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
      StackTraceElement caller = getCaller();
      logger.logp(Level.INFO, caller.getClassName(), caller.getMethodName(), message);
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
      StackTraceElement caller = getCaller();
      logger.logp(Level.WARNING, caller.getClassName(), caller.getMethodName(), message);
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
      StackTraceElement caller = getCaller();
      logger.logp(Level.SEVERE, caller.getClassName(), caller.getMethodName(), message);
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
      StackTraceElement caller = getCaller();
      logger.logp(Level.SEVERE, caller.getClassName(), caller.getMethodName(), message, throwable);
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
      StackTraceElement caller = getCaller();
      logger.logp(Level.WARNING, caller.getClassName(), caller.getMethodName(), message, throwable);
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
      StackTraceElement caller = getCaller();
      logger.logp(Level.INFO, caller.getClassName(), caller.getMethodName(), message, throwable);
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
      StackTraceElement caller = getCaller();
      logger.logp(Level.FINE, caller.getClassName(), caller.getMethodName(), message, throwable);
    }
  }
}