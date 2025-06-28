package io.github.panghy.javaflow.io;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A real implementation of FlowFileSystem that interacts with the actual filesystem.
 * This implementation uses Java NIO for file operations.
 */
public class RealFlowFileSystem implements FlowFileSystem {

  @Override
  public CompletableFuture<FlowFile> open(Path path, OpenOptions... options) {
    return RealFlowFile.open(path, options);
  }

  @Override
  public CompletableFuture<Void> delete(Path path) {
    CompletableFuture<Void> result = new CompletableFuture<>();

    try {
      Files.delete(path);
      result.complete(null);
    } catch (Exception e) {
      result.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public CompletableFuture<Boolean> exists(Path path) {
    CompletableFuture<Boolean> result = new CompletableFuture<>();

    try {
      boolean exists = Files.exists(path);
      result.complete(exists);
    } catch (Exception e) {
      result.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public CompletableFuture<Void> createDirectory(Path path) {
    CompletableFuture<Void> result = new CompletableFuture<>();

    try {
      Files.createDirectory(path);
      result.complete(null);
    } catch (Exception e) {
      result.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public CompletableFuture<Void> createDirectories(Path path) {
    CompletableFuture<Void> result = new CompletableFuture<>();

    try {
      Files.createDirectories(path);
      result.complete(null);
    } catch (Exception e) {
      result.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public CompletableFuture<List<Path>> list(Path directory) {
    CompletableFuture<List<Path>> result = new CompletableFuture<>();

    try {
      List<Path> paths = new ArrayList<>();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
        for (Path entry : stream) {
          paths.add(entry);
        }
      }
      result.complete(paths);
    } catch (Exception e) {
      result.completeExceptionally(e);
    }

    return result;
  }

  @Override
  public CompletableFuture<Void> move(Path source, Path target) {
    CompletableFuture<Void> result = new CompletableFuture<>();

    try {
      Files.move(source, target);
      result.complete(null);
    } catch (Exception e) {
      result.completeExceptionally(e);
    }

    return result;
  }
}