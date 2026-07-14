package com.senamed.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class SenamedBackendApplication {

	public static void main(String[] args) {
		// spring.jpa.properties.hibernate.jdbc.time_zone=UTC (application.properties) tells
		// Hibernate to bind/read every LocalDateTime/Instant column using a UTC calendar. That is
		// only a no-op if the JVM's own default timezone is also UTC - on a host whose OS timezone
		// is something else (e.g. this dev machine's Brasilia time, UTC-3), Hibernate ends up
		// applying a real offset shift when converting between "JVM-local" and "UTC" calendars,
		// silently storing wall-clock values that are off by the host's UTC offset. Pinning the JVM
		// default to UTC here removes that ambiguity everywhere, independent of the host OS.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(SenamedBackendApplication.class, args);
	}

}
