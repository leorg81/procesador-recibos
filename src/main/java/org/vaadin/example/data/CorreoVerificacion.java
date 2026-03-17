package org.vaadin.example.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// CorreoVerificacion.java - SIN restricción de dominio
@Entity
@Table(name = "correo_verificaciones")
public class CorreoVerificacion extends AbstractEntity {


    @Column(nullable = false)
    private String correo; // Cualquier correo válido

    @Column(nullable = false)
    private String codigo; // 6 dígitos

    @Column(nullable = false)
    private String chatId; // Telegram chat ID

    @Column(nullable = false)
    private LocalDateTime creadoEn;

    @Column
    private LocalDateTime usadoEn;

    @Column(nullable = false)
    private Boolean usado = false;

    @Column(nullable = false)
    private Integer intentos = 0;

    @Column
    private String ipSolicitud; // Para auditoría

    // 15 minutos de validez (más que 10 para correos personales)
    public boolean esValido() {
        return !usado &&
                intentos < 3 &&
                creadoEn.plusMinutes(15).isAfter(LocalDateTime.now());
    }
}