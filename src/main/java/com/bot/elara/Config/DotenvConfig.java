package com.bot.elara.Config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;
import org.springframework.context. annotation.Bean;
import org. springframework.context.annotation.Configuration;
import org.springframework.web. client.RestTemplate;

import jakarta.annotation.PostConstruct;

@Configuration
public class DotenvConfig {

    @PostConstruct
    public void loadEnv() {
        Dotenv dotenv = Dotenv.configure()
                .directory("./")  // Busca .env en la raíz
                .ignoreIfMissing() // No falla si no existe
                .load();

        // Carga todas las variables en System.properties
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
        );
    }
}