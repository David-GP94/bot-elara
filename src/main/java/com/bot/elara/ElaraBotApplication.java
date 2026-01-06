package com.bot.elara;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@Slf4j
public class ElaraBotApplication {

	public static void main(String[] args) {
		log.info("🚀 ========================================");
		log.info("🚀 Iniciando Elara Bot Application");
		log.info("🚀 Feign Clients habilitados: @EnableFeignClients");
		log.info("🚀 ========================================");
		SpringApplication.run(ElaraBotApplication.class, args);
	}

}
