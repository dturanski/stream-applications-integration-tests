package org.springframework.cloud.stream.apps.integration.test;

import java.time.Duration;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.testcontainers.containers.output.OutputFrame;

public class LogMatcher implements Consumer<OutputFrame> {
	private static Logger logger = LoggerFactory.getLogger(LogMatcher.class);
	private Duration timeout = Duration.ofMinutes(5);
	private List<Consumer<String>> listeners = new LinkedList<>();
	private TaskExecutor executor = new SimpleAsyncTaskExecutor();

	public LogMatcher(Duration timeout) {
		this.timeout = timeout;
	}

	public LogMatcher() {
	}

	public static String contains(String string) {
		return ".*" + string + ".*";
	}

	public static String endsWith(String string) {
		return ".*" + string;
	}

	@Override
	public void accept(OutputFrame outputFrame) {
		listeners.forEach(m -> m.accept(outputFrame.getUtf8String()));
	}

	public boolean waitFor(String regex) {
		LogListener logListener = new LogListener(regex);
		listeners.add(logListener);
		Callable callable = (Callable) () -> {
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
			logger.trace(this + "matching " + s.trim() + " using pattern " + pattern.pattern());
			if (pattern.matcher(s.trim()).matches()) {
				logger.debug(" MATCHED " + s.trim());
				matched.set(true);
			}
		}

		boolean matched() {
			return matched.get();
		}
	}
}
