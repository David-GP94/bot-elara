package com.bot.elara.Domain.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bot_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String whatsappId;

    @Enumerated(EnumType.STRING)
    private OnboardingStep currentStep;

    private String lastListContext;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    private Boolean pendingInactivityResponse;

    private Long consultaId;

    @Column(unique = true)
    private String consultaPublicId;

    private Boolean paymentConfirmed = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}


