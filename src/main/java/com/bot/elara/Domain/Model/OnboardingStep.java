package com.bot.elara.Domain.Model;

// com.bot.elara.Domain.Model.OnboardingStep
public enum OnboardingStep {
    START,
    ASK_MOTIVO, // M-1
    ASK_EMAIL, // M-2
    CONFIRM_EMAIL, // M-3
    ASK_MAYORIA_EDAD, // M-4
    ASK_NOMBRE, // M-5
    ASK_GENERO, // M-6
    ASK_FECHA_NAC, // M-7
    ASK_PESO, // M-8
    ASK_ALTURA, // M-9
    ASK_FUMA, // M-10
    ASK_DESDE_CUANDO, // M-11
    ASK_GRAVEDAD, // M-12 para Acne, M-37 para Manchas, etc. (ramificar)
    ASK_TRATAMIENTO_ANTERIOR, // M-13
    ASK_TRATAMIENTOS_USADOS, // M-14
    ASK_ALERGIAS, // M-15
    ASK_ALERGIAS_DETALLES, // M-16
    ASK_MEDICAMENTOS, // M-17
    ASK_MEDICAMENTOS_DETALLES, // M-18
    ASK_ENFERMEDADES, // M-29
    ASK_ENFERMEDADES_DETALLES,
    // Específicos (ejemplos)
    ASK_MEJORA_PRINCIPAL, // M-26 Anti-edad
    ASK_TIPO_PIEL, // M-27
    ASK_SENSIBILIDAD_PIEL, // M-28
    ASK_EXPOSICION_SOL, // M-30
    ASK_USA_PROTECTOR, // M-31
    ASK_AREA_CAIDA, // M-34 Caida
    ASK_ANTECEDENTES_FAMILIA, // M-35
    // Mujeres
    ASK_STATUS_EMBARAZO, // M-33 si genero Femenino
    ASK_NOTAS_ADICIONALES, // M-19, M-32, etc. por padecimiento
    ASK_FOTOS, // M-20
    ASK_MAS_FOTOS, // M-22
    ASK_TERMINOS, // Enviar D1 y preguntar M-9
    ASK_AVISO_PRIVACIDAD, // D2 y M-10
    ASK_CONSENTIMIENTO, // D3 y M-11
    PROCESS_PAYMENT, // M-23
    COMPLETED,
    ERROR
}
