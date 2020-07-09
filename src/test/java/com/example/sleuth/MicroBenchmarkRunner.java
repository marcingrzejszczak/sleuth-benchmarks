package com.example.sleuth;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class MicroBenchmarkRunner {

	// Convenience main entry-point for testing from IDE
	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(MicroBenchmarkRunner.class.getPackage().getName() + ".*")
				.build();

		new Runner(opt).run();
	}
}