package org.springframework.cloud.stream.apps.integration.test.source;

import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.apps.integration.test.AbstractStreamApplicationTests;
import org.springframework.cloud.stream.apps.integration.test.LogMatcher;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.stream.apps.integration.test.LogMatcher.contains;

public class JdbcSourceTests extends AbstractStreamApplicationTests {

	private static LogMatcher logMatcher = new LogMatcher(Duration.ofSeconds(30));

	@Container
	private static final DockerComposeContainer environment =
			new DockerComposeContainer(
					kafka(),
					resolveTemplate("compose-jdbc-log.yml", Collections.singletonMap("init.sql", resourceAsFile("init.sql")))
			)
					.withLogConsumer("log-sink", logMatcher)
					.waitingFor("jdbc-source", Wait.forLogMessage(contains("Started JdbcSource"), 1)
							.withStartupTimeout(Duration.ofMinutes(2)));

	@Test
	void test() {
		assertThat(logMatcher.waitFor(contains("Bart Simpson"))).isTrue();
	}
}
