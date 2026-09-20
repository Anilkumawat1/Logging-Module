package com.anil.logging.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Passes response bytes directly to the client while retaining only a bounded prefix for logging.
 */
final class BoundedContentCachingResponseWrapper extends HttpServletResponseWrapper {
    private final int limit;
    private final ByteArrayOutputStream captured;
    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private boolean truncated;

    BoundedContentCachingResponseWrapper(HttpServletResponse response, int limit) {
        super(response);
        this.limit = Math.max(limit, 0);
        this.captured = new ByteArrayOutputStream(Math.min(this.limit, 1_024));
    }

    byte[] getCapturedContent() {
        if (writer != null) {
            writer.flush();
        }
        return captured.toByteArray();
    }

    boolean isCaptureTruncated() {
        return truncated;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        if (outputStream == null) {
            ServletOutputStream delegate = getResponse().getOutputStream();
            outputStream = new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    delegate.setWriteListener(writeListener);
                }

                @Override
                public void write(int value) throws IOException {
                    capture(value);
                    delegate.write(value);
                }

                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    capture(bytes, offset, length);
                    delegate.write(bytes, offset, length);
                }

                @Override
                public void flush() throws IOException {
                    delegate.flush();
                }

                @Override
                public void close() throws IOException {
                    delegate.close();
                }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() has already been called");
        }
        if (writer == null) {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(encoding);
            writer = new PrintWriter(new OutputStreamWriter(getOutputStreamForWriter(), charset));
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        } else if (outputStream != null) {
            outputStream.flush();
        }
        super.flushBuffer();
    }

    private ServletOutputStream getOutputStreamForWriter() throws IOException {
        ServletOutputStream delegate = getResponse().getOutputStream();
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return delegate.isReady();
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                delegate.setWriteListener(writeListener);
            }

            @Override
            public void write(int value) throws IOException {
                capture(value);
                delegate.write(value);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                capture(bytes, offset, length);
                delegate.write(bytes, offset, length);
            }

            @Override
            public void flush() throws IOException {
                delegate.flush();
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }
        };
    }

    private void capture(int value) {
        if (captured.size() < limit) {
            captured.write(value);
        } else {
            truncated = true;
        }
    }

    private void capture(byte[] bytes, int offset, int length) {
        int remaining = limit - captured.size();
        int copied = Math.min(Math.max(remaining, 0), length);
        if (copied > 0) {
            captured.write(bytes, offset, copied);
        }
        if (copied < length) {
            truncated = true;
        }
    }
}
