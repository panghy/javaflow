package io.github.panghy.javaflow.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for the IOUtil class.
 */
@ExtendWith(MockitoExtension.class)
class IOUtilTest {

  @Mock
  private AutoCloseable mockCloseable;

  @Test
  void testCloseQuietlyWhenObjectIsNotNull() throws Exception {
    // Act
    IOUtil.closeQuietly(mockCloseable);

    // Assert
    verify(mockCloseable, times(1)).close();
    verifyNoMoreInteractions(mockCloseable);
  }

  @Test
  void testCloseQuietlyWhenObjectIsNull() {
    // Act - this should not throw any exception
    IOUtil.closeQuietly(null);
    
    // No assertion needed - test passes if no exception is thrown
  }

  @Test
  void testCloseQuietlySwallowsExceptions() throws Exception {
    // Arrange
    doThrow(new RuntimeException("Test exception")).when(mockCloseable).close();

    // Act - this should not propagate the exception
    IOUtil.closeQuietly(mockCloseable);

    // Assert
    verify(mockCloseable, times(1)).close();
    verifyNoMoreInteractions(mockCloseable);
  }
}