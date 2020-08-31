package org.springframework.cloud.stream.apps.integration.test;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.SocketUtils;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class AbstractStreamApplicationTests {

	protected final static String STREAM_APPS_VERSION = "3.0.0-SNAPSHOT";
	public static final String STREAM_APPS_VERSION_KEY = "stream.apps.version";

	protected static Path tempDir;

	protected static File kafka() {
		return resourceAsFile("compose-kafka.yml");
	}

	protected static File resourceAsFile(String path) {
		try {
			return new ClassPathResource(path).getFile();
		} catch (IOException e) {
			throw new IllegalStateException("Unable to access resource " + path);
		}
	}

	//Junit TempDir does not work with DockerComposeContainer unless you mount it.
	//Also doesn't work as @BeforeAll in this case.
	static void initializeTempDir() throws IOException {
		Path tempRoot = Paths.get(new ClassPathResource("/").getFile().getAbsolutePath());
		if (tempDir == null) {
			tempDir = Files.createTempDirectory(tempRoot, UUID.randomUUID().toString());
			tempDir.toFile().deleteOnExit();
		}
	}

	protected static int findAvailablePort() {
		return SocketUtils.findAvailableTcpPort(10000, 20000);
	}

	protected static File resolveTemplate(String templatePath, Map<String, Object> templateProperties) {
		try {
			initializeTempDir();
			try (InputStreamReader resourcesTemplateReader = new InputStreamReader(
					Objects.requireNonNull(new ClassPathResource(templatePath).getInputStream()))) {
				Template resourceTemplate = Mustache.compiler().escapeHTML(false).compile(resourcesTemplateReader);
				Path temporaryFile = Files.createFile(tempDir.resolve(templatePath));
				Files.write(temporaryFile,
						resourceTemplate.execute(addGlobalProperties(templateProperties)).getBytes()).toFile();
				temporaryFile.toFile().deleteOnExit();
				return temporaryFile.toFile();
			}
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	private static Map<String, Object> addGlobalProperties(Map<String, Object> templateProperties) {
		if (templateProperties.containsKey(STREAM_APPS_VERSION)) {
			return templateProperties;
		}
		Map<String, Object> enriched = new HashMap<>(templateProperties);
		enriched.put(STREAM_APPS_VERSION_KEY, STREAM_APPS_VERSION);
		return enriched;
	}

	private static class AppLog extends Slf4jLogConsumer {
		public AppLog(String appName) {
			super(LoggerFactory.getLogger(appName));
		}
	}

	protected static AppLog appLog(String appName) {
		return new AppLog(appName);
	}


}
