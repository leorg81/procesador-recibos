package org.vaadin.example.services;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vaadin.example.data.Localidad;
import org.vaadin.example.data.LocalidadRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LocalidadService {

    private final LocalidadRepository localidadRepository;

    public LocalidadService(LocalidadRepository localidadRepository) {
        this.localidadRepository = localidadRepository;
    }

    // ==================== OPERACIONES CRUD ====================

    @Transactional
    public Localidad guardar(Localidad localidad) {
        // Validar que el código no exista
        if (localidad.getId() == null &&
                localidadRepository.existsByCodigo(localidad.getCodigo())) {
            throw new IllegalArgumentException(
                    "Ya existe una localidad con el código: " + localidad.getCodigo());
        }

        return localidadRepository.save(localidad);
    }

    @Transactional(readOnly = true)
    public List<Localidad> listarActivas() {
        return localidadRepository.findAll(); // Puedes agregar un campo "activo" si necesitas
    }

    @Transactional(readOnly = true)
    public Optional<Localidad> buscarPorId(Long id) {
        return localidadRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Localidad> buscarPorCodigo(String codigo) {
        return localidadRepository.findByCodigo(codigo);
    }

    @Transactional(readOnly = true)
    public List<Localidad> buscarPorNombre(String nombre) {
        return localidadRepository.findByNombreContainingIgnoreCase(nombre);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!localidadRepository.existsById(id)) {
            throw new IllegalArgumentException("Localidad no encontrada con ID: " + id);
        }

        // Verificar si tiene funcionarios asociados
        Localidad localidad = localidadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Localidad no encontrada"));

        if (!localidad.getFuncionarios().isEmpty()) {
            throw new IllegalStateException(
                    "No se puede eliminar la localidad porque tiene funcionarios asociados. " +
                            "Primero reasigne o elimine los funcionarios.");
        }

        localidadRepository.deleteById(id);
    }

    @Transactional
    public Localidad actualizar(Long id, Localidad localidadActualizada) {
        return localidadRepository.findById(id)
                .map(localidad -> {
                    // Verificar si el código cambió y si ya existe
                    if (!localidad.getCodigo().equals(localidadActualizada.getCodigo()) &&
                            localidadRepository.existsByCodigo(localidadActualizada.getCodigo())) {
                        throw new IllegalArgumentException(
                                "Ya existe una localidad con el código: " + localidadActualizada.getCodigo());
                    }

                    localidad.setCodigo(localidadActualizada.getCodigo());
                    localidad.setNombre(localidadActualizada.getNombre());

                    return localidadRepository.save(localidad);
                })
                .orElseThrow(() -> new IllegalArgumentException("Localidad no encontrada con ID: " + id));
    }

    // ==================== OPERACIONES ESPECÍFICAS ====================

    @Transactional(readOnly = true)
    public long contarTotal() {
        return localidadRepository.count();
    }

    @Transactional(readOnly = true)
    public long contarFuncionariosPorLocalidad(Long localidadId) {
        return localidadRepository.findById(localidadId)
                .map(localidad -> (long) localidad.getFuncionarios().size())
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public long contarRecibosPorLocalidad(Long localidadId) {
        return localidadRepository.findById(localidadId)
                .map(localidad -> (long) localidad.getRecibos().size())
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public boolean existePorCodigo(String codigo) {
        return localidadRepository.existsByCodigo(codigo);
    }

    @Transactional(readOnly = true)
    public List<Localidad> buscarLocalidadesConFuncionarios() {
        return localidadRepository.findLocalidadesConFuncionarios();
    }

    @Transactional(readOnly = true)
    public List<Localidad> buscarLocalidadesConRecibos() {
        return localidadRepository.findLocalidadesConRecibos();
    }

    @Transactional
    public Localidad crearLocalidadSiNoExiste(String codigo, String nombre) {
        return localidadRepository.findByCodigo(codigo)
                .orElseGet(() -> {
                    Localidad nuevaLocalidad = new Localidad(codigo, nombre);
                    return localidadRepository.save(nuevaLocalidad);
                });
    }

    // ==================== MÉTODOS PARA DASHBOARD/REPORTES ====================





    @Transactional(readOnly = true)
    public List<Localidad> listarTodas() {
        List<Localidad> localidades = localidadRepository.findAllByOrderByNombreAsc();

        // Inicializar colecciones en consultas separadas
        for (Localidad localidad : localidades) {
            // Inicializar funcionarios en consulta separada
            Hibernate.initialize(localidad.getFuncionarios());
        }

        return localidades;
    }

    @Transactional(readOnly = true)
    public List<Localidad> listarTodasConFuncionarios() {
        // Usa el método específico del repository
        return localidadRepository.findAllWithFuncionarios();
    }

    @Transactional(readOnly = true)
    public List<Localidad> listarTodasConRecuentos() {
        List<Localidad> localidades = localidadRepository.findAllByOrderByNombreAsc();

        // Inicializar solo los tamaños, no toda la colección
        for (Localidad localidad : localidades) {
            // Solo necesitamos el tamaño, no los elementos
            localidad.getFuncionarios().size();
            localidad.getRecibos().size();
        }

        return localidades;
    }

}