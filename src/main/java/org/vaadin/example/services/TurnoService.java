package org.vaadin.example.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.vaadin.example.data.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TurnoService {

    private final TurnoRepository turnoRepository;


    public TurnoService(TurnoRepository turnoRepository) {
        this.turnoRepository = turnoRepository;
    }

    public List<Turno> findTurnosByEstado(String estado) {
        return turnoRepository.findByEstado(estado);
    }

    public Turno save(Turno turno) {
        return turnoRepository.save(turno);
    }

    public Turno update(Turno turno, User actuadoPor) {
        turno.setActuadoPor(actuadoPor);
        return turnoRepository.save(turno);
    }

    public void delete(Turno turno) {
        turnoRepository.delete(turno);
    }


    public List<Turno> findBySectorAndEstados(Sector sector, EstadoTurno... estados) {
        return turnoRepository.findBySectorAndEstadoIn(sector, Arrays.asList(estados));
    }


    public String generarCodigoTurno(Localidad localidad, Sector sector) {
        // Obtener la fecha de hoy
        LocalDate hoy = LocalDate.now();

        // Consultar el número de turnos generados hoy
        long contador = turnoRepository.countByLocalidadAndSectorAndFechaHoraBetween(
                localidad,
                sector,
                hoy.atStartOfDay(),
                hoy.plusDays(1).atStartOfDay()
        );

        // Incrementar el contador y generar el código
        contador++;
        return String.format("%s-%04d",

                sector.getNombre().substring(0, 4).toUpperCase(),
                contador
        );
    }

    public Turno crearTurno(Localidad localidad, Sector sector) {
        String codigoTurno = generarCodigoTurno(localidad, sector);

        Turno turno = new Turno();
        turno.setLocalidad(localidad);
        turno.setSector(sector);
        turno.setCodigoTurno(codigoTurno);
        turno.setFechaHora(LocalDateTime.now());
        turno.setEstado(EstadoTurno.EN_ESPERA);

        return turnoRepository.save(turno);
    }

    public Optional<Turno> obtenerSiguienteTurnoEnEspera(Sector sector) {
        // Obtener el inicio y fin del día actual
        LocalDateTime inicioDelDia = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime finDelDia = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // Filtrar los turnos que están en espera y cuyo timestamp está dentro del día actual
        List<Turno> turnos = turnoRepository.findBySectorAndEstadoAndFechaHoraBetweenOrderByFechaHoraAsc(
                sector,
                EstadoTurno.EN_ESPERA,
                inicioDelDia,
                finDelDia
        );

        return turnos.isEmpty() ? Optional.empty() : Optional.of(turnos.get(0));
    }

    public List<Turno> findTurnosDelDiaPorSectorYEstados(Sector sector, EstadoTurno... estados) {
        LocalDateTime inicioDelDia = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime finDelDia = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        return turnoRepository.findTurnosDelDiaPorSectorYEstados(sector,  Arrays.asList(estados), inicioDelDia, finDelDia);
    }

    public List<Turno> findByLocalidadAndEstados(Localidad localidad, EstadoTurno... estados) {
        // Confirma que esta consulta filtra correctamente los estados
        return turnoRepository.findByLocalidadAndEstadoIn(localidad, Arrays.asList(estados));
    }

    public List<Turno> findTurnosDelDiaByLocalidadAndEstados(Localidad localidad, EstadoTurno... estados) {
        LocalDateTime inicioDelDia = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime finDelDia = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        return turnoRepository.findTurnosDelDiaByLocalidadAndEstados(localidad, Arrays.asList(estados), inicioDelDia, finDelDia);
    }


    public void actualizarTurno(Turno turno) {
        turnoRepository.save(turno);
    }
    public List<Turno> findTurnosDelDiaByLocalidadSectoresAndEstados(Localidad localidad,
                                                                     List<Sector> sectores,
                                                                     EstadoTurno... estados) {
        return turnoRepository.findTurnosDelDiaByLocalidadAndSectoresAndEstadosOrderByFechaHoraAsc(localidad, sectores, Arrays.asList(estados));
    }

    public List<Turno> findTurnosByLocalidadSectorAndFecha(Localidad localidad, Sector sector, LocalDate fechaInicio, LocalDate fechaFin) {
        return turnoRepository.findAll().stream()
                .filter(t -> t.getLocalidad().equals(localidad))
                .filter(t -> t.getSector().equals(sector))
                .filter(t -> !t.getFechaHora().toLocalDate().isBefore(fechaInicio) &&
                        !t.getFechaHora().toLocalDate().isAfter(fechaFin))
                .collect(Collectors.toList());
    }

    public List<Turno> findTurnosByLocalidadAndSectores(Localidad localidad, List<Sector> sectores) {

        return turnoRepository.findByLocalidadAndSectores(localidad,sectores);
    }

    public List<Turno> findByLocalidadSectorAndFecha(Localidad localidad, Sector sector, LocalDateTime fechaInicio, LocalDateTime fechaFin) {
        // Validaciones
        if (localidad == null || sector == null || fechaInicio == null || fechaFin == null) {
            throw new IllegalArgumentException("Los parámetros no pueden ser nulos.");
        }

        if (fechaInicio.isAfter(fechaFin)) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la fecha de fin.");
        }

        // Llamada al repositorio
        return turnoRepository.findByLocalidadAndSectorAndFechaHoraBetween(localidad, sector, fechaInicio, fechaFin);
    }
}
