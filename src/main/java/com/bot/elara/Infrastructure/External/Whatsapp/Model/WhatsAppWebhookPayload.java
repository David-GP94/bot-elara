package com.bot.elara.Infrastructure.External.Whatsapp.Model;

import lombok.Data;

import java.util.List;

@Data
public class WhatsAppWebhookPayload {
    private List<Entry> entry;
}
