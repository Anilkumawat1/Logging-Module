package com.anil.logging.filter;

import com.anil.logging.config.LoggingProperties;
import jakarta.servlet.http.HttpServletRequest;

public class DefaultClientIpResolver implements ClientIpResolver {
    private final LoggingProperties properties;

    public DefaultClientIpResolver(LoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        for (String header : properties.getIp().getHeaders()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return firstForwardedValue(value);
            }
        }
        return request.getRemoteAddr();
    }

    private static String firstForwardedValue(String value) {
        int comma = value.indexOf(',');
        return comma >= 0 ? value.substring(0, comma).trim() : value.trim();
    }
}
