package org.vaadin.example.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vaadin.example.data.ReciboProcesado;
import org.vaadin.example.data.ReciboProcesadoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReciboService {

    private final ReciboProcesadoRepository repository;

    public ReciboService(ReciboProcesadoRepository repository) {
        this.repository = repository;
    }

    // ==================== CONSULTAS BÁSICAS ====================

    @Transactional(readOnly = true)
    public Optional<ReciboProcesado> buscarPorId(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ReciboProcesado> buscarPorCi(String ci) {
        return repository.findByCi(ci);
    }

    @Transactional(readOnly = true)
    public List<ReciboProcesado> buscarPorCiYMes(String ci, String mesAnio) {
        return repository.findByCiAndMesAnio(ci, mesAnio);
    }

    @Transactional(readOnly = true)
    public List<ReciboProcesado> buscarPorCiYTipo(String ci, String tipo) {
        return repository.findByCiAndTipo(ci, tipo);
    }

    @Transactional(readOnly = true)
    public List<ReciboProcesado> buscarPorCiTipoYMes(String ci, String tipo, String mesAnio) {
        return repository.findByCiAndTipoAndMesAnio(ci, tipo, mesAnio);
    }

    @Transactional(readOnly = true)
    public List<ReciboProcesado> buscarPorLocalidad(String localidadCodigo) {
        return repository.findByLocalidadCodigo(localidadCodigo);
    }

    @Transactional(readOnly = true)
    public List<ReciboProcesado> buscarPorTipo(String tipo) {
        return repository.findByTipo(tipo);
    }

    @Transactional(readOnly = true)
    public List<ReciboProcesado> buscarPorMes(String mesAnio) {
        return repository.findByMesAnio(mesAnio);
    }

    @Transactional(readOnly = true)
    public List<ReciboProcesado> buscarPorCiTipoLocalidadYMes(String ci, String tipo,
                                                              String localidadCodigo,
                                                              String mesAnio) {
        return repository.findByCiAndTipoAndLocalidadCodigoAndMesAnio(ci, tipo,
                localidadCodigo, mesAnio);
    }

    // ==================== CONSULTAS AVANZADAS ====================

    @Transactional(readOnly = true)
    public List<ReciboProcesado> obtenerUltimosRecibos(String ci, int cantidadMeses) {
        LocalDateTime fechaLimite = LocalDateTime.now().minusMonths(cantidadMeses);
        return repository.findByCiAndProcesadoEnAfter(ci, fechaLimite);
    }

    @Transactional(readOnly = true)
    public List<ReciboProcesado> listarTodos() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ReciboProcesado> listarPaginado(int pagina, int tamanio) {
        Pageable pageable = PageRequest.of(pagina, tamanio);
        Page<ReciboProcesado> page = repository.findAll(pageable);
        return page.getContent();
    }

    @Transactional(readOnly = true)
    public Optional<ReciboProcesado> obtenerUltimoRecibo() {
        return repository.findFirstByOrderByProcesadoEnDesc();
    }

    // ==================== ESTADÍSTICAS ====================

    @Transactional(readOnly = true)
    public long contarTotal() {
        return repository.count();
    }

    @Transactional(readOnly = true)
    public long contarRecibosHoy() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime finDia = LocalDateTime.now();
        return repository.countByProcesadoEnBetween(inicioDia, finDia);
    }

    @Transactional(readOnly = true)
    public long contarRecibosEsteMes() {
        YearMonth mesActual = YearMonth.now();
        LocalDateTime inicioMes = mesActual.atDay(1).atStartOfDay();
        LocalDateTime finMes = LocalDateTime.now();
        return repository.countByProcesadoEnBetween(inicioMes, finMes);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> contarPorTipo() {
        List<Object[]> resultados = repository.countByTipoGrouped();
        return resultados.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]
                ));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> contarPorLocalidad() {
        List<Object[]> resultados = repository.countByLocalidadGrouped();
        return resultados.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]
                ));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> contarPorMes(int mesesAtras) {
        LocalDateTime fechaLimite = LocalDateTime.now().minusMonths(mesesAtras);
        List<Object[]> resultados = repository.countByMesGrouped(fechaLimite);

        return resultados.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]
                ));
    }

    // ==================== OPERACIONES ====================

    @Transactional
    public void registrarDescarga(Long reciboId, String telefono) {
        repository.findById(reciboId).ifPresent(recibo -> {
            // Aquí puedes agregar lógica adicional para registrar descargas
            // Por ejemplo, incrementar un contador, guardar en otra tabla, etc.
            System.out.println("📥 Recibo " + reciboId + " descargado por " + telefono);
        });
    }

    @Transactional
    public ReciboProcesado guardar(ReciboProcesado recibo) {
        return repository.save(recibo);
    }

    @Transactional
    public void eliminar(Long id) {
        repository.deleteById(id);
    }
}