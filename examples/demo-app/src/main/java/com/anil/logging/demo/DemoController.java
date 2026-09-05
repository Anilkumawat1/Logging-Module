package com.anil.logging.demo;

import com.anil.logging.operation.LogOperation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {
    private final OrderService orderService;

    public DemoController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/users")
    @LogOperation("LIST_USERS")
    List<Map<String, Object>> users() {
        return List.of(Map.of("id", "1001", "role", "ADMIN"));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @LogOperation("CREATE_ORDER")
    Map<String, Object> createOrder(@RequestBody Map<String, Object> order) {
        return orderService.create(order);
    }

    @GetMapping("/orders/{id}")
    @LogOperation("READ_ORDER")
    Map<String, Object> order(@PathVariable String id) {
        return Map.of("id", id, "status", "PAID");
    }

    @PostMapping("/error")
    @LogOperation("DEMO_ERROR")
    ResponseEntity<Map<String, Object>> error() {
        throw new IllegalStateException("demo failure with token=SECRET");
    }

    @PostMapping("/async")
    @LogOperation("ASYNC_ORDER")
    Map<String, Object> async() {
        orderService.completableFutureWork();
        orderService.asyncWork();
        return Map.of("status", "STARTED");
    }
}
