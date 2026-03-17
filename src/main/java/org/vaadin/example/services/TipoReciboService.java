package org.vaadin.example.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vaadin.example.data.TipoRecibo;
import org.vaadin.example.data.TipoReciboRepository;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TipoReciboService {

    @Autowired
    private TipoReciboRepository repository;

    public List<TipoRecibo> listarTodos() {
        return repository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public TipoRecibo guardar(TipoRecibo tipoRecibo) {
        // Validar que el código no exista (excepto en edición)
        if (tipoRecibo.getId() == null) {
            if (repository.existsByCodigo(tipoRecibo.getCodigo())) {
                throw new IllegalArgumentException("Ya existe un tipo de recibo con el código: " + tipoRecibo.getCodigo());
            }
        }
        return repository.save(tipoRecibo);
    }
    @Transactional
    public void eliminar(Long id) {
        TipoRecibo tipoRecibo = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de recibo no encontrado con ID: " + id));

        // Verificar si tiene recibos asociados antes de eliminar
        if (tipoRecibo.getRecibos() != null && !tipoRecibo.getRecibos().isEmpty()) {
            throw new IllegalStateException("No se puede eliminar el tipo de recibo porque tiene " +
                    tipoRecibo.getRecibos().size() + " recibos asociados.");
        }

        repository.delete(tipoRecibo);
    }
    public Optional<TipoRecibo> buscarPorCodigo(String codigo) {
        return repository.findByCodigo(codigo);
    }

    @Transactional(readOnly = true)
    public List<TipoRecibo> listarTodosConRecuentos() {
        // Este método carga los recibos asociados para evitar LazyInitializationException
        return repository.findAllWithRecibos();
    }

    @Transactional(readOnly = true)
    public TipoRecibo buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tipo de recibo no encontrado con ID: " + id));
    }

}