package com.bot.elara.Infrastructure.External.Clients.ElaraApi.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterConsultRequest {
    
    // === IDENTIFICACIÓN (Obligatorios) ===
    private String whatsappId;
    
    private String consultaId; // Opcional - para actualizaciones
    
    private String padecimiento; // "Acne", "Caida de pelo", "Anti edad", "Rosacea", "Manchas", "Dermatitis", "Otro"
    
    private String email;
    
    @JsonProperty("Consulta")
    private Integer consulta; // 1: "para mi", 2: "para otra persona", 3: "menor de 18 años"
    
    // === DATOS PERSONALES (Obligatorios) ===
    private String nombreCompleto;
    
    private String genero; // "Masculino", "Femenino"
    
    private String fechaNacimiento; // Formato: "DD-MM-YYYY"
    
    private Double pesoKg;
    
    private Double alturaM;
    
    private Boolean fuma;
    
    // === HISTORIAL MÉDICO (Obligatorios) ===
    private String desdeCuando; // "Hace dias", "Hace semanas", "Hace meses", "Hace años"
    
    private Boolean tratamientoAnterior;
    
    private String tratamientosUsados;
    
    private Boolean alergias;
    
    private String alergiasDetalles; // Opcional
    
    private Boolean medicamentos;
    
    private String medicamentosDetalles; // Opcional
    
    private Boolean enfermedades;
    
    private String enfermedadesDetalles; // Opcional
    
    private String notadermatologo; // Opcional
    
    // === CAMPOS CONDICIONALES - CAÍDA DE PELO ===
    private String areaCaida; // "Entradas", "Coronilla", "Entradas + Coronilla"
    
    private String antecedentesFamiliares; // "Padre", "Madre", "Abuelos", "Hermanos", "NO", "No lo se"
    
    // === CAMPOS CONDICIONALES - GRAVEDAD ===
    private String gravedadAcne; // "Leve", "Moderado", "Grave", "Muy Grave"
    
    private String gravedadManchas; // "Leve", "Moderado", "Grave", "Muy Grave"
    
    private String gravedadRosacea; // "Leve", "Moderado", "Grave", "Muy Grave"
    
    // === CAMPOS CONDICIONALES - ANTI EDAD ===
    private String mejoraPrincipal; // "Arrugas", "Manchas", etc.
    
    private String tipoPiel; // "Mixta", "Seca", "Grasa", "Normal"
    
    private String sensibilidadPiel; // "Nunca", "A veces", "Siempre"
    
    private String exposicionSol; // "Diario", "Semanal", "Ocasional"
    
    private String usaProtector; // "Sí siempre", "A veces", "No uso"
    
    // === CAMPOS CONDICIONALES - GÉNERO FEMENINO ===
    private String statusEmbarazo; // "Ninguna", "Embarazada", "En lactancia"
    
    // === FOTOS Y NOTAS (Opcionales) ===
    private List<String> photoUrls;
    
    private String notasAdicionales;
    
    // === ACEPTACIONES (Obligatorias) ===
    private Boolean aceptaTerminos;
    
    private Boolean aceptaAvisoPrivacidad;
    
    private Boolean aceptaConsentimiento;
    
    // === PAGO (Obligatorios) ===
    private Boolean pagoProcesado;
    
    private String pagoId; // Opcional
    
    // === METADATA (Obligatorios) ===
    private String canalOrigen; // "WHATSAPP"
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime updatedAt;
}
