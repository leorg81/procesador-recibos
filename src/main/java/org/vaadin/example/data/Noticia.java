package org.vaadin.example.data;

import javax.persistence.Entity;
import javax.persistence.Lob;

@Entity
public class Noticia extends AbstractEntity{

    private String Descripcion;

    @Lob
    private byte[] foto;

    private boolean activo;

    public String getDescripcion() {
        return Descripcion;
    }

    public void setDescripcion(String descripcion) {
        Descripcion = descripcion;
    }

    public byte[] getFoto() {
        return foto;
    }

    public void setFoto(byte[] foto) {
        this.foto = foto;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
