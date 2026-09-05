package com.anil.logging.logging;

public interface TimerContext extends AutoCloseable {
    @Override
    void close();
}
