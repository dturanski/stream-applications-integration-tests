package org.springframework.cloud.stream.apps.integration.test.source;

import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.apps.integration.test.AbstractStreamApplicationTests;
import org.springframework.cloud.stream.apps.integration.test.LogMatcher;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.stream.apps.integration.test.LogMatcher.endsWith;

public class HttpSourceTests extends AbstractStreamApplicationTests {
	private static WebClient webClient;

	private static int port = findAvailablePort();

	private static LogMatcher logMatcher = new LogMatcher(Duration.ofSeconds(30));

	@Container
	private static final DockerComposeContainer environment =
			new DockerComposeContainer(
					kafka(),
					resolveTemplate("compose-http-log.yml", Collections.singletonMap("port", port))
			)
					.withLogConsumer("log-sink", appLog("log-sink"))
					.withLogConsumer("log-sink", logMatcher)
					.withExposedService("http-source", port,
							Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

	@BeforeAll
	static void setup() {
		webClient = WebClient.builder().build();
	}

	@Test
	void plaintext() {
		ClientResponse response = webClient
				.post()
				.uri("http://localhost:" + port)
				.contentType(MediaType.TEXT_PLAIN)
				.body(Mono.just("Hello"), String.class)
				.exchange()
				.block();
		assertThat(response.statusCode().is2xxSuccessful()).isTrue();

		assertThat(logMatcher.waitFor(endsWith("Hello"))).isTrue();
	}

	@Test
	void json() {
		ClientResponse response = webClient
				.post()
				.uri("http://localhost:" + port)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Mono.just("{\"Hello\":\"world\"}"), String.class)
				.exchange()
				.block();
		assertThat(response.statusCode().is2xxSuccessful()).isTrue();
		assertThat(logMatcher.waitFor(".*\\{\"Hello\":\"world\"\\}")).isTrue();
	}
}
