package com.bot.elara.Infrastructure.DTO.Django;

import lombok.Data;

@Data
public class BotUserResponse {

    private Boolean success;
    private Long user_id;
    private Boolean nuevo;

}
