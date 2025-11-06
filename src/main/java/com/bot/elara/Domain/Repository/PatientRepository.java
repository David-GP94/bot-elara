package com.bot.elara.Domain.Repository;

import com.bot.elara.Domain.Model.Patient;

import java.util.Optional;

public interface PatientRepository {
    Optional<Patient> findByWhatsappId(String whatsappId);
    Patient save(Patient patient);
    Boolean existsByWhatsappId(String whatsappId);
}
