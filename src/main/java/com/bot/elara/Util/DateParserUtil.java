package com.bot.elara.Util;

import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Component
public class DateParserUtil {

    private static final List<DateTimeFormatter> FORMATTERS = List.of(
            // Formatos más usados en México / Latam
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd MM yyyy"),
            DateTimeFormatter.ofPattern("ddMMyyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d M yyyy"),

            // Con nombre del mes en español
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd/MMMM/yyyy", Locale.forLanguageTag("es")),

            // Formato americano (por si acaso)
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy")
    );

    public LocalDate parseFechaNacimiento(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String cleaned = input.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase()
                .replaceAll(" de ", " de ");

        // Intento con los formatters comunes
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDate.parse(cleaned, formatter);
            } catch (DateTimeParseException e) {
                // Continúa con el siguiente
            }
        }

        // Último recurso: solo números (ddMMyy o ddMMyyyy)
        return parseFromDigitsOnly(cleaned);
    }

    private LocalDate parseFromDigitsOnly(String input) {
        String digits = input.replaceAll("\\D", "");
        if (digits.length() == 6) {
            // Formato ddMMyy → asumimos siglo
            int day = Integer.parseInt(digits.substring(0, 2));
            int month = Integer.parseInt(digits.substring(2, 4));
            int year = Integer.parseInt(digits.substring(4, 6));
            year = year < 30 ? 2000 + year : 1900 + year;
            return LocalDate.of(year, month, day);
        }
        if (digits.length() == 8) {
            // Formato ddMMyyyy
            return LocalDate.of(
                    Integer.parseInt(digits.substring(4, 8)),
                    Integer.parseInt(digits.substring(2, 4)),
                    Integer.parseInt(digits.substring(0, 2))
            );
        }
        return null;
    }
}
