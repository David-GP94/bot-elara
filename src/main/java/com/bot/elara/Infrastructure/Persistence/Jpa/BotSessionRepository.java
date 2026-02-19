package com.bot.elara.Infrastructure.Persistence.Jpa;

import com.bot.elara.Domain.Model.BotSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BotSessionRepository extends JpaRepository<BotSession, Long> {
    Optional<BotSession> findByWhatsappId(String whatsappId);
}
