/*
 * Copyright 2020-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.apps.integration.test.source;

import java.time.Duration;
import java.util.function.Consumer;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;

import org.springframework.cloud.fn.test.support.geode.GeodeContainer;
import org.springframework.cloud.stream.apps.integration.test.AbstractStreamApplicationTests;
import org.springframework.cloud.stream.apps.integration.test.LogMatcher;

import static org.awaitility.Awaitility.await;
import static org.springframework.cloud.stream.apps.integration.test.AbstractStreamApplicationTests.AppLog.appLog;
import static org.springframework.cloud.stream.apps.integration.test.FluentMap.fluentMap;

public class GeodeSourceTests extends AbstractStreamApplicationTests {

	private static LogMatcher logMatcher = new LogMatcher();

	private static LogMatcher geodeLogMatcher = new LogMatcher();

	private static int locatorPort = findAvailablePort();

	private static int cacheServerPort = findAvailablePort();

	@Container
	private static GeodeContainer geode = (GeodeContainer) new GeodeContainer(new ImageFromDockerfile()
			.withFileFromClasspath("Dockerfile", "geode/Dockerfile")
			.withBuildArg("CACHE_SERVER_PORT", String.valueOf(cacheServerPort))
			.withBuildArg("LOCATOR_PORT", String.valueOf(locatorPort)),
			locatorPort, cacheServerPort)
					.withCreateContainerCmdModifier(
							(Consumer<CreateContainerCmd>) createContainerCmd -> createContainerCmd
									.withHostName("geode").withHostConfig(new HostConfig().withPortBindings(
											new PortBinding(Ports.Binding.bindPort(cacheServerPort),
													new ExposedPort(cacheServerPort)),
											new PortBinding(Ports.Binding.bindPort(locatorPort),
													new ExposedPort(locatorPort)))))
					.withCommand("tail", "-f", "/dev/null")
					.withStartupTimeout(Duration.ofMinutes(2));

	@BeforeAll
	static void init() {
		geode.execGfsh("start locator --name=Locator1 " + "--hostname-for-clients=geode"
				+ " --port=" + locatorPort);
		geode.execGfsh("connect --locator=geode[" + locatorPort + "]",
				"start server --name=Server1 " + "--hostname-for-clients=geode" + " --server-port="
						+ cacheServerPort,
				"create region --name=myRegion --type=REPLICATE");
	}

	@Container
	private DockerComposeContainer environment = new DockerComposeContainer(
			kafka(),
			resolveTemplate("source/geode-source-tests.yml", fluentMap()
					.withEntry("geode.host-addresses", "geode:" + locatorPort)
					.withEntry("extraHosts", "geode:" + localHostAddress())
					.withEntry("geode.region", "myRegion")))
							.withLogConsumer("log-sink", appLog("log-sink"))
							.withLogConsumer("geode-source", geodeLogMatcher)
							.withLogConsumer("log-sink", logMatcher)
							.withLocalCompose(true);

	@Test
	void test() {
		LogMatcher.LogListener logListener = logMatcher.withRegex(LogMatcher.contains("world"));
		await().atMost(Duration.ofMinutes(2))
				.untilTrue(geodeLogMatcher.withRegex(LogMatcher.contains("Started GeodeSource")).matches());
		geode.connectAndExecGfsh("put --key=hello --value=world --region=myRegion");
		await().atMost(Duration.ofSeconds(30))
				.untilTrue(logListener.matches());
	}
}
