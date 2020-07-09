package com.example.sleuth;

import java.time.Duration;
import java.util.stream.Collectors;

import brave.propagation.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.cloud.sleuth.instrument.reactor.SleuthOperators.doWithThreadLocal;

@SpringBootApplication
public class SleuthWebfluxBenchmarkSampleApplication {

	public static void main(String[] args) {
//		 System.setProperty("spring.sleuth.enabled", "false");
		// System.setProperty("spring.sleuth.reactor.instrumentation-type", "DECORATE_ON_EACH");
		// System.setProperty("spring.sleuth.reactor.instrumentation-type", "DECORATE_ON_LAST");
//		System.setProperty("spring.sleuth.reactor.instrumentation-type", "MANUAL");
		SpringApplication.run(SleuthWebfluxBenchmarkSampleApplication.class, args);
	}

}

@RestController
class Foo {

	private static final Logger log = LoggerFactory.getLogger(Foo.class);

	@GetMapping("/fooNoSleuth")
	public Mono<String> fooNoSleuth() {
		return Flux.range(1, 10)
				.map(String::valueOf)
				.collect(Collectors.toList())
				.doOnEach(signal -> log.info("Got a request"))
				.flatMap(s -> Mono.delay(Duration.ZERO, Schedulers.newParallel("foo")).map(aLong -> {
					log.info("Logging [{}] from flat map", s);
					return "";
				}))
				.doOnEach(signal -> {
					log.info("Doing assertions");
					Assert.isTrue(signal.getContext().isEmpty(), "Context must be empty");
				});
	}

	@GetMapping("/foo")
	public Mono<String> foo() {
		return Flux.range(1, 10)
				.map(String::valueOf)
				.collect(Collectors.toList())
				.doOnEach(signal -> log.info("Got a request"))
				.flatMap(s -> Mono.delay(Duration.ZERO, Schedulers.newParallel("foo")).map(aLong -> {
					log.info("Logging [{}] from flat map", s);
					return "";
				}))
				.doOnEach(signal -> {
					log.info("Doing assertions");
					TraceContext traceContext = signal.getContext().get(TraceContext.class);
					Assert.notNull(traceContext, "Context must be set by Sleuth instrumentation");
					Assert.state(traceContext.traceIdString().equals("4883117762eb9420"), "TraceId must be propagated");
				});
	}

	@GetMapping("/fooManual")
	public Mono<String> fooManual() {
		return Flux.range(1, 10)
				.map(String::valueOf)
				.collect(Collectors.toList())
				.doOnEach(doWithThreadLocal(() -> log.info("Got a request")))
				.flatMap(s -> Mono.subscriberContext().delayElement(Duration.ZERO, Schedulers.newParallel("foo")).map(ctx -> {
					doWithThreadLocal(ctx, () -> log.info("Logging [{}] from flat map", s));
					return "";
				}))
				.doOnEach(signal -> {
					doWithThreadLocal(signal.getContext(), () -> log.info("Doing assertions"));
					TraceContext traceContext = signal.getContext().get(TraceContext.class);
					Assert.notNull(traceContext, "Context must be set by Sleuth instrumentation");
					Assert.state(traceContext.traceIdString().equals("4883117762eb9420"), "TraceId must be propagated");
				});
	}
}