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

	protected static Path tempDir;

	protected static final File kafka() {
		return resourceAsFile("compose-kafka.yml");
	}

	protected static final File resourceAsFile(String path) {
		try {
			return new ClassPathResource(path).getFile();
		} catch (IOException e) {
			throw new IllegalStateException("Unable to access resource " + path);
		}
	}

	//Junit TempDir does not work with TestContainers unless you mount it.
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
						resourceTemplate.execute(templateProperties).getBytes()).toFile();
				temporaryFile.toFile().deleteOnExit();
				return temporaryFile.toFile();
			}
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	private static class AppLog extends Slf4jLogConsumer {
		public AppLog(String appName) {
			super(LoggerFactory.getLogger(appName));
		}
	}

	protected static AppLog appLog(String appName) {
		return new AppLog(appName);
	}

	protected static class LogMatcher implements Consumer<OutputFrame> {
		private static Logger logger = LoggerFactory.getLogger(LogMatcher.class);
		private Duration timeout = Duration.ofMinutes(5);
		private List<Consumer<String>> listeners = new LinkedList<>();
		private TaskExecutor executor = new SimpleAsyncTaskExecutor();

		protected LogMatcher(Duration timeout) {
			this.timeout = timeout;
		}

		protected LogMatcher() {
		}

		@Override
		public void accept(OutputFrame outputFrame) {
			listeners.forEach(m -> m.accept(outputFrame.getUtf8String()));
		}

		protected boolean waitFor(String regex) {
			LogListener logListener = new LogListener(regex);
			listeners.add(logListener);
			Callable<Boolean> callable = (Callable) () -> {
				Date start = new Date();
				while (!logListener.matched()) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						Thread.interrupted();
					}
					if (Duration.ofMillis(new Date().getTime() - start.getTime()).compareTo(timeout) >= 0) {
						return false;
					}
				}
				return true;
			};

			boolean matched;
			try {
				FutureTask<Boolean> futureTask = new FutureTask<>(callable);
				executor.execute(futureTask);
				matched = futureTask.get();
			} catch (Exception e) {
				throw new IllegalStateException(e.getMessage(), e);
			}
			return matched;
		}

		class LogListener implements Consumer<String> {
			private AtomicBoolean matched = new AtomicBoolean();
			private final Pattern pattern;

			LogListener(String regex) {
				pattern = Pattern.compile(regex);
			}
			@Override
			public void accept(String s) {
				if (pattern.matcher(s).matches()) {
					logger.debug(this + " MATCHED " + s);
					matched.set(true);
				}
			}
			boolean matched() {
				return matched.get();
			}
		}
	}
}
