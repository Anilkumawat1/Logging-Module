package com.anil.logging.filter;

import jakarta.servlet.http.HttpServletRequest;

public interface ClientIpResolver {
    String resolve(HttpServletRequest request);
}
