package org.vaadin.example.data;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tipo_recibo")
public class TipoRecibo extends AbstractEntity{


    @NotBlank
    @Column(unique = true, nullable = false)
    private String codigo;

    @NotBlank
    private String nombre;

    private String descripcion;

    @OneToMany(mappedBy = "tipoRecibo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReciboProcesado> recibos = new ArrayList<>();

    // Constructores
    public TipoRecibo() {}

    public TipoRecibo(String codigo, String nombre, String descripcion) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    // Getters y Setters


    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public List<ReciboProcesado> getRecibos() { return recibos; }
    public void setRecibos(List<ReciboProcesado> recibos) { this.recibos = recibos; }
}