package org.vaadin.example.views.recibo;

public class ProcesoReciboDTO {
    private String localidadCodigo;
    private String tipo;
    private String mesAnio;

    // Para mostrar en la UI
    private String localidadNombre;

    public ProcesoReciboDTO(String localidadCodigo, String tipo, String mesAnio) {
        this.localidadCodigo = localidadCodigo;
        this.tipo = tipo;
        this.mesAnio = mesAnio;
    }

    // Constructor con nombre
    public ProcesoReciboDTO(String localidadCodigo, String localidadNombre,
                            String tipo, String mesAnio) {
        this.localidadCodigo = localidadCodigo;
        this.localidadNombre = localidadNombre;
        this.tipo = tipo;
        this.mesAnio = mesAnio;
    }

    // Getters
    public String getLocalidadCodigo() {
        return localidadCodigo;
    }

    public String getLocalidad() {
        return localidadNombre != null ? localidadNombre : localidadCodigo;
    }

    public String getTipo() {
        return tipo;
    }

    public String getMesAnio() {
        return mesAnio;
    }

    @Override
    public String toString() {
        return tipo + " - " + mesAnio + " - " + getLocalidad();
    }
}