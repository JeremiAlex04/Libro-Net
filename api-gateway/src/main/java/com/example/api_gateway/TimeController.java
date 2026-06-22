package com.example.api_gateway;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.HashMap;
import java.util.Map;

@RestController
public class TimeController {

    @GetMapping("/api/time")
    public Mono<Map<String, Object>> getReferenceTime() {
        Map<String, Object> response = new HashMap<>();
        response.put("serverTimeMs", System.currentTimeMillis());
        return Mono.just(response);
    }
}
