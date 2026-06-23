package com.acme.platform;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.acme.platform.quote.DemoSeeder;

@SpringBootApplication
public class PlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlatformApplication.class, args);
	}

	/** Self-seed the stable demo quote on startup so it is always resolvable (brief §9). */
	@Bean
	CommandLineRunner seedDemo(DemoSeeder demo) {
		return args -> demo.ensureSeeded();
	}

}
