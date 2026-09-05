package com.anil.logging.operation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class OperationLoggingInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            LogOperation method = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), LogOperation.class);
            LogOperation type = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), LogOperation.class);
            LogOperation selected = method != null ? method : type;
            if (selected != null && !selected.value().isBlank()) {
                request.setAttribute(DefaultOperationResolver.ATTRIBUTE, selected.value());
            }
        }
        return true;
    }
}
