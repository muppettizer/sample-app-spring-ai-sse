package com.sample.app.sample_app_spring_ai_sse;

import org.springframework.boot.SpringApplication;

public class TestSampleAppSpringAiSseApplication {

	public static void main(String[] args) {
		SpringApplication.from(SampleAppSpringAiSseApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
