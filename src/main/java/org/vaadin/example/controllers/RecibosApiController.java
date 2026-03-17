package org.vaadin.example.controllers;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vaadin.example.data.Funcionario;
import org.vaadin.example.data.ReciboProcesado;
import org.vaadin.example.services.FuncionarioService;
import org.vaadin.example.services.ReciboService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/recibosw")
@CrossOrigin(origins = "*")
public class RecibosApiController {

    private final ReciboService reciboService;
    private final FuncionarioService funcionarioService;

    public RecibosApiController(ReciboService reciboService,
                                FuncionarioService funcionarioService) {
        this.reciboService = reciboService;
        this.funcionarioService = funcionarioService;
    }

    /**
     * API 1: Consultar recibos disponibles por teléfono (para WhatsApp/BotAdmin)
     * GET /api/v1/recibos/consultar/{telefono}?mesAnio=012025
     */
    @GetMapping("/consultar/{telefono}")
    public ResponseEntity<Map<String, Object>> consultarRecibosPorTelefono(
            @PathVariable String telefono,
            @RequestParam(required = false) String mesAnio) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "Recibos API");

        try {
            // Normalizar teléfono
            String telefonoNormalizado = normalizarTelefono(telefono);

            // Buscar funcionario por teléfono
            Optional<Funcionario> funcionarioOpt = funcionarioService
                    .buscarPorTelefono(telefonoNormalizado);

            if (funcionarioOpt.isEmpty()) {
                response.put("success", false);
                response.put("error", true);
                response.put("mensaje", "❌ No estás registrado en el sistema. Contacta a RRHH.");
                response.put("codigo", "FUNCIONARIO_NO_ENCONTRADO");
                return ResponseEntity.status(404).body(response);
            }

            Funcionario funcionario = funcionarioOpt.get();
            String ci = funcionario.getCi();

            // Buscar recibos
            List<ReciboProcesado> recibos;
            if (mesAnio != null && !mesAnio.isEmpty()) {
                recibos = reciboService.buscarPorCiYMes(ci, mesAnio);
            } else {
                // Últimos 6 meses por defecto
                recibos = reciboService.obtenerUltimosRecibos(ci, 6);
            }

            if (recibos.isEmpty()) {
                response.put("success", true);
                response.put("mensaje", mesAnio != null ?
                        "📭 No tienes recibos para " + formatearMesAnio(mesAnio) :
                        "📭 No tienes recibos disponibles (últimos 6 meses)");
                response.put("total", 0);
                response.put("recibos", Collections.emptyList());
                return ResponseEntity.ok(response);
            }

            // Formatear respuesta
            response.put("success", true);
            response.put("funcionario", funcionario.getNombre());
            response.put("ci", ci);
            response.put("localidad", funcionario.getLocalidad() != null ?
                    funcionario.getLocalidad().getNombre() : "No asignada");
            response.put("total", recibos.size());

            List<Map<String, Object>> recibosFormateados = recibos.stream()
                    .map(recibo -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("id", recibo.getId());
                        info.put("tipo", recibo.getTipo());
                        info.put("mes_anio", recibo.getMesAnio());
                        info.put("mes_anio_formateado", formatearMesAnio(recibo.getMesAnio()));
                        info.put("fecha_procesado", recibo.getProcesadoEn());
                        info.put("nombre_archivo", recibo.getNombreArchivo());
                        info.put("localidad", recibo.getLocalidad() != null ?
                                recibo.getLocalidad().getNombre() : "Sin localidad");
                        info.put("descargar_url", "/api/v1/recibos/" + recibo.getId() + "/descargar?telefono=" + telefono);
                        info.put("ver_url", "/api/v1/recibos/" + recibo.getId() + "/ver?telefono=" + telefono);
                        return info;
                    })
                    .sorted((a, b) -> {
                        // Ordenar por fecha (más reciente primero)
                        String mesAnioA = (String) a.get("mes_anio");
                        String mesAnioB = (String) b.get("mes_anio");
                        return mesAnioB.compareTo(mesAnioA);
                    })
                    .collect(Collectors.toList());

            response.put("recibos", recibosFormateados);

            // Crear mensaje legible para WhatsApp
            StringBuilder mensajeWhatsApp = new StringBuilder();
            mensajeWhatsApp.append("✅ *RECIBOS DISPONIBLES*\n\n");
            mensajeWhatsApp.append("👤 *").append(funcionario.getNombre()).append("*\n");
            mensajeWhatsApp.append("📱 *Tel:* ").append(formatearTelefono(telefono)).append("\n");
            mensajeWhatsApp.append("📍 *Localidad:* ").append(
                    funcionario.getLocalidad() != null ?
                            funcionario.getLocalidad().getNombre() : "No asignada").append("\n\n");

            for (int i = 0; i < recibosFormateados.size(); i++) {
                Map<String, Object> recibo = recibosFormateados.get(i);
                mensajeWhatsApp.append("➡️ *").append(i + 1).append(".* ")
                        .append(recibo.get("mes_anio_formateado")).append(" - ")
                        .append(recibo.get("tipo")).append("\n");
            }

            mensajeWhatsApp.append("\n📥 *Para descargar:*\n");
            mensajeWhatsApp.append("Escribe el número (ej: *1*)\n\n");
            mensajeWhatsApp.append("🔄 *Otras opciones:*\n");
            mensajeWhatsApp.append("• *todos* - Ver todos los recibos\n");
            mensajeWhatsApp.append("• *ayuda* - Mostrar ayuda");

            response.put("mensaje_whatsapp", mensajeWhatsApp.toString());
            response.put("opciones_validas", generarOpcionesValidas(recibosFormateados.size()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", true);
            response.put("mensaje", "⚠️ Error en el servidor. Intenta más tarde.");
            response.put("detalle_error", e.getMessage());
            response.put("codigo", "ERROR_SERVIDOR");
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * API 2: Descargar recibo específico (attachment)
     * GET /api/v1/recibos/{id}/descargar?telefono=59812345678
     */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<Resource> descargarRecibo(
            @PathVariable Long id,
            @RequestParam String telefono,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        try {
            Optional<ReciboProcesado> reciboOpt = reciboService.buscarPorId(id);

            if (reciboOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ReciboProcesado recibo = reciboOpt.get();

            // Validar que el teléfono corresponde al CI del recibo
            String telefonoNormalizado = normalizarTelefono(telefono);
            Optional<Funcionario> funcionarioOpt = funcionarioService
                    .buscarPorTelefono(telefonoNormalizado);

            if (funcionarioOpt.isEmpty() ||
                    !funcionarioOpt.get().getCi().equals(recibo.getCi())) {
                return ResponseEntity.status(403).build();
            }

            // Cargar archivo PDF
            Path path = Paths.get(recibo.getRutaArchivo());
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.status(404).build();
            }

            // Registrar descarga
            reciboService.registrarDescarga(id, telefono);

            // Configurar headers para descarga
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + recibo.getNombreArchivo() + "\"");
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.add(HttpHeaders.PRAGMA, "no-cache");
            headers.add(HttpHeaders.EXPIRES, "0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(resource.contentLength())
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * API 3: Ver recibo en navegador (inline)
     * GET /api/v1/recibos/{id}/ver?telefono=59812345678
     */
    @GetMapping("/{id}/ver")
    public ResponseEntity<Resource> verRecibo(
            @PathVariable Long id,
            @RequestParam String telefono) {

        try {
            Optional<ReciboProcesado> reciboOpt = reciboService.buscarPorId(id);

            if (reciboOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ReciboProcesado recibo = reciboOpt.get();

            // Validar que el teléfono corresponde al CI del recibo
            String telefonoNormalizado = normalizarTelefono(telefono);
            Optional<Funcionario> funcionarioOpt = funcionarioService
                    .buscarPorTelefono(telefonoNormalizado);

            if (funcionarioOpt.isEmpty() ||
                    !funcionarioOpt.get().getCi().equals(recibo.getCi())) {
                return ResponseEntity.status(403).build();
            }

            // Cargar archivo PDF
            Path path = Paths.get(recibo.getRutaArchivo());
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.status(404).build();
            }

            // Configurar headers para visualización en navegador
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + recibo.getNombreArchivo() + "\"");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * API 4: Listar recibos con filtros (para administración)
     * GET /api/v1/recibos?ci=43341950&tipo=MENSUAL&localidad=RN&mesAnio=012025
     */
    @GetMapping("")
    public ResponseEntity<Map<String, Object>> listarRecibos(
            @RequestParam(required = false) String ci,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String localidad,
            @RequestParam(required = false) String mesAnio,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanio) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            List<ReciboProcesado> recibos;

            if (ci != null && tipo != null && localidad != null && mesAnio != null) {
                recibos = reciboService.buscarPorCiTipoLocalidadYMes(ci, tipo, localidad, mesAnio);
            } else if (ci != null && tipo != null && mesAnio != null) {
                recibos = reciboService.buscarPorCiTipoYMes(ci, tipo, mesAnio);
            } else if (ci != null && tipo != null) {
                recibos = reciboService.buscarPorCiYTipo(ci, tipo);
            } else if (ci != null) {
                recibos = reciboService.buscarPorCi(ci);
            } else if (localidad != null) {
                recibos = reciboService.buscarPorLocalidad(localidad);
            } else if (mesAnio != null) {
                recibos = reciboService.buscarPorMes(mesAnio);
            } else if (tipo != null) {
                recibos = reciboService.buscarPorTipo(tipo);
            } else {
                // Paginación para todos los recibos
                recibos = reciboService.listarPaginado(pagina, tamanio);
                long total = reciboService.contarTotal();
                response.put("pagina", pagina);
                response.put("tamanio_pagina", tamanio);
                response.put("total_registros", total);
                response.put("total_paginas", (int) Math.ceil((double) total / tamanio));
            }

            response.put("success", true);
            response.put("total", recibos.size());
            response.put("recibos", recibos.stream()
                    .map(this::convertirReciboAMap)
                    .collect(Collectors.toList()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * API 5: Obtener estadísticas
     * GET /api/v1/recibos/estadisticas
     */
    @GetMapping("/estadisticas")
    public ResponseEntity<Map<String, Object>> obtenerEstadisticas(
            @RequestParam(required = false) String localidad,
            @RequestParam(required = false) String mesAnio) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());

        try {
            // Estadísticas generales
            long totalRecibos = reciboService.contarTotal();
            long recibosHoy = reciboService.contarRecibosHoy();
            long recibosEsteMes = reciboService.contarRecibosEsteMes();

            // Por tipo
            Map<String, Long> porTipo = reciboService.contarPorTipo();

            // Por localidad
            Map<String, Long> porLocalidad = reciboService.contarPorLocalidad();

            // Por mes
            Map<String, Long> porMes = reciboService.contarPorMes(6); // Últimos 6 meses

            response.put("success", true);
            response.put("estadisticas", Map.of(
                    "total_recibos", totalRecibos,
                    "recibos_hoy", recibosHoy,
                    "recibos_este_mes", recibosEsteMes,
                    "por_tipo", porTipo,
                    "por_localidad", porLocalidad,
                    "por_mes", porMes
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * API 6: Health Check
     * GET /api/v1/recibos/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Recibos API v1");
        health.put("timestamp", LocalDateTime.now());
        health.put("version", "1.0.0");

        try {
            long totalRecibos = reciboService.contarTotal();
            health.put("database", "CONNECTED");
            health.put("total_recibos", totalRecibos);
            health.put("ultimo_recibo", reciboService.obtenerUltimoRecibo()
                    .map(r -> r.getProcesadoEn().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .orElse("Ninguno"));
        } catch (Exception e) {
            health.put("database", "DISCONNECTED");
            health.put("error", e.getMessage());
        }

        return ResponseEntity.ok(health);
    }

    // ==================== MÉTODOS AUXILIARES ====================

    private String normalizarTelefono(String telefono) {
        if (telefono == null) return "";

        // Eliminar espacios, guiones, paréntesis, +
        String limpio = telefono.replaceAll("[\\s\\-\\(\\)\\+]", "");

        // Si tiene 8 dígitos, agregar 598
        if (limpio.length() == 8) {
            limpio = "598" + limpio;
        }
        // Si empieza con 0 y tiene 9 dígitos, quitar 0 y agregar 598
        else if (limpio.length() == 9 && limpio.startsWith("0")) {
            limpio = "598" + limpio.substring(1);
        }

        return limpio;
    }

    private String formatearTelefono(String telefono) {
        String normalizado = normalizarTelefono(telefono);
        if (normalizado.length() == 11 && normalizado.startsWith("598")) {
            return "+" + normalizado.substring(0, 3) + " " +
                    normalizado.substring(3, 7) + " " +
                    normalizado.substring(7);
        }
        return telefono;
    }

    private String formatearMesAnio(String mesAnio) {
        if (mesAnio == null || mesAnio.length() != 6) {
            return mesAnio;
        }

        String mes = mesAnio.substring(0, 2);
        String anio = mesAnio.substring(2);

        Map<String, String> meses = Map.ofEntries(
                Map.entry("01", "Enero"), Map.entry("02", "Febrero"),
                Map.entry("03", "Marzo"), Map.entry("04", "Abril"),
                Map.entry("05", "Mayo"), Map.entry("06", "Junio"),
                Map.entry("07", "Julio"), Map.entry("08", "Agosto"),
                Map.entry("09", "Septiembre"), Map.entry("10", "Octubre"),
                Map.entry("11", "Noviembre"), Map.entry("12", "Diciembre")
        );

        return meses.getOrDefault(mes, mes) + " " + anio;
    }

    private Map<String, Object> convertirReciboAMap(ReciboProcesado recibo) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put("id", recibo.getId());
        mapa.put("ci", recibo.getCi());
        mapa.put("tipo", recibo.getTipo());
        mapa.put("mes_anio", recibo.getMesAnio());
        mapa.put("mes_anio_formateado", formatearMesAnio(recibo.getMesAnio()));
        mapa.put("nombre_archivo", recibo.getNombreArchivo());
        mapa.put("ruta_archivo", recibo.getRutaArchivo());
        mapa.put("procesado_en", recibo.getProcesadoEn());
        mapa.put("localidad", recibo.getLocalidad() != null ?
                recibo.getLocalidad().getNombre() : null);
        mapa.put("localidad_codigo", recibo.getLocalidadCodigo());
        return mapa;
    }

    private List<String> generarOpcionesValidas(int totalRecibos) {
        List<String> opciones = new ArrayList<>();
        for (int i = 1; i <= totalRecibos; i++) {
            opciones.add(String.valueOf(i));
        }
        opciones.add("todos");
        opciones.add("ayuda");
        opciones.add("menu");
        return opciones;
    }
}