package org.vaadin.example.data;

public enum EstadoTurno {
    EN_ESPERA, LLAMANDO, ATENDIDO, RESUELTO, CERRADO;

    @Override
    public String toString() {
        // Formateo de los estados para ser más amigables visualmente
        return name().replace("_", " ").toLowerCase();
    }
}
