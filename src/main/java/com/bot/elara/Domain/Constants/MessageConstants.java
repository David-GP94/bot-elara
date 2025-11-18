package com.bot.elara.Domain.Constants;

import java.util.List;

public class MessageConstants {

    // Mensajes comunes y por padecimiento (copiados tal cual del Excel)
    public static final String M_1 = "¡Comencemos! ¿Cuéntame, cuál es el motivo de tu consulta?";
    public static final String M_2 = "¡Perfecto!, sigamos avanzando. ¿Cuál es tu correo electrónico? (terminando la consulta enviaremos tu contraseña a ese correo).";
    public static final String M_3 = "Confirma tu correo.";
    public static final String M_4 = "¿Eres mayor de 18 años y estás autorizado para llenar esta consulta?";
    public static final String M_5 = "¡Perfecto!, vamos ahora con tus datos generales. ¿Cuál es tu nombre completo? ";
    public static final String M_6 = "¿Cuál es tu género de nacimiento?";
    public static final String M_7 = "¡Gracias!, ¿cuál fecha de nacimiento?";
    public static final String M_8 = "¿Cuál es tu peso?  (Ej. 81 kg)";
    public static final String M_9 = "¿Cuál es tu altura? (Ej. 1.67)";
    public static final String M_10 = "¿Fumas?";
    public static final String M_11 = "¡Listo! Empecemos con tu historial. ¿Desde cuándo tienes el problema?";
    public static final String M_12 = "¿Qué tan grave consideras tu acné?";
    public static final String M_13 = "Perfecto, esto es importante. ¿Has recibido tratamiento para el  anteriormente? ";
    public static final String M_14 = "¿Podrías escribir en una linea cuáles tratamientos usaste?";
    public static final String M_15 = "¡Gracias! Ahora necesito saber si tienes alergias. si tu respuesta es sí, ¿cuáles?";
    public static final String M_16 = "Por favor ingresa que alergia tienes";
    public static final String M_17 = "¿Tomas algún medicamento, suplemento alimenticio y/o vitaminas?, Si tu respuesta es sí, ¿cuáles?";
    public static final String M_18 = "Por favor escribe en una linea los medicamentos, suplemento alimenticio o vitaminas que usas.";
    public static final String M_19 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo? (Por ejemplo: cambios recientes de cremas, exposición al sol, días donde empeora el acné, etc).";
    public static final String M_20 = "¡Muy bien! Ahora tus fotos. “Puedes cargar hasta 5 imágenes del área del cuerpo sobre la que quieres consultar. (Si prefieres, envía tus fotos más tarde mediante el chat con tu dermatólogo.)”";
    public static final String M_21 = "Por favor adjunta tu fotografia";
    public static final String M_22 = "¿Deseas cargar mas imagenes?";
    public static final String M_23 = "¡Listo! Gracias por completar tu consulta. Aquí tienes el link para realizar tu pago.";
    public static final String M_24 = "Tu gago fue procesado correctamente, tu dermatóloga revisará tu caso, entra a tu cuenta para darle seguimiento.   (Enviar link para el panel administrativo)";
    public static final String M_25 = "Hubo un error en tu pago, por favor intenta nuevamente";
    public static final String M_26 = "¡Listo! Empecemos con tu historial. ¿Qué te gustaría mejorar de tu piel principalmente?";
    public static final String M_27 = "¿Cómo describirías tu tipo de piel?";
    public static final String M_28 = "Perfecto, esto es importante. ¿Qué tipo de sensibilidad en piel tienes? ";
    public static final String M_29 = "¿Tienes alguna enfermedad?, si tu respuesta es sí, ¿cuál?";
    public static final String M_30 = "¿Te expones al sol?";
    public static final String M_31 = "¡Bien! Estamos por terminar. ¿Usas protector solar?";
    public static final String M_32 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo? (Por ejemplo: cambios recientes de cremas, comes saludable, cuánta agua tomas, etc).";
    public static final String M_33 = "¡Muy bien! Esto es importante. Elige une opción.";
    public static final String M_34 = "¡Listo! Empecemos con tu historial. ¿En qué área notas la caída de pelo?";
    public static final String M_35 = "¿Tienes familiares con antecedentes de caída de pelo?";
    public static final String M_36 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo? (Por ejemplo: ¿La caída empezó de repente o ha sido progresiva?, tienes episodios de estrés, notas más la caída cuando te bañas, etc).";
    public static final String M_37 = "¿Qué tan grave consideras tus manchas?";
    public static final String M_38 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo? (Por ejemplo: cambios recientes de cremas, exposición al sol, días donde empeora las manchas,como empezo tood,  alguien de tu familia tiene estas manchas? Mientras más información nos compartas mejor para tu Derma. ";
    public static final String M_39 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo? (Por ejemplo: el problema empeoró recientemente, hay antecedentes en tu familia, te afecta otras áreas del cuerpo, etc).";
    public static final String M_40 = "¿Qué tan grave consideras tu rosácea/ enorojocemiento de la piel?";
    public static final String M_41 = "Y por último, ¿Tienes algo más que compartirle a tu dermatólogo? (Por ejemplo: cambios recientes de cremas, usas protector solar, exposición al sol, días donde empeora la cara roja o sensible,  como inicio el problema, alguien de tu familia tiene este problema? Mientras más información nos compartas mejor para tu Derma.";

    // Opciones para selects (solo para los que tienen respuestas desplegables)
    public static final List<String> M_1_OPTIONS = List.of("Acné", "Caída de pelo", "Anti-edad/Skincare", "Rosácea", "Manchas", "Dermatitis", "Otros / No sé qué tengo");
    public static final List<String> M_4_OPTIONS = List.of("Sí, tengo más de 18 años y la consulta es para mí.", "Sí, tengo más de 18 años y lleno la consulta para otra persona.", "No");
    public static final List<String> M_6_OPTIONS = List.of("Femenino", "Masculino");
    public static final List<String> M_10_OPTIONS = List.of("Sí", "No");
    public static final List<String> M_11_OPTIONS = List.of("Hace días", "Hace semanas", "Hace meses", "Hace años");
    public static final List<String> M_12_OPTIONS = List.of("Leve", "Moderado", "Grave", "Muy grave");
    public static final List<String> M_13_OPTIONS = List.of("No", "Sí");
    public static final List<String> M_15_OPTIONS = List.of("No", "Sí.");
    public static final List<String> M_17_OPTIONS = List.of("No", "Sí, “el usuario escribe cuales”.");
    public static final List<String> M_20_OPTIONS = List.of("Cargar ahora", "Cargar después");
    public static final List<String> M_22_OPTIONS = List.of("No", "Sí.");
    public static final List<String> M_26_OPTIONS = List.of("Manchas y tono desigual", "Arrugas o líneas finas", "Sequedad profunda", "Poros y textura");
    public static final List<String> M_27_OPTIONS = List.of("Seca", "Mixta", "Grasa", "No lo sé");
    public static final List<String> M_28_OPTIONS = List.of("Nunca se irrita", "A veces se enrojece", "Se irrita con facilidad");
    public static final List<String> M_29_OPTIONS = List.of("No", "Sí, “el usuario escribe cuales”.");
    public static final List<String> M_30_OPTIONS = List.of("Rara vez", "A veces", "Diario");
    public static final List<String> M_31_OPTIONS = List.of("Sí, siempre.", "A veces", "Nunca");
    public static final List<String> M_33_OPTIONS = List.of("Tengo planes de embarazo a corto plazo", "Estoy embarazada", "Estoy lactando", "Ninguna de las anteriores");
    public static final List<String> M_34_OPTIONS = List.of("Área de entradas", "Área de la coronilla", "Área de las entradas + coronilla.");
    public static final List<String> M_35_OPTIONS = List.of("Padre", "Madre", "Abuelo materno", "Hermanos", "No tengo familiares con antecedentes de caída de pelo", "No lo sé");
    public static final List<String> M_37_OPTIONS = List.of("Leve ( casi no se ven, casi imperceptibles )", "Moderado ( son notables )", "Grave ( se ven bastante y muy marcadas)");
    public static final List<String> M_40_OPTIONS = List.of("Leve ( solo me veo rojito )", "Moderado ( me veo rojo y además tengo mucha sensibilidad en la piel  )", "Grave ( me veo rojo ,  tengo mucha sensibilidad en la piel  y me salieron granitos)");
}