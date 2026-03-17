package org.vaadin.example.data;

import java.time.LocalDateTime;

public class InfoReciboDetectado {
    private String tipo;
    private String mes;
    private String anio;
    private String mesAnio;
    private String ci;
    private String fechaPago;
    private String nombres; // NUEVO
    private String apellidos; // NUEVO
    private double confianza;
    private String textoExtraido;
    private String nombreSugerido;
    private LocalDateTime detectadoEn;
    private String liquidacionLinea; // Nueva: línea completa de liquidación
    private String localidadCodigo;

    // Constructor
    public InfoReciboDetectado() {
        this.detectadoEn = LocalDateTime.now();
    }

    // Constructor completo
    public InfoReciboDetectado(String tipo, String mes, String anio, String ci,
                               double confianza, String nombres, String apellidos) {
        this.tipo = tipo;
        this.mes = mes;
        this.anio = anio;
        this.mesAnio = mes + anio;
        this.ci = ci;
        this.confianza = confianza;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.detectadoEn = LocalDateTime.now();

    }

    // Getters y Setters
    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getMes() {
        return mes;
    }

    public void setMes(String mes) {
        this.mes = mes;
    }

    public String getAnio() {
        return anio;
    }

    public void setAnio(String anio) {
        this.anio = anio;
    }

    public String getMesAnio() {
        return mesAnio;
    }

    public void setMesAnio(String mesAnio) {
        this.mesAnio = mesAnio;
    }

    public String getCi() {
        return ci;
    }

    public void setCi(String ci) {
        this.ci = ci;
    }

    public String getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(String fechaPago) {
        this.fechaPago = fechaPago;
    }

    public String getNombres() { // NUEVO
        return nombres;
    }

    public void setNombres(String nombres) { // NUEVO
        this.nombres = nombres;
    }

    public String getApellidos() { // NUEVO
        return apellidos;
    }

    public void setApellidos(String apellidos) { // NUEVO
        this.apellidos = apellidos;
    }

    public double getConfianza() {
        return confianza;
    }

    public void setConfianza(double confianza) {
        this.confianza = confianza;
    }

    public String getTextoExtraido() {
        return textoExtraido;
    }

    public void setTextoExtraido(String textoExtraido) {
        this.textoExtraido = textoExtraido;
    }

    public String getNombreSugerido() {
        return nombreSugerido;
    }

    public void setNombreSugerido(String nombreSugerido) {
        this.nombreSugerido = nombreSugerido;
    }

    public LocalDateTime getDetectadoEn() {
        return detectadoEn;
    }

    public void setDetectadoEn(LocalDateTime detectadoEn) {
        this.detectadoEn = detectadoEn;
    }

    // Métodos de utilidad
    public boolean isConfiable() {
        return confianza >= 0.7;
    }



    public String getLiquidacionLinea() { return liquidacionLinea; }
    public void setLiquidacionLinea(String liquidacionLinea) { this.liquidacionLinea = liquidacionLinea; }

    public String getLocalidadCodigo() { return localidadCodigo; }
    public void setLocalidadCodigo(String localidadCodigo) { this.localidadCodigo = localidadCodigo; }

    public String getResumen() {
        return String.format("CI: %s, Tipo: %s, Mes/Año: %s/%s, Localidad: %s",
                ci, tipo, mes, anio, localidadCodigo != null ? localidadCodigo : "N/A");
    }

    @Override
    public String toString() {
        return "InfoReciboDetectado{" +
                "tipo='" + tipo + '\'' +
                ", mes='" + mes + '\'' +
                ", anio='" + anio + '\'' +
                ", ci='" + ci + '\'' +
                ", nombres='" + nombres + '\'' +
                ", apellidos='" + apellidos + '\'' +
                ", confianza=" + confianza +
                '}';
    }
}