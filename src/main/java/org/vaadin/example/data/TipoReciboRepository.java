package org.vaadin.example.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TipoReciboRepository extends JpaRepository<TipoRecibo, Long> {
    Optional<TipoRecibo> findByCodigo(String codigo);
    List<TipoRecibo> findAllByOrderByNombreAsc();
    boolean existsByCodigo(String codigo);
    @Query("SELECT tr FROM TipoRecibo tr LEFT JOIN FETCH tr.recibos")
    List<TipoRecibo> findAllWithRecibos();

}