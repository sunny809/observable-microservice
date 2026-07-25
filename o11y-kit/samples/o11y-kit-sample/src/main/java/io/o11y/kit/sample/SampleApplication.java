package io.o11y.kit.sample;

import io.o11y.kit.metrics.BusinessMetricsPort;
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

        private final BusinessMetricsPort metrics;

        public SampleController(BusinessMetricsPort metrics) {
            this.metrics = metrics;
        }

        @GetMapping("/hello")
        public String hello() {
            metrics.increment("api.hello.calls");
            return "Hello, o11y-kit!";
        }
    }
}