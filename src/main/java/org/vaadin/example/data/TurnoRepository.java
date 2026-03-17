package org.vaadin.example.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vaadin.example.data.Turno;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    List<Turno> findByEstado(String estado); // Encontrar turnos por estado

    List<Turno> findByLocalidad(Localidad localidad);


    List<Turno> findBySectorAndEstado(Sector sector, EstadoTurno estado);
    long countBySector(String sector);


    long countByLocalidadAndSectorAndFechaHoraBetween(Localidad localidad, Sector sector, LocalDateTime inicio, LocalDateTime fin);

    Optional<Turno> findFirstBySectorAndEstadoOrderByFechaHoraAsc(Sector sector, EstadoTurno estado);

    List<Turno> findBySectorAndEstadoIn(Sector sector, List<EstadoTurno> list);

    List<Turno> findBySectorAndEstadoOrderByFechaHoraAsc(Sector sector, EstadoTurno enEspera);

    @Query("SELECT t FROM Turno t WHERE t.sector = :sector AND t.estado IN :estados ORDER BY t.fechaHora ASC")
    List<Turno> findBySectorAndEstadoInOrderByFechaHoraAsc(
            @Param("sector") Sector sector,
            @Param("estados") List<EstadoTurno> estados
    );


    Optional<Turno> findByCodigoTurno(String codigo);

    List<Turno> findByLocalidadAndEstadoIn(Localidad localidad, List<EstadoTurno> list);

    @Query("SELECT t FROM Turno t WHERE t.localidad = :localidad AND t.estado IN :estados AND t.fechaHora BETWEEN :inicioDelDia AND :finDelDia")
    List<Turno> findTurnosDelDiaByLocalidadAndEstados(
            @Param("localidad") Localidad localidad,
            @Param("estados") List<EstadoTurno> estados,
            @Param("inicioDelDia") LocalDateTime inicioDelDia,
            @Param("finDelDia") LocalDateTime finDelDia
    );



    @Query("SELECT t FROM Turno t WHERE t.sector = :sector AND t.estado IN :estados AND t.fechaHora BETWEEN :inicioDelDia AND :finDelDia")
    List<Turno> findTurnosDelDiaPorSectorYEstados(
            @Param("sector") Sector sector,
            @Param("estados") List<EstadoTurno> estados,
            @Param("inicioDelDia") LocalDateTime inicioDelDia,
            @Param("finDelDia") LocalDateTime finDelDia
    );

    List<Turno> findBySectorAndEstadoAndFechaHoraBetweenOrderByFechaHoraAsc(
            Sector sector,
            EstadoTurno estado,
            LocalDateTime inicioDelDia,
            LocalDateTime finDelDia
    );

    @Query("SELECT t FROM Turno t WHERE t.localidad = :localidad AND t.sector IN :sectores AND t.estado IN :estados AND t.fechaHora = CURRENT_DATE ORDER BY t.fechaHora ASC ")
    List<Turno> findTurnosDelDiaByLocalidadAndSectoresAndEstadosOrderByFechaHoraAsc(
            @Param("localidad") Localidad localidad,
            @Param("sectores") List<Sector> sectores,
            @Param("estados") List<EstadoTurno> estados);

    @Query("SELECT t FROM Turno t WHERE t.localidad = :localidad AND t.sector IN :sectores")
    List<Turno> findByLocalidadAndSectores(Localidad localidad, List<Sector> sectores);


    List<Turno> findByLocalidadAndSectorAndFechaHoraBetween(Localidad localidad, Sector sector, LocalDateTime fechaInicio, LocalDateTime fechaFin);
}
