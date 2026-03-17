package org.vaadin.example.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.vaadin.example.data.Localidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocalidadRepository extends JpaRepository<Localidad, Long> {

    Optional<Localidad> findByCodigo(String codigo);

    List<Localidad> findByNombreContainingIgnoreCase(String nombre);

    List<Localidad> findAllByOrderByNombreAsc();

    boolean existsByCodigo(String codigo);

    // Consultas avanzadas
    @Query("SELECT l FROM Localidad l WHERE SIZE(l.funcionarios) > 0 ORDER BY l.nombre")
    List<Localidad> findLocalidadesConFuncionarios();

    @Query("SELECT l FROM Localidad l WHERE SIZE(l.recibos) > 0 ORDER BY l.nombre")
    List<Localidad> findLocalidadesConRecibos();

    @Query("SELECT l.codigo, l.nombre, COUNT(f) as totalFuncionarios, " +
            "COUNT(r) as totalRecibos " +
            "FROM Localidad l " +
            "LEFT JOIN l.funcionarios f " +
            "LEFT JOIN l.recibos r " +
            "GROUP BY l.id, l.codigo, l.nombre " +
            "ORDER BY l.nombre")
    List<Object[]> obtenerEstadisticasLocalidades();

    @Query("SELECT l FROM Localidad l " +
            "LEFT JOIN l.recibos r " +
            "GROUP BY l.id " +
            "ORDER BY COUNT(r) DESC, l.nombre ASC " +
            "LIMIT :limite")
    List<Localidad> findLocalidadesMasActivas(@Param("limite") int limite);

    @Query("SELECT l.codigo, COUNT(f) FROM Localidad l " +
            "LEFT JOIN l.funcionarios f " +
            "GROUP BY l.id, l.codigo " +
            "ORDER BY COUNT(f) DESC")
    List<Object[]> countFuncionariosPorLocalidad();

    @Query("SELECT l.codigo, COUNT(r) FROM Localidad l " +
            "LEFT JOIN l.recibos r " +
            "GROUP BY l.id, l.codigo " +
            "ORDER BY COUNT(r) DESC")
    List<Object[]> countRecibosPorLocalidad();

    // Para búsqueda con filtros combinados
    @Query("SELECT l FROM Localidad l WHERE " +
            "(:codigo IS NULL OR l.codigo = :codigo) AND " +
            "(:nombre IS NULL OR LOWER(l.nombre) LIKE LOWER(CONCAT('%', :nombre, '%')))")
    List<Localidad> buscarConFiltros(@Param("codigo") String codigo,
                                     @Param("nombre") String nombre);

    @Query("SELECT DISTINCT l FROM Localidad l LEFT JOIN FETCH l.funcionarios ORDER BY l.nombre ASC")
    List<Localidad> findAllWithFuncionarios();

    // Otro método si solo necesitas contar
    @Query("SELECT l, COUNT(f) as funcionariosCount FROM Localidad l LEFT JOIN l.funcionarios f GROUP BY l ORDER BY l.nombre ASC")
    List<Object[]> findAllWithFuncionariosCount();

    @Query("SELECT DISTINCT l FROM Localidad l " +
            "LEFT JOIN FETCH l.funcionarios " +
            "LEFT JOIN FETCH l.recibos " +
            "ORDER BY l.nombre ASC")
    List<Localidad> findAllWithFuncionariosAndRecibos();

}

