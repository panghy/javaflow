package io.github.panghy.javaflow.io;

import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

/**
 * Options for opening a file in the JavaFlow file system.
 * These are used when opening a file with {@link FlowFileSystem#open}.
 */
public enum OpenOptions {
  /**
   * Open for read access.
   */
  READ,

  /**
   * Open for write access.
   */
  WRITE,

  /**
   * Create file if it doesn't exist.
   */
  CREATE,

  /**
   * Create a new file, failing if the file already exists.
   */
  CREATE_NEW,

  /**
   * Truncate an existing file to 0 bytes when opening it.
   */
  TRUNCATE_EXISTING,

  /**
   * Open file and append to the end of it.
   */
  APPEND,

  /**
   * Delete file when closed.
   */
  DELETE_ON_CLOSE,

  /**
   * Synchronized I/O content (fsync).
   */
  SYNC;

  /**
   * Converts FlowFile open options to NIO StandardOpenOption.
   *
   * @param options The options to convert
   * @return A set of StandardOpenOption
   */
  public static EnumSet<StandardOpenOption> toStandardOptions(OpenOptions... options) {
    EnumSet<StandardOpenOption> result = EnumSet.noneOf(StandardOpenOption.class);
    
    for (OpenOptions option : options) {
      switch (option) {
        case READ:
          result.add(StandardOpenOption.READ);
          break;
        case WRITE:
          result.add(StandardOpenOption.WRITE);
          break;
        case CREATE:
          result.add(StandardOpenOption.CREATE);
          break;
        case CREATE_NEW:
          result.add(StandardOpenOption.CREATE_NEW);
          break;
        case TRUNCATE_EXISTING:
          result.add(StandardOpenOption.TRUNCATE_EXISTING);
          break;
        case APPEND:
          result.add(StandardOpenOption.APPEND);
          break;
        case DELETE_ON_CLOSE:
          result.add(StandardOpenOption.DELETE_ON_CLOSE);
          break;
        case SYNC:
          result.add(StandardOpenOption.SYNC);
          break;
      }
    }
    
    return result;
  }
}