package com.bot.elara.Infrastructure.Persistence.Jpa;

import com.bot.elara.Domain.Model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataPatientRepository extends JpaRepository<Patient, String> {
    Optional<Patient> findByWhatsappId(String whatsappId);
}
