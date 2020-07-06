package com.example.sleuth;

import java.util.function.Consumer;

import brave.propagation.CurrentTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.instrument.reactor.SleuthOperators;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RestController;

import brave.propagation.TraceContext;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;
import reactor.util.context.Context;

import org.springframework.web.bind.annotation.GetMapping;

@SpringBootApplication
public class SleuthWebfluxBenchmarkSampleApplication {

	public static void main(String[] args) {
		// System.setProperty("spring.sleuth.enabled", "false");
		// System.setProperty("spring.sleuth.reactor.instrumentation-type", "DECORATE_ON_EACH");
		// System.setProperty("spring.sleuth.reactor.instrumentation-type", "DECORATE_ON_LAST");
//		System.setProperty("spring.sleuth.reactor.instrumentation-type", "MANUAL");
		SpringApplication.run(SleuthWebfluxBenchmarkSampleApplication.class, args);	
	}

}

@RestController
class Foo {

	private static final Logger log = LoggerFactory.getLogger(Foo.class);

	@GetMapping("/foo")
	public Mono<String> foo() {
		return Mono.just("fooTraced")
		.doOnEach(signal -> {
			log.info("Got a request");
		})
		.doOnEach(signal -> {
			TraceContext traceContext = signal.getContext().get(TraceContext.class);
			Assert.notNull(traceContext, "Context must be set by Sleuth instrumentation");
			Assert.state(traceContext.traceIdString().equals("4883117762eb9420"), "TraceId must be propagated");
		});
	}

	@GetMapping("/fooNoSleuth")
	public Mono<String> fooNoSleuth() {
		return Mono.just("fooTraced")
		.doOnEach(signal -> log.info("Got a request"));
	}


	@GetMapping("/fooManual")
	public Mono<String> fooManual() {
		return Mono.just("fooManual")
				.doOnEach(SleuthOperators.doWithThreadLocal(() -> log.info("Got a request")))
				.doOnEach(signal -> {
					TraceContext traceContext = signal.getContext().get(TraceContext.class);
					Assert.notNull(traceContext, "Context must be set by Sleuth instrumentation");
					Assert.state(traceContext.traceIdString().equals("4883117762eb9420"), "TraceId must be propagated");
				});
	}
}