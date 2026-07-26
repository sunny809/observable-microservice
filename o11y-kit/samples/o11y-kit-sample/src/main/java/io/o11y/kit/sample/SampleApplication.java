package io.o11y.kit.sample;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    @RestController
    @RequestMapping("/api")
    public static class SampleController {

        private final MeterRegistry registry;

        public SampleController(MeterRegistry registry) {
            this.registry = registry;
        }

        @GetMapping("/hello")
        public String hello() {
            registry.counter("api.hello.calls").increment();
            return "Hello, o11y-kit!";
        }
    }
}