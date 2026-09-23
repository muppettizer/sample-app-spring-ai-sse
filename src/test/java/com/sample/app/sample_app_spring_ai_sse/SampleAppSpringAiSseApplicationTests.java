package com.sample.app.sample_app_spring_ai_sse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SampleAppSpringAiSseApplicationTests {

	@Test
	void contextLoads() {
	}

}
