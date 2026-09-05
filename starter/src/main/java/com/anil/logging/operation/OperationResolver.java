package com.anil.logging.operation;

import jakarta.servlet.http.HttpServletRequest;

public interface OperationResolver {
    String resolve(HttpServletRequest request);
}
