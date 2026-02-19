package com.bot.elara.Infrastructure.DTO.Django;

import lombok.Data;

@Data
public class BotCreateConsultaRequest {

    private Long user_id;

    private String motivo_consulta;
    private String para_quien;

    private String nombre_paciente;
    private String genero_nacimiento;
    private String fecha_nacimiento;
    private Integer peso;
    private Double altura;
    private Boolean fuma;

    private Boolean tiene_alergias;
    private String alergias_descripcion;

    private Boolean tiene_enfermedades;
    private String enfermedades_descripcion;

    private Boolean toma_medicamentos;
    private String medicamentos_descripcion;

    private String informacion_adicional;

    private String metodo_pago;
    private String codigo_descuento;

}
