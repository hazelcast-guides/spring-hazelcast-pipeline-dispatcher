package com.hazelcast.solutions.pipeline.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.hazelcast.solutions.pipeline")
public class PipelineDispatcherApplication {

	public static void main(String[] args) {
		SpringApplication.run(PipelineDispatcherApplication.class, args);
	}

}
