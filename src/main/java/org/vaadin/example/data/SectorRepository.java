package org.vaadin.example.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vaadin.example.data.Sector;

import java.util.List;

public interface SectorRepository extends JpaRepository<Sector, Long> {
    List<Sector> findByLocalidadId(Long localidadId);

    List<Sector> findByLocalidad(Localidad localidad);
    // Aquí puedes agregar métodos adicionales si es necesario
}
