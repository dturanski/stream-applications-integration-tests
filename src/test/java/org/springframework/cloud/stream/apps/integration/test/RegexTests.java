package org.springframework.cloud.stream.apps.integration.test;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RegexTests {
	@Test
	void test(){
		String value = "2020-08-31 01:44:22.154  INFO 1 --- [container-0-C-1] log-sink                                 : {\"id\":1,\"name\":\"Bart Simpson\",\"street\":\"Main Street\",\"city\":\"Springfield\"}";
		Pattern pattern = Pattern.compile(LogMatcher.contains("Bart Simpson"));
		assertThat(pattern.matcher(value).matches()).isTrue();
	}
}
