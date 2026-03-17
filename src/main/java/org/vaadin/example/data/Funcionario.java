package org.vaadin.example.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "funcionario")
public class Funcionario extends AbstractEntity {


    private String ci;
    private String nombre;
    @Email
    private String correo;
    private String telefono;
    @ManyToOne
    @JoinColumn(name = "localidad_id")
    private Localidad localidad;

    @Column(name = "localidad_codigo")
    private String localidadCodigo;

    // Campos para integración con Telegram
    @Column(name = "telegram_chat_id")
    private String telegramChatId;

    @Column(name = "telegram_activado")
    private Boolean telegramActivado = false;

    @Column(name = "telegram_codigo_activacion")
    private String telegramCodigoActivacion;

    @Column(name = "telegram_codigo_expiracion")
    private LocalDateTime telegramCodigoExpiracion;

    @Column(name = "telegram_registrado_en")
    private LocalDateTime telegramRegistradoEn;

    private String whatsappLid;
    private boolean whatsappActivado = false;
    private String whatsappCodigoActivacion;
    private LocalDateTime whatsappCodigoExpiracion;
    private LocalDateTime whatsappRegistradoEn;


    public String getTelegramChatId() {
        return telegramChatId;
    }

    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public boolean getTelegramActivado() {
        return telegramActivado != null ? telegramActivado : false;
    }

    public void setTelegramActivado(boolean telegramActivado) {
        this.telegramActivado = telegramActivado;
    }

    public String getTelegramCodigoActivacion() {
        return telegramCodigoActivacion;
    }

    public void setTelegramCodigoActivacion(String telegramCodigoActivacion) {
        this.telegramCodigoActivacion = telegramCodigoActivacion;
    }

    public LocalDateTime getTelegramCodigoExpiracion() {
        return telegramCodigoExpiracion;
    }

    public void setTelegramCodigoExpiracion(LocalDateTime telegramCodigoExpiracion) {
        this.telegramCodigoExpiracion = telegramCodigoExpiracion;
    }

    public LocalDateTime getTelegramRegistradoEn() {
        return telegramRegistradoEn;
    }

    public void setTelegramRegistradoEn(LocalDateTime telegramRegistradoEn) {
        this.telegramRegistradoEn = telegramRegistradoEn;
    }

    // Método auxiliar para verificar si el código está expirado
    @Transient
    @JsonIgnore
    public boolean isCodigoActivacionExpirado() {
        if (telegramCodigoExpiracion == null) return true;
        return LocalDateTime.now().isAfter(telegramCodigoExpiracion);
    }

    // Método auxiliar para verificar si Telegram está activo
    @Transient
    @JsonIgnore
    public boolean isTelegramActivo() {
        return Boolean.TRUE.equals(telegramActivado) && telegramChatId != null;
    }


    public String getWhatsappLid() { return whatsappLid; }
    public void setWhatsappLid(String whatsappLid) { this.whatsappLid = whatsappLid; }

    public boolean getWhatsappActivado() { return whatsappActivado; }

    public void setWhatsappActivado(Boolean whatsappActivado) {
        this.whatsappActivado = whatsappActivado != null ? whatsappActivado : false;
    }

    public String getWhatsappCodigoActivacion() { return whatsappCodigoActivacion; }
    public void setWhatsappCodigoActivacion(String whatsappCodigoActivacion) { this.whatsappCodigoActivacion = whatsappCodigoActivacion; }

    public LocalDateTime getWhatsappCodigoExpiracion() { return whatsappCodigoExpiracion; }
    public void setWhatsappCodigoExpiracion(LocalDateTime whatsappCodigoExpiracion) { this.whatsappCodigoExpiracion = whatsappCodigoExpiracion; }

    public LocalDateTime getWhatsappRegistradoEn() { return whatsappRegistradoEn; }
    public void setWhatsappRegistradoEn(LocalDateTime whatsappRegistradoEn) { this.whatsappRegistradoEn = whatsappRegistradoEn; }


    public String getCi() {
        return ci;
    }

    public void setCi(String ci) {
        this.ci = ci;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public Localidad getLocalidad() {
        return localidad;
    }

    public void setLocalidad(Localidad localidad) {
        this.localidad = localidad;
    }

    public String getLocalidadCodigo() {
        return localidadCodigo;
    }

    public void setLocalidadCodigo(String localidadCodigo) {
        this.localidadCodigo = localidadCodigo;
    }
}
