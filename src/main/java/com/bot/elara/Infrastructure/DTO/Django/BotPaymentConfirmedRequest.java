package com.bot.elara.Infrastructure.DTO.Django;

import lombok.Data;

@Data
public class BotPaymentConfirmedRequest {

    private String public_id;
    private Long consulta_id;
    private String estado;
    private Boolean pago_completado;
    private String metodo_pago;

}
