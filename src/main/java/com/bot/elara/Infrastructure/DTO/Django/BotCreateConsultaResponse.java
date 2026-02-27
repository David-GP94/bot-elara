package com.bot.elara.Infrastructure.DTO.Django;

import lombok.Data;

@Data
public class BotCreateConsultaResponse {

    private Boolean success;
    private Long consulta_id;
    private String public_id;

    private Double precio_original;
    private Double descuento;
    private Double precio_final;

}
