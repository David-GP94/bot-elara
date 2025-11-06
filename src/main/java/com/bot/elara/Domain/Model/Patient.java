package com.bot.elara.Domain.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table (name = "patients")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Patient {
    @Id
    @Column(name = "whatsapp_id", unique = true, nullable = false)
    private String whatsappId;

    private String name;
    private Integer age;
    private String skinConcern;
    private String photoUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step")
    private OnboardingStep currentStep;
}
