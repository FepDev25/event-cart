package com.eventcart;

import org.springframework.boot.SpringApplication;

public class TestEventCartApplication {

	public static void main(String[] args) {
		SpringApplication.from(EventCartApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
