package com.bot.elara.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            jdbcTemplate.execute("IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'derma_onboarding') CREATE DATABASE derma_onboarding;");
            System.out.println("✅ Base de datos 'derma_onboarding' verificada o creada correctamente.");
        } catch (Exception e) {
            System.err.println("⚠️ Error al crear/verificar la base de datos: " + e.getMessage());
        }
    }
}
