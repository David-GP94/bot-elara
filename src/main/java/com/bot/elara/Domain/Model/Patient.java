package com.bot.elara.Domain.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// com.bot.elara.Domain.Model.Patient
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Patient {
    @Id
    private String whatsappId; // O contactId de Respond.io

    private OnboardingStep currentStep;
    private String padecimiento; // e.g., "Acne", "Caida de Pelo"

    // Campos comunes
    private String email;
    private Boolean mayorEdad;
    private String nombreCompleto;
    private String genero; // "Femenino", "Masculino"
    private LocalDate fechaNacimiento;
    private Integer pesoKg;
    private Double alturaM;
    private Boolean fuma;

    // Campos generales
    private String desdeCuando; // "Hace dias", etc.
    private Boolean tratamientoAnterior;
    private String tratamientosUsados;
    private Boolean alergias;
    private String alergiasDetalles;
    private Boolean medicamentos;
    private String medicamentosDetalles;
    private Boolean enfermedades;
    private String enfermedadesDetalles;

    // Específicos por padecimiento (usa campos nullable o un Map<String, String> para flexibilidad)
    // Ej. para Acne
    private String gravedadAcne; // "Leve", etc.
    // Para Anti-edad
    private String mejoraPrincipal;
    private String tipoPiel;
    private String sensibilidadPiel;
    private String exposicionSol;
    private String usaProtector;
    // Para Caida Pelo
    private String areaCaida;
    private String antecedentesFamiliares; // JSON o comma-separated
    // Etc. para otros

    // Mujeres específicas
    private String statusEmbarazo; // Si es mujer

    // Fotos (lista, ya que hasta 5)
    @ElementCollection
    private List<String> photoUrls = new ArrayList<>();

    // Notas adicionales
    private String notasAdicionales;

    // Pago
    private Boolean pagoProcesado;
    private String pagoId; // De pasarela

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
