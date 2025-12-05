package com.bot.elara.Infrastructure.Persistence.Jpa;

import com.bot.elara.Domain.Model.Patient;
import com.bot.elara.Domain.Repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaPatientRepository implements PatientRepository {

    private final SpringDataPatientRepository jpa;

    @Override
    public Optional<Patient> findByWhatsappId(String whatsappId) {
        return jpa.findByWhatsappId(whatsappId);
    }

    @Override
    public Patient save(Patient patient) {
        return jpa.save(patient);
    }

    @Override
    public Boolean existsByWhatsappId(String whatsappId) {
        return jpa.existsById(whatsappId);
    }

    @Override
    public void flush() {
        jpa.flush();
    }
}
