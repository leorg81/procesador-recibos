package org.vaadin.example.services;


import org.springframework.stereotype.Service;
import org.vaadin.example.data.Noticia;
import org.vaadin.example.data.NoticiaRepository;
import org.vaadin.example.data.UserRepository;

import java.util.List;

@Service
public class NoticiaService {
    private final NoticiaRepository noticiaRepository;

    public NoticiaService(NoticiaRepository noticiaRepository) {


        this.noticiaRepository = noticiaRepository;
    }


    public void delete(Long id) {
        noticiaRepository.delete(noticiaRepository.getById(id));
    }

    public void update(Noticia noticia) {
        noticiaRepository.save(noticia);
    }

    public List<Noticia> findAll() {
        return noticiaRepository.findAll();
    }

    public Noticia findById(Long id) {

        return noticiaRepository.findById(id).get();
    }


    public List<Noticia> findNoticiasActivas() {
        return noticiaRepository.findByActivoTrue();
    }
}
