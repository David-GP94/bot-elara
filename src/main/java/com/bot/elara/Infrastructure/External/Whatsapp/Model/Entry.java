package com.bot.elara.Infrastructure.External.Whatsapp.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class Entry {
    @JsonProperty("id")
    private String id;

    @JsonProperty("changes")
    private List<Change> changes;
}
