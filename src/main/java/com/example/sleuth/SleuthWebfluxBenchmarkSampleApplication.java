package com.example.sleuth;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import brave.Tracing;
import brave.propagation.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.instrument.messaging.MessagingSleuthOperator;
import org.springframework.cloud.sleuth.instrument.web.WebFluxSleuthOperators;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootApplication
@Import(TestChannelBinderConfiguration.class)
public class SleuthWebfluxBenchmarkSampleApplication {

	public static void main(String[] args) throws InterruptedException, IOException {
//		 System.setProperty("spring.sleuth.enabled", "false");
		// System.setProperty("spring.sleuth.reactor.instrumentation-type", "DECORATE_ON_EACH");
		// System.setProperty("spring.sleuth.reactor.instrumentation-type", "DECORATE_ON_LAST");
//		System.setProperty("spring.sleuth.reactor.instrumentation-type", "MANUAL");
		System.setProperty("spring.sleuth.reactor.instrumentation-type", "MANUAL");
		System.setProperty("spring.sleuth.function.type", "simple");
		ConfigurableApplicationContext context = SpringApplication.run(SleuthWebfluxBenchmarkSampleApplication.class, args);
		System.out.println("PRess any key to continue");
		System.in.read();
		for (int i = 0; i < 1; i++) {
			InputDestination input = context.getBean(InputDestination.class);
			input.send(MessageBuilder.withPayload("hello".getBytes())
					.setHeader("b3", "4883117762eb9420-4883117762eb9420-1").build());
			System.out.println("Retrieving the message for tests");
			OutputDestination output = context.getBean(OutputDestination.class);
			Message<byte[]> message = output.receive(200L);
			System.out.println("Got the message from output");
			assertThat(message).isNotNull();
			System.out.println("Message is not null");
			assertThat(message.getPayload()).isEqualTo("HELLO".getBytes());
			System.out.println("Payload is HELLO");
			String b3 = message.getHeaders().get("b3", String.class);
			System.out.println("Checking the b3 header [" + b3 + "]");
			assertThat(b3).startsWith("4883117762eb9420");
		}
	}

	@Bean
	ExecutorService sleuthExecutorService() {
		return Executors.newCachedThreadPool();
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "simple")
	public Function<String, String> nonReactiveSimpleSleuthFunction() {
		System.out.println("simple_function");
		return new SimpleFunction();
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "reactive_simple")
	public Function<Flux<String>, Flux<String>> reactiveSimpleSleuthFunction() {
		System.out.println("simple_reactive_function");
		return new SimpleReactiveFunction();
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "simple_manual")
	public Function<Message<String>, Message<String>> nonReactiveSimpleManualSleuthFunction(Tracing tracing) {
		System.out.println("simple_manual_function");
		return new SimpleManualFunction(tracing);
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "reactive_simple_manual")
	public Function<Flux<Message<String>>, Flux<Message<String>>> reactiveSimpleManualSleuthFunction(Tracing tracing) {
		System.out.println("simple_reactive_manual_function");
		return new SimpleReactiveManualFunction(tracing);
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.nonreactive.function.enabled", havingValue = "true")
	public Function<String, String> nonReactiveSleuthFunction(ExecutorService executorService) {
		System.out.println("no sleuth non reactive function");
		return new SleuthNonReactiveFunction(executorService);
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "DECORATE_ON_EACH", matchIfMissing = true)
	public Function<Flux<String>, Flux<String>> onEachFunction() {
		System.out.println("on each function");
		return new SleuthFunction();
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "DECORATE_ON_LAST")
	public Function<Flux<String>, Flux<String>> onLastFunction() {
		System.out.println("on last function");
		return new SleuthFunction();
	}

	@Bean(name = "myFlux")
	@ConditionalOnProperty(value = "spring.sleuth.function.type", havingValue = "MANUAL")
	public Function<Flux<String>, Flux<String>> manualFunction() {
		System.out.println("manual function");
		return new SleuthManualFunction();
	}
}

class SimpleFunction implements Function<String, String> {

	private static final Logger log = LoggerFactory.getLogger(SimpleFunction.class);

	@Override
	public String apply(String input) {
		log.info("Hello from simple [{}]", input);
		return input.toUpperCase();
	}
}

class SimpleReactiveFunction implements Function<Flux<String>, Flux<String>> {

	private static final Logger log = LoggerFactory.getLogger(SimpleReactiveFunction.class);

	@Override
	public Flux<String> apply(Flux<String> input) {
		return input
				.doOnNext(s -> log.info("Hello from simple [{}]", s))
				.map(String::toUpperCase);
	}
}

class SimpleManualFunction implements Function<Message<String>, Message<String>> {

	private static final Logger log = LoggerFactory.getLogger(SimpleFunction.class);

	private final Tracing tracing;

	SimpleManualFunction(Tracing tracing) {
		this.tracing = tracing;
	}

	@Override
	public Message<String> apply(Message<String> input) {
		return ((Function<Message<String>, Message<String>>) stringMessage -> MessagingSleuthOperator.forInputMessage(this.tracing, stringMessage))
				.andThen(msg -> MessagingSleuthOperator.withSpanInScope(this.tracing, msg, stringMessage -> {
					log.info("Hello from simple manual [{}]", stringMessage.getPayload());
					return stringMessage;
				})).andThen(msg -> MessagingSleuthOperator.afterMessageHandled(this.tracing, msg, null)).andThen(msg -> MessagingSleuthOperator.toOutputMessage(this.tracing, msg))
				.andThen(msg -> MessageBuilder.createMessage(msg.getPayload().toUpperCase(), msg.getHeaders()))
				.andThen(msg -> MessagingSleuthOperator.afterMessageHandled(this.tracing, msg, null)).apply(input);
	}
}

class SimpleReactiveManualFunction implements Function<Flux<Message<String>>, Flux<Message<String>>> {

	private static final Logger log = LoggerFactory.getLogger(SimpleReactiveFunction.class);

	private final Tracing tracing;

	SimpleReactiveManualFunction(Tracing tracing) {
		this.tracing = tracing;
	}

	@Override
	public Flux<Message<String>> apply(Flux<Message<String>> input) {
		return input
				.map(msg -> MessagingSleuthOperator.forInputMessage(this.tracing, msg))
				.map(msg -> MessagingSleuthOperator.withSpanInScope(this.tracing, msg, stringMessage -> {
					log.info("Hello from simple reactive manual [{}]", stringMessage.getPayload());
					return stringMessage;
				})).map(msg -> MessagingSleuthOperator.afterMessageHandled(this.tracing, msg, null)).map(msg -> MessagingSleuthOperator.toOutputMessage(this.tracing, msg))
				.map(msg -> MessageBuilder.createMessage(msg.getPayload().toUpperCase(), msg.getHeaders()))
				.map(msg -> MessagingSleuthOperator.afterMessageHandled(this.tracing, msg, null));
	}
}

class SleuthNonReactiveFunction implements Function<String, String> {

	private static final Logger log = LoggerFactory.getLogger(SleuthNonReactiveFunction.class);

	private final ExecutorService executorService;

	SleuthNonReactiveFunction(ExecutorService executorService) {
		this.executorService = executorService;
	}

	@Override
	public String apply(String input) {
		log.info("Got a message");
		try {
			return this.executorService.submit(() -> {
				log.info("Logging [{}] from a new thread", input);
				return input.toUpperCase();
			}).get(20, TimeUnit.MILLISECONDS);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}

class SleuthFunction implements Function<Flux<String>, Flux<String>> {

	private static final Logger log = LoggerFactory.getLogger(SleuthFunction.class);

	@Override
	public Flux<String> apply(Flux<String> input) {
		return input
				.doOnEach(signal -> log.info("Got a message"))
				.flatMap(s -> Mono.delay(Duration.ofMillis(1), Schedulers.newParallel("foo")).map(aLong -> {
					log.info("Logging [{}] from flat map", s);
					return s.toUpperCase();
				}));
	}
}

class SleuthManualFunction implements Function<Flux<String>, Flux<String>> {

	private static final Logger log = LoggerFactory.getLogger(SleuthManualFunction.class);

	@Override
	public Flux<String> apply(Flux<String> input) {
		return input
				.doOnEach(WebFluxSleuthOperators.withSpanInScope(() -> log.info("Got a message")))
				.flatMap(s -> Mono.subscriberContext().delayElement(Duration.ofMillis(1), Schedulers.newParallel("foo")).map(ctx -> {
					WebFluxSleuthOperators.withSpanInScope(ctx, () -> log.info("Logging [{}] from flat map", s));
					return s.toUpperCase();
				}))
				.doOnEach(signal -> {
					WebFluxSleuthOperators.withSpanInScope(signal.getContext(), () -> log.info("Doing assertions"));
					TraceContext traceContext = signal.getContext().get(TraceContext.class);
					Assert.notNull(traceContext, "Context must be set by Sleuth instrumentation");
					Assert.state(traceContext.traceIdString().equals("4883117762eb9420"), "TraceId must be propagated");
					log.info("Assertions passed");
				});
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
				.flatMap(s -> Mono.delay(Duration.ofMillis(1), Schedulers.newParallel("foo")).map(aLong -> {
					log.info("Logging [{}] from flat map", s);
					return "";
				}))
				.doOnEach(signal -> {
					log.info("Doing assertions");
					Assert.isTrue(signal.getContext().isEmpty(), "Context must be empty");
					log.info("Assertions passed");
				});
	}

	@GetMapping("/foo")
	public Mono<String> foo() {
		return Flux.range(1, 10)
				.map(String::valueOf)
				.collect(Collectors.toList())
				.doOnEach(signal -> log.info("Got a request"))
				.flatMap(s -> Mono.delay(Duration.ofMillis(1), Schedulers.newParallel("foo")).map(aLong -> {
					log.info("Logging [{}] from flat map", s);
					return "";
				}))
				.doOnEach(signal -> {
					log.info("Doing assertions");
					TraceContext traceContext = signal.getContext().get(TraceContext.class);
					Assert.notNull(traceContext, "Context must be set by Sleuth instrumentation");
					Assert.state(traceContext.traceIdString().equals("4883117762eb9420"), "TraceId must be propagated");
					log.info("Assertions passed");
				});
	}

	@GetMapping("/fooManual")
	public Mono<String> fooManual() {
		return Flux.range(1, 10)
				.map(String::valueOf)
				.collect(Collectors.toList())
				.doOnEach(WebFluxSleuthOperators.withSpanInScope(() -> log.info("Got a request")))
				.flatMap(s -> Mono.subscriberContext().delayElement(Duration.ofMillis(1), Schedulers.newParallel("foo")).map(ctx -> {
					WebFluxSleuthOperators.withSpanInScope(ctx, () -> log.info("Logging [{}] from flat map", s));
					return "";
				}))
				.doOnEach(signal -> {
					WebFluxSleuthOperators.withSpanInScope(signal.getContext(), () -> log.info("Doing assertions"));
					TraceContext traceContext = signal.getContext().get(TraceContext.class);
					Assert.notNull(traceContext, "Context must be set by Sleuth instrumentation");
					Assert.state(traceContext.traceIdString().equals("4883117762eb9420"), "TraceId must be propagated");
					log.info("Assertions passed");
				});
	}
}