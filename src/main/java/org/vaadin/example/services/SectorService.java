package org.vaadin.example.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vaadin.example.data.Localidad;
import org.vaadin.example.data.Sector;
import org.vaadin.example.data.SectorRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class SectorService {

    private final SectorRepository sectorRepository;

    @Autowired
    public SectorService(SectorRepository sectorRepository) {
        this.sectorRepository = sectorRepository;
    }

    public List<Sector> findAll() {
        return sectorRepository.findAll();
    }

    public Sector save(Sector sector) {
        return sectorRepository.save(sector);
    }

    public void delete(Sector sector) {
        sectorRepository.delete(sector);
    }
    public List<Sector> findByLocalidadId(Long localidadId) {
        return sectorRepository.findByLocalidadId(localidadId);

    }

    public List<Sector> findByLocalidad(Localidad localidad) {
        // Implementa la lógica para obtener los sectores según la localidad
        return sectorRepository.findByLocalidad(localidad);
    }
}
