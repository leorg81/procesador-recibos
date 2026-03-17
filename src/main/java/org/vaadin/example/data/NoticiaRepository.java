package org.vaadin.example.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticiaRepository extends JpaRepository<Noticia, Long>{

    List<Noticia> findByActivoTrue();
}
