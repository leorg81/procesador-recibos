package org.vaadin.example.state;

import com.vaadin.flow.spring.annotation.UIScope;
import org.springframework.stereotype.Component;
import org.vaadin.example.data.Localidad;
import org.vaadin.example.data.Sector;

import java.io.Serializable;
import java.util.List;

@UIScope
@Component
public class PantallaTurnoState implements Serializable {

    private Localidad localidad;
    private List<Sector> sectoresSeleccionados;

    public Localidad getLocalidad() {
        return localidad;
    }

    public void setLocalidad(Localidad localidad) {
        this.localidad = localidad;
    }

    public List<Sector> getSectoresSeleccionados() {
        return sectoresSeleccionados;
    }

    public void setSectoresSeleccionados(List<Sector> sectoresSeleccionados) {
        this.sectoresSeleccionados = sectoresSeleccionados;
    }

    public void clear() {
        this.localidad = null;
        this.sectoresSeleccionados = null;
    }
}