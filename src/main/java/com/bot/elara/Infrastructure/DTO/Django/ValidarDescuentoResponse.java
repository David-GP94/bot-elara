package com.bot.elara.Infrastructure.DTO.Django;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ValidarDescuentoResponse {

    @JsonProperty("codigo_valido")
    private Boolean codigoValido;

    @JsonProperty("precio_original")
    private Double precioOriginal;

    private Double descuento;

    @JsonProperty("precio_final")
    private Double precioFinal;
}

