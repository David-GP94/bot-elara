package com.bot.elara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class ElaraBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(ElaraBotApplication.class, args);
	}

}
