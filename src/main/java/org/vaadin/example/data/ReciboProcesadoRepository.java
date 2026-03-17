package org.vaadin.example.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReciboProcesadoRepository extends JpaRepository<ReciboProcesado, Long> {



    List<ReciboProcesado> findByTipoAndMesAnioAndCi(String tipo, String mesAnio, String ci);

    List<ReciboProcesado> findByTipoIgnoreCaseAndMesAnioAndCiIgnoreCase(
            String tipo,
            String mesAnio,
            String ci
    );

    @Query("SELECT r.tipo, COUNT(r) FROM ReciboProcesado r " +
            "WHERE r.procesadoEn >= :fechaDesde " +
            "GROUP BY r.tipo")
    List<Object[]> countByTipoDesde(@Param("fechaDesde") LocalDateTime fechaDesde);

    @Query("SELECT r.localidadCodigo, r.tipo, COUNT(r) FROM ReciboProcesado r " +
            "WHERE r.procesadoEn >= :fechaDesde " +
            "GROUP BY r.localidadCodigo, r.tipo")
    List<Object[]> countByLocalidadYTipoDesde(@Param("fechaDesde") LocalDateTime fechaDesde);

    @Query("SELECT FUNCTION('DATE_FORMAT', r.procesadoEn, '%Y-%m'), " +
            "r.localidadCodigo, COUNT(r) " +
            "FROM ReciboProcesado r " +
            "WHERE r.procesadoEn >= :fechaDesde " +
            "GROUP BY FUNCTION('DATE_FORMAT', r.procesadoEn, '%Y-%m'), r.localidadCodigo " +
            "ORDER BY FUNCTION('DATE_FORMAT', r.procesadoEn, '%Y-%m')")
    List<Object[]> evolucionMensualDesde(@Param("fechaDesde") LocalDateTime fechaDesde);


    //----

    List<ReciboProcesado> findByCi(String ci);
    List<ReciboProcesado> findByCiAndMesAnio(String ci, String mesAnio);
    List<ReciboProcesado> findByCiAndTipo(String ci, String tipo);

    // Métodos adicionales necesarios
    List<ReciboProcesado> findByMesAnio(String mesAnio);
    List<ReciboProcesado> findByTipo(String tipo);
    List<ReciboProcesado> findByLocalidadCodigo(String localidadCodigo);

    List<ReciboProcesado> findByCiAndTipoAndMesAnio(String ci, String tipo, String mesAnio);

    List<ReciboProcesado> findByCiAndTipoAndLocalidadCodigoAndMesAnio(
            String ci, String tipo, String localidadCodigo, String mesAnio);

    List<ReciboProcesado> findByCiAndProcesadoEnAfter(String ci, LocalDateTime fecha);

    Optional<ReciboProcesado> findFirstByOrderByProcesadoEnDesc();

    long countByProcesadoEnBetween(LocalDateTime inicio, LocalDateTime fin);

    // Consultas para estadísticas
    @Query("SELECT r.tipo, COUNT(r) FROM ReciboProcesado r GROUP BY r.tipo ORDER BY COUNT(r) DESC")
    List<Object[]> countByTipoGrouped();

    @Query("SELECT r.localidadCodigo, COUNT(r) FROM ReciboProcesado r " +
            "WHERE r.localidadCodigo IS NOT NULL " +
            "GROUP BY r.localidadCodigo ORDER BY COUNT(r) DESC")
    List<Object[]> countByLocalidadGrouped();

    @Query("SELECT r.mesAnio, COUNT(r) FROM ReciboProcesado r " +
            "WHERE r.procesadoEn >= :fechaDesde " +
            "GROUP BY r.mesAnio ORDER BY r.mesAnio DESC")
    List<Object[]> countByMesGrouped(@Param("fechaDesde") LocalDateTime fechaDesde);

    // Para paginación
    @Query("SELECT r FROM ReciboProcesado r ORDER BY r.procesadoEn DESC")
    List<ReciboProcesado> findAllOrderedByFecha();

    boolean existsByLocalidadCodigoAndTipoAndMesAnio(
            String localidadCodigo, String tipo, String mesAnio);

    long countByLocalidadCodigoAndTipoAndMesAnio(String localidadCodigo, String tipo, String mesAnio);

    List<ReciboProcesado> findByLocalidadCodigoAndTipoAndMesAnio(String localidadCodigo, String tipo, String mesAnio);

    List<ReciboProcesado> findByTipoAndMesAnio(String tipo, String mesAnio);

    List<ReciboProcesado> findByLocalidadCodigoAndTipoAndMesAnioOrderByProcesadoEnDesc(
            String localidadCodigo, String tipo, String mesAnio);



}