package io.github.panghy.javaflow.io;

import io.github.panghy.javaflow.core.FlowFuture;
import io.github.panghy.javaflow.core.FlowPromise;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A real implementation of FlowFileSystem that interacts with the actual filesystem.
 * This implementation uses Java NIO for file operations.
 */
public class RealFlowFileSystem implements FlowFileSystem {

  @Override
  public FlowFuture<FlowFile> open(Path path, OpenOptions... options) {
    return RealFlowFile.open(path, options);
  }

  @Override
  public FlowFuture<Void> delete(Path path) {
    FlowFuture<Void> result = new FlowFuture<>();
    FlowPromise<Void> promise = result.getPromise();

    try {
      Files.delete(path);
      promise.complete(null);
    } catch (Exception e) {
      promise.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public FlowFuture<Boolean> exists(Path path) {
    FlowFuture<Boolean> result = new FlowFuture<>();
    FlowPromise<Boolean> promise = result.getPromise();

    try {
      boolean exists = Files.exists(path);
      promise.complete(exists);
    } catch (Exception e) {
      promise.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public FlowFuture<Void> createDirectory(Path path) {
    FlowFuture<Void> result = new FlowFuture<>();
    FlowPromise<Void> promise = result.getPromise();

    try {
      Files.createDirectory(path);
      promise.complete(null);
    } catch (Exception e) {
      promise.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public FlowFuture<Void> createDirectories(Path path) {
    FlowFuture<Void> result = new FlowFuture<>();
    FlowPromise<Void> promise = result.getPromise();

    try {
      Files.createDirectories(path);
      promise.complete(null);
    } catch (Exception e) {
      promise.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public FlowFuture<List<Path>> list(Path directory) {
    FlowFuture<List<Path>> result = new FlowFuture<>();
    FlowPromise<List<Path>> promise = result.getPromise();

    try {
      List<Path> paths = new ArrayList<>();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
        for (Path entry : stream) {
          paths.add(entry);
        }
      }
      promise.complete(paths);
    } catch (Exception e) {
      promise.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public FlowFuture<Void> move(Path source, Path target) {
    FlowFuture<Void> result = new FlowFuture<>();
    FlowPromise<Void> promise = result.getPromise();

    try {
      Files.move(source, target);
      promise.complete(null);
    } catch (Exception e) {
      promise.completeExceptionally(e);
    }

    return result;
  }
}