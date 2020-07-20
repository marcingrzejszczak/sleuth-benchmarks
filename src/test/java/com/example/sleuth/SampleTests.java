package com.example.sleuth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import brave.Tracing;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;


@Disabled
public class SampleTests {

	@Test
	public void testStream() throws Exception {
		for (BenchmarkContext.Instrumentation value : BenchmarkContext.Instrumentation.values()) {
			run(value);
		}
//		run(BenchmarkContext.Instrumentation.sleuthReactiveSimpleManual);
	}

	private void run(BenchmarkContext.Instrumentation value) throws Exception {
		BenchmarkContext context = new BenchmarkContext();
		System.out.println("\n\n\n\n WILL WORK WITH [" + value + "]\n\n\n\n");
		context.instrumentation = value;
		context.setup();

		try {
			context.run(value);
		}
		finally {
			context.clean();
		}
		System.out.println("\n\n FINISHED WITH [" + value + "]\n\n\n\n");
	}

	public static class BenchmarkContext {

		public enum Instrumentation {

			noSleuthSimple("spring.sleuth.enabled=false,spring.sleuth.function.type=simple"),
			sleuthSimple("spring.sleuth.function.type=simple"),
			noSleuthReactiveSimple("spring.sleuth.enabled=false,spring.sleuth.function.type=reactive_simple"),
			sleuthReactiveSimpleOnEach("spring.sleuth.reactor.instrumentation-type=DECORATE_ON_EACH,spring.sleuth.integration.enabled=true,spring.sleuth.function.type=DECORATE_ON_EACH"),
			//						 This won't work with messaging
//			sleuthReactiveSimpleOnLast("spring.sleuth.reactor.instrumentation-type=DECORATE_ON_LAST,spring.sleuth.function.type=DECORATE_ON_LAST"),
			// NO FUNCTION, NO INTEGRATION, MANUAL OPERATORS
			sleuthSimpleManual("spring.sleuth.function.enabled=false,spring.sleuth.integration.enabled=false,spring.sleuth.function.type=simple_manual"),
			sleuthReactiveSimpleManual("spring.sleuth.function.enabled=false,spring.sleuth.integration.enabled=false,spring.sleuth.function.type=reactive_simple_manual"),
			// NO FUNCTION - OLD INTEGRATION STYLE
			sleuthSimpleNoFunctionInstrumentationManual("spring.sleuth.function.type=simple_manual,spring.sleuth.function.enabled=false,spring.sleuth.integration.enabled=true,spring.sleuth.reactor.instrumentation-type=MANUAL"),
			sleuthReactiveSimpleNoFunctionInstrumentationManual("spring.sleuth.function.type=reactive_simple_manual,spring.sleuth.function.enabled=false,spring.sleuth.integration.enabled=true,spring.sleuth.reactor.instrumentation-type=MANUAL");

			private Set<String> entires = new HashSet<>();

			Instrumentation(String key, String value) {
				this.entires.add(key + "=" + value);
			}

			Instrumentation(String commaSeparated) {
				this.entires.addAll(StringUtils.commaDelimitedListToSet(commaSeparated));
			}

		}

		@Param
		private Instrumentation instrumentation;

		volatile ConfigurableApplicationContext applicationContext;

		volatile InputDestination input;

		volatile OutputDestination output;

		@Setup
		public void setup() {
			this.applicationContext = initContext();
			this.input = this.applicationContext.getBean(InputDestination.class);
			this.output = this.applicationContext.getBean(OutputDestination.class);
		}

		protected ConfigurableApplicationContext initContext() {
			SpringApplication application = new SpringApplicationBuilder(
					SleuthWebfluxBenchmarkSampleApplication.class).web(WebApplicationType.REACTIVE)
					.application();
			return application.run(runArgs());
		}

		protected String[] runArgs() {
			List<String> strings = new ArrayList<>();
			strings.addAll(Arrays.asList("--spring.jmx.enabled=false",
					"--spring.application.name=defaultTraceContextForStream" + instrumentation.name()));
			strings.addAll(instrumentation.entires.stream().map(s -> "--" + s).collect(Collectors.toList()));
			return strings.toArray(new String[0]);
		}

		void run(Instrumentation value) {
			System.out.println("Sending the message to input");
			input.send(MessageBuilder.withPayload("hello".getBytes())
					.setHeader("b3", "4883117762eb9420-4883117762eb9420-1").build());
			System.out.println("Retrieving the message for tests");
			Message<byte[]> message = output.receive(200L);
			System.out.println("Got the message from output");
			assertThat(message).isNotNull();
			System.out.println("Message is not null");
			assertThat(message.getPayload()).isEqualTo("HELLO".getBytes());
			System.out.println("Payload is HELLO");
			if (!value.toString().toLowerCase().contains("nosleuth")) {
				String b3 = message.getHeaders().get("b3", String.class);
				System.out.println("Checking the b3 header [" + b3 + "]");
				assertThat(b3).startsWith("4883117762eb9420");
			}
		}

		@TearDown
		public void clean() throws Exception {
			Tracing current = Tracing.current();
			if (current != null) {
				current.close();
			}
			try {
				this.applicationContext.close();
			}
			catch (Exception ig) {

			}
		}

	}

	@org.springframework.context.annotation.Configuration
	@Import(TestChannelBinderConfiguration.class)
	static class Configuration {

	}
}