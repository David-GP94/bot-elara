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

    private String lastListContext; // ← SIN @Enumerated

    // === FLUJO PRINCIPAL ===
    private String padecimiento;

    private String email;
    private Boolean mayorEdad;
    private Boolean consultaParaOtraPersona;
    private String nombreCompleto;
    private String genero;
    private LocalDate fechaNacimiento;
    private Double pesoKg;
    private Double alturaM;
    private Boolean fuma;

    private String desdeCuando;

    private Boolean tratamientoAnterior;
    private String tratamientosUsados;

    private Boolean alergias;
    private String alergiasDetalles;

    private Boolean medicamentos;
    private String medicamentosDetalles;

    private Boolean enfermedades;
    private String enfermedadesDetalles;

    private String gravedadAcne;
    private String gravedadManchas;
    private String gravedadRosacea;

    private String mejoraPrincipal;
    private String tipoPiel;
    private String sensibilidadPiel;
    private String exposicionSol;
    private String usaProtector;

    private String areaCaida;
    private String antecedentesFamiliares;

    private String statusEmbarazo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "patient_photos", joinColumns = @JoinColumn(name = "patient_whatsapp_id"))
    @Column(name = "photo_url")
    private List<String> photoUrls = new ArrayList<>();

    private String notasAdicionales;

    private Boolean pagoProcesado;
    private String pagoId;
    private Integer metodoPagoElegido;
    @Column(columnDefinition = "TEXT")
    private String paymentUrl;
    private String codigoDescuento;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastImageReceivedAt;
    private Integer pendingImageCount;

    private Boolean pendingInactivityResponse = false;

    public Patient() {
        this.photoUrls = new ArrayList<>();
    }
}
