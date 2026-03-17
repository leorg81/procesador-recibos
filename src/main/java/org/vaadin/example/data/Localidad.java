package org.vaadin.example.data;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "localidades")
public class Localidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String codigo; // Ej: "RN", "PA", "CL"

    @Column(nullable = false)
    private String nombre; // Ej: "Río Negro", "Paysandú", "Cerro Largo"

    @OneToMany(mappedBy = "localidad", cascade = CascadeType.ALL)
    private List<Funcionario> funcionarios = new ArrayList<>();

    @OneToMany(mappedBy = "localidad")
    private List<ReciboProcesado> recibos = new ArrayList<>();

    // Constructores, getters y setters
    public Localidad() {}

    public Localidad(String codigo, String nombre) {
        this.codigo = codigo;
        this.nombre = nombre;
    }

    // Método toString útil
    @Override
    public String toString() {
        return nombre + " (" + codigo + ")";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public List<Funcionario> getFuncionarios() {
        return funcionarios;
    }

    public void setFuncionarios(List<Funcionario> funcionarios) {
        this.funcionarios = funcionarios;
    }

    public List<ReciboProcesado> getRecibos() {
        return recibos;
    }

    public void setRecibos(List<ReciboProcesado> recibos) {
        this.recibos = recibos;
    }
}