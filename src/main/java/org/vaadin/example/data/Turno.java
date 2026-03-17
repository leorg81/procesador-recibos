package org.vaadin.example.data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Turno extends AbstractEntity {

    @ManyToOne
    private Sector sector;
    @ManyToOne
    @JoinColumn(name = "localidad_id", nullable = false)
    private Localidad localidad;
    private String codigoTurno; // Código único del turno
    private LocalDateTime fechaHora;
    private String descripcion;
    @Enumerated(EnumType.STRING)
    private EstadoTurno estado;
    @ManyToOne
    private User actuadoPor; // Relación con el User que actuó sobre el turno

    // Getters y setters

    public String getCodigoTurno() {
        return codigoTurno;
    }

    public void setCodigoTurno(String codigoTurno) {
        this.codigoTurno = codigoTurno;
    }

    public Sector getSector() {
        return sector;
    }

    public void setSector(Sector sector) {
        this.sector = sector;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public void setEstado(EstadoTurno estado) {
        this.estado = estado;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public User getActuadoPor() {
        return actuadoPor;
    }

    public void setActuadoPor(User actuadoPor) {
        this.actuadoPor = actuadoPor;
    }

    public Localidad getLocalidad() {
        return localidad;
    }

    public void setLocalidad(Localidad localidad) {
        this.localidad = localidad;
    }

}
