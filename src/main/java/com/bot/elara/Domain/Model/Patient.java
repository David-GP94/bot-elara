package com.bot.elara.Domain.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@AllArgsConstructor
public class Patient {

    @Id
    private String whatsappId;

    @Enumerated(EnumType.STRING)
    private OnboardingStep currentStep;
    private String lastListContext;

    // === FLUJO PRINCIPAL ===
    private String padecimiento; // Acne, Caida de Pelo, Anti-edad, Rosacea, Manchas, Dermatitis, Otros

    private String email;
    private Boolean mayorEdad; // true si es mayor o está autorizado
    private Boolean consultaParaOtraPersona; // true si la consulta es para otra persona
    private String nombreCompleto; // del paciente o de la persona consultada
    private String genero; // "Femenino", "Masculino"
    private LocalDate fechaNacimiento;
    private Double pesoKg;
    private Double alturaM;
    private Boolean fuma;

    private String desdeCuando; // Hace días, semanas, meses, años

    // === TRATAMIENTOS Y ANTECEDENTES MÉDICOS ===
    private Boolean tratamientoAnterior;
    private String tratamientosUsados;

    private Boolean alergias;
    private String alergiasDetalles;

    private Boolean medicamentos;
    private String medicamentosDetalles;

    private Boolean enfermedades;
    private String enfermedadesDetalles;

    // === GRAVEDAD POR PADECIMIENTO (campos específicos) ===
    private String gravedadAcne;      // Leve, Moderado, Grave, Muy grave
    private String gravedadManchas;   // Leve, Moderado, Grave
    private String gravedadRosacea;   // Leve, Moderado, Grave

    // === ANTI-EDAD / SKINCARE ===
    private String mejoraPrincipal;     // Manchas, Arrugas, Sequedad, Poros
    private String tipoPiel;            // Seca, Mixta, Grasa, No lo sé
    private String sensibilidadPiel;    // Nunca, A veces, Con facilidad
    private String exposicionSol;       // Rara vez, A veces, Diario
    private String usaProtector;        // Sí siempre, A veces, Nunca

    // === CAÍDA DE PELO ===
    private String areaCaida;           // Entradas, Coronilla, Ambas
    private String antecedentesFamiliares; // Padre, Madre, Abuelo materno, etc. (puede ser texto libre)

    // === MUJERES ===
    private String statusEmbarazo; // Tengo planes, Estoy embarazada, Lactando, Ninguna

    // === FOTOS ===
    @ElementCollection
    @CollectionTable(name = "patient_photos", joinColumns = @JoinColumn(name = "patient_whatsapp_id"))
    @Column(name = "photo_url")
    private List<String> photoUrls = new ArrayList<>();

    // === NOTAS ADICIONALES ===
    private String notasAdicionales;

    // === PAGO Y ESTADO ===
    private Boolean pagoProcesado;
    private String pagoId; // ID del pago en Stripe, Mercado Pago, etc.

    // === AUDITORÍA ===
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructor por defecto para JPA
    public Patient() {
        this.photoUrls = new ArrayList<>();
        this.currentStep = OnboardingStep.START;
    }
}