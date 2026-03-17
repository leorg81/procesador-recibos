package org.vaadin.example.data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@jakarta.persistence.Table(name = "recibos_procesados") // Agregar si no existe
public class ReciboProcesado extends AbstractEntity {

    @Column(nullable = false)
    private String ci;

    @Column(nullable = false)
    private String tipo; // MENSUAL, AGUINALDO, COMPLEMENTARIA, etc.

    @Column(nullable = false)
    private String nombreArchivo;

    @Column(nullable = false)
    private String mesAnio; // ej: 012025, 122024

    private String mes; // NUEVO: almacenar mes separado
    private String anio; // NUEVO: almacenar año separado

    private String fechaPago; // NUEVO: fecha de pago
    private String nombres; // NUEVO: nombres del funcionario
    private String apellidos; // NUEVO: apellidos del funcionario
    private Double confianza; // NUEVO: nivel de confianza de detección

    @Column(nullable = false)
    private LocalDateTime procesadoEn;

    @Column(nullable = false)
    private String rutaArchivo;

    @ManyToOne
    @JoinColumn(name = "localidad_id")
    private Localidad localidad;

    @Column(name = "localidad_codigo")
    private String localidadCodigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_recibo_id")
    private TipoRecibo tipoRecibo;


    // Constructor vacío
    public ReciboProcesado() {
        this.procesadoEn = LocalDateTime.now();
    }

    // Getters y Setters
    public String getCi() {
        return ci;
    }

    public void setCi(String ci) {
        this.ci = ci;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getMesAnio() {
        return mesAnio;
    }

    public void setMesAnio(String mesAnio) {
        this.mesAnio = mesAnio;
    }

    public String getMes() { // NUEVO
        return mes;
    }

    public void setMes(String mes) { // NUEVO
        this.mes = mes;
    }

    public String getAnio() { // NUEVO
        return anio;
    }

    public void setAnio(String anio) { // NUEVO
        this.anio = anio;
    }

    public String getFechaPago() { // NUEVO
        return fechaPago;
    }

    public void setFechaPago(String fechaPago) { // NUEVO
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

    public Double getConfianza() { // NUEVO
        return confianza;
    }

    public void setConfianza(Double confianza) { // NUEVO
        this.confianza = confianza;
    }

    public LocalDateTime getProcesadoEn() {
        return procesadoEn;
    }

    public void setProcesadoEn(LocalDateTime procesadoEn) {
        this.procesadoEn = procesadoEn;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
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

    public TipoRecibo getTipoRecibo() { return tipoRecibo; }
    public void setTipoRecibo(TipoRecibo tipoRecibo) {
        this.tipoRecibo = tipoRecibo;
        if (tipoRecibo != null) {
            this.tipo = tipoRecibo.getCodigo();
        }
    }



    // Método de utilidad
    public String getResumen() {
        return String.format("%s - %s/%s - CI: %s",
                tipo, mes, anio, ci);
    }
}