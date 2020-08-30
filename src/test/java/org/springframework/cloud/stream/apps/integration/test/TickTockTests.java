package org.springframework.cloud.stream.apps.integration.test;

import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;

import static org.assertj.core.api.Assertions.assertThatCode;

public class TickTockTests extends AbstractStreamApplicationTests {
	//"MM/dd/yy HH:mm:ss";
	private final Pattern pattern = Pattern.compile(".*\\d{2}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\n");

	@Container
	private final DockerComposeContainer environment =
			new DockerComposeContainer(
					kafka(),
					resourceAsFile("compose-time-log.yml")
			);

	@Test
	void ticktock() {
		assertThatCode(()-> environment.waitingFor("log-sink", Wait.forLogMessage(pattern.pattern(), 5)
				.withStartupTimeout(Duration.ofMinutes(2)))).doesNotThrowAnyException();
	}
}
