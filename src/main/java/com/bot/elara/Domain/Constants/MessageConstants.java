// src/main/java/com/bot/elara/Domain/Constants/MessageConstants.java
package com.bot.elara.Domain.Constants;

import java.util.List;

public class MessageConstants {

    // M1 - Motivo consulta
    public static final String M_1 = "¡Comencemos! ¿Cuéntame, cuál es el motivo de tu consulta?";
    public static final List<String> M_1_OPTIONS = List.of(
            "Acné",
            "Caída de pelo",
            "Anti-edad",
            "Rosácea",
            "Manchas",
            "Dermatitis",
            "Otros"
    );

    public static final String M_2 = "¡Perfecto!, sigamos avanzando. ¿Cuál es tu correo electrónico? (terminando la consulta enviaremos tu contraseña a ese correo).";
    public static final String M_3 = "Confirma tu correo.";
    public static final String M_4 = "¿Eres mayor de 18 años y estás autorizado para llenar esta consulta?";
    public static final List<String> M_4_OPTIONS = List.of("Para mí", "Para otra persona.", "Menor de edad");

    public static final String M_5 = "¡Perfecto!, vamos ahora con tus datos generales. ¿Cuál es tu nombre completo?";
    public static final String M_6 = "¿Cuál es tu género de nacimiento?";
    public static final List<String> M_6_OPTIONS = List.of("Femenino", "Masculino");

    public static final String M_7 = "¡Gracias!, ¿cuál fecha de nacimiento? (formato: AAAA-MM-DD)";
    public static final String M_8 = "¿Cuál es tu peso? (Ej. 81 kg)";
    public static final String M_9 = "¿Cuál es tu altura? (Ej. 1.67)";
    public static final String M_10 = "¿Fumas?";
    public static final List<String> M_10_OPTIONS = List.of("Sí", "No");

    public static final String M_11 = "¡Listo! Empecemos con tu historial. ¿Desde cuándo tienes el problema?";
    public static final List<String> M_11_OPTIONS = List.of("Hace días", "Hace semanas", "Hace meses", "Hace años");

    // Gravedad por padecimiento
    public static final String M_12 = "¿Qué tan grave consideras tu acné?";
    public static final List<String> M_12_OPTIONS = List.of("Leve", "Moderado", "Grave", "Muy grave");

    public static final String M_37 = "¿Qué tan grave consideras tus manchas?";
    public static final List<String> M_37_OPTIONS = List.of("Leve(casi no se ven)", "Moderado(se notan)", "Grave (se ven mucho)");

    public static final String M_40 = "¿Qué tan grave consideras tu rosácea/enrojecimiento de la piel?";
    public static final List<String> M_40_OPTIONS = List.of("Leve", "Moderado", "c) Grave");

    public static final String M_13 = "Perfecto, esto es importante. ¿Has recibido tratamiento para el anteriormente?";
    public static final List<String> M_13_OPTIONS = List.of("No", "Sí");

    public static final String M_14 = "¿Podrías escribir en una linea cuáles tratamientos usaste?";
    public static final String M_15 = "¡Gracias! Ahora necesito saber si tienes alergias.";
    public static final List<String> M_15_OPTIONS = List.of("No", "Sí");
    public static final String M_16 = "Por favor ingresa que alergia tienes";

    public static final String M_17 = "¿Tomas algún medicamento, suplemento alimenticio y/o vitaminas?";
    public static final List<String> M_17_OPTIONS = List.of("a) No", "b) Sí");
    public static final String M_18 = "Por favor escribe en una linea los medicamentos, suplemento alimenticio o vitaminas que usas.";

    public static final String M_20 = "¡Muy bien! Ahora tus fotos. Puedes cargar hasta 5 imágenes del área del cuerpo sobre la que quieres consultar.";
    public static final List<String> M_20_OPTIONS = List.of("a) Cargar ahora", "b) Cargar después");
    public static final String M_21 = "Por favor adjunta tu fotografía";
    public static final String M_22 = "¿Deseas cargar mas imagenes?";
    public static final List<String> M_22_OPTIONS = List.of("a) No", "b) Sí");

    public static final String M_23 = "¡Listo! Gracias por completar tu consulta. Aquí tienes el link para realizar tu pago:";
    public static final String M_24 = "Tu pago fue procesado correctamente, tu dermatóloga revisará tu caso, entra a tu cuenta para darle seguimiento.";
    public static final String M_25 = "Hubo un error en tu pago, por favor intenta nuevamente";

    // Anti-edad
    public static final String M_26 = "¡Listo! Empecemos con tu historial. ¿Qué te gustaría mejorar de tu piel principalmente?";
    public static final List<String> M_26_OPTIONS = List.of("a) Manchas y tono desigual", "b) Arrugas o líneas finas", "c) Sequedad profunda", "d) Poros y textura");

    public static final String M_27 = "¿Cómo describirías tu tipo de piel?";
    public static final List<String> M_27_OPTIONS = List.of("a) Seca", "b) Mixta", "c) Grasa", "d) No lo sé");

    public static final String M_28 = "¿Qué tipo de sensibilidad en piel tienes?";
    public static final List<String> M_28_OPTIONS = List.of("a) Nunca se irrita", "b) A veces se enrojece", "c) Se irrita con facilidad");

    public static final String M_29 = " ¿Tienes alguna enfermedad?, si tu respuesta es sí, ¿cuál?";
    public static final List<String> M_29_OPTIONS = List.of("a) No", "b) Si");

    public static final String M_30 = "¿Te expones al sol?";
    public static final List<String> M_30_OPTIONS = List.of("a) Rara vez", "b) A veces", "c) Diario");

    public static final String M_31 = "¿Usas protector solar?";
    public static final List<String> M_31_OPTIONS = List.of("a) Sí, siempre", "b) A veces", "c) Nunca");

    // Caída de pelo
    public static final String M_34 = "¡Listo! Empecemos con tu historial. ¿En qué área notas la caída de pelo?";
    public static final List<String> M_34_OPTIONS = List.of("a) Área de entradas", "b) Área de la coronilla", "c) Área de las entradas + coronilla");

    public static final String M_35 = "¿Tienes familiares con antecedentes de caída de pelo?";
    public static final List<String> M_35_OPTIONS = List.of("a) Padre", "b) Madre", "c) Abuelo materno", "d) Hermanos", "e) No tengo familiares con antecedentes", "f) No lo sé");

    // Embarazo (solo mujeres)
    public static final String M_33 = "¡Muy bien! Esto es importante. Elige una opción.";
    public static final List<String> M_33_OPTIONS = List.of(
            "a) Tengo planes de embarazo a corto plazo",
            "b) Estoy embarazada",
            "c) Estoy lactando",
            "d) Ninguna de las anteriores"
    );

    // Notas adicionales por padecimiento
    public static final String M_19 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo sobre tu acné?";
    public static final String M_32 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo sobre tu skincare?";
    public static final String M_36 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo sobre tu caída de pelo?";
    public static final String M_38 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo sobre tus manchas?";
    public static final String M_39 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo?";
    public static final String M_41 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo sobre tu rosácea?";
}