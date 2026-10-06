package com.tailorcards.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.tailorcards.api", "com.tailorcards"})
public class ApiApplication {

	public static void main(String[] args) {
		String demoEnv = System.getenv("DEMO_MODE");
		if ("true".equalsIgnoreCase(demoEnv)) {
			String currentProfiles = System.getProperty("spring.profiles.active");
			if (currentProfiles == null || currentProfiles.isBlank()) {
				System.setProperty("spring.profiles.active", "demo");
			} else if (!currentProfiles.contains("demo")) {
				System.setProperty("spring.profiles.active", currentProfiles + ",demo");
			}
		}
		SpringApplication.run(ApiApplication.class, args);
	}

}
