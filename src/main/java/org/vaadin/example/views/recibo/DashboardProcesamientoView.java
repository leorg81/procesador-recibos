package org.vaadin.example.views.recibo;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.vaadin.example.data.Localidad;
import org.vaadin.example.data.ReciboProcesado;
import org.vaadin.example.services.LocalidadService;
import org.vaadin.example.services.ReciboService;
import org.vaadin.example.views.MainLayout;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@PageTitle("Dashboard - Procesamiento")
@Route(value = "dashboard-procesamiento", layout = MainLayout.class)
@Menu(order = 3, icon = LineAwesomeIconUrl.CHART_BAR_SOLID)
@RolesAllowed({"ADMIN", "LIQUIDACIONES","PERSONAL"})
public class DashboardProcesamientoView extends VerticalLayout {

    private final ReciboService reciboService;
    private final LocalidadService localidadService;

    private final Select<String> filtroPeriodo = new Select<>();
    private final Select<Localidad> filtroLocalidad = new Select<>();
    private final TextField busqueda = new TextField("Buscar CI o Nombre");

    private final Grid<ResumenLocalidad> gridResumen = new Grid<>();
    private final Grid<ReciboProcesado> gridDetalle = new Grid<>();

    // Tarjetas de estadísticas
    private final Span cardTotalRecibos = new Span();
    private final Span cardRecibosHoy = new Span();
    private final Span cardRecibosMes = new Span();
    private final Span cardLocalidades = new Span();
    private final Span cardFiabilidadPromedio = new Span();


    public DashboardProcesamientoView(ReciboService reciboService,
                                      LocalidadService localidadService) {
        this.reciboService = reciboService;
        this.localidadService = localidadService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("📊 Dashboard de Procesamiento");

        // Configurar filtros
        configurarFiltros();

        // Configurar grids
        configurarGrids();

        // Layout principal
        VerticalLayout contenido = new VerticalLayout();
        contenido.setSizeFull();
        contenido.setSpacing(true);

        // Panel de filtros
        HorizontalLayout panelFiltros = new HorizontalLayout(
                new VerticalLayout(new H3("Filtros"), filtroPeriodo, filtroLocalidad),
                new VerticalLayout(new H3("Búsqueda"), busqueda)
        );
        panelFiltros.setWidthFull();
        panelFiltros.setSpacing(true);

        // Panel de estadísticas (tarjetas)
        HorizontalLayout panelEstadisticas = crearPanelEstadisticas();

        // Panel de grids
        HorizontalLayout panelGrids = new HorizontalLayout(gridResumen, gridDetalle);
        panelGrids.setSizeFull();
        panelGrids.setHeight("500px");

        contenido.add(panelFiltros, panelEstadisticas, panelGrids);
        add(titulo, contenido);

        // Cargar datos iniciales
        cargarDatos();
    }

    private HorizontalLayout crearPanelEstadisticas() {
        HorizontalLayout panel = new HorizontalLayout();
        panel.setWidthFull();
        panel.setSpacing(true);

        // Tarjeta 1: Total de recibos
        VerticalLayout card1 = crearTarjetaEstadistica(
                "Total Recibos",
                cardTotalRecibos,
                VaadinIcon.FILE_TABLE,
                "#4CAF50"
        );

        // Tarjeta 2: Confianza Promedio (NUEVA)
        VerticalLayout card2 = crearTarjetaEstadistica(
                "Confianza Prom.",
                cardFiabilidadPromedio, // Necesitas crear este Span
                VaadinIcon.TRENDING_UP,
                "#2196F3"
        );

        // Tarjeta 3: Recibos hoy (MANTÉN o CAMBIA)
        VerticalLayout card3 = crearTarjetaEstadistica(
                "Recibos Hoy",
                cardRecibosHoy,
                VaadinIcon.CALENDAR_CLOCK,
                "#FF9800"
        );

        // Tarjeta 4: Localidades activas (MANTÉN)
        VerticalLayout card4 = crearTarjetaEstadistica(
                "Localidades Activas",
                cardLocalidades,
                VaadinIcon.MAP_MARKER,
                "#9C27B0"
        );

        panel.add(card1, card2, card3, card4);
        return panel;
    }

    private VerticalLayout crearTarjetaEstadistica(String titulo, Span valor, VaadinIcon icono, String color) {
        VerticalLayout card = new VerticalLayout();
        card.getStyle()
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "8px")
                .set("padding", "1rem")
                .set("background", "white")
                .set("box-shadow", "0 2px 4px rgba(0,0,0,0.1)");
        card.setSpacing(false);
        card.setPadding(false);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        Span tituloSpan = new Span(titulo);
        tituloSpan.getStyle()
                .set("font-size", "0.875rem")
                .set("color", "var(--lumo-secondary-text-color)");

        Icon icon = icono.create();
        icon.setSize("16px");
        icon.getStyle().set("color", color);

        header.add(tituloSpan, icon);

        valor.getStyle()
                .set("font-size", "1.5rem")
                .set("font-weight", "bold")
                .set("color", "var(--lumo-header-text-color)");

        card.add(header, valor);
        return card;
    }

    private void configurarFiltros() {
        // Filtro de período
        filtroPeriodo.setLabel("Período");
        filtroPeriodo.setItems(
                "Hoy",
                "Esta semana",
                "Este mes",
                "Últimos 3 meses",
                "Últimos 6 meses",
                "Este año",
                "Todos"
        );
        filtroPeriodo.setValue("Este mes");
        filtroPeriodo.addValueChangeListener(e -> cargarDatos());

        // Filtro de localidad
        filtroLocalidad.setLabel("Localidad");
        filtroLocalidad.setItems(localidadService.listarTodas());
        filtroLocalidad.setItemLabelGenerator(Localidad::getNombre);
        filtroLocalidad.setPlaceholder("Todas las localidades");
        filtroLocalidad.addValueChangeListener(e -> cargarDatos());

        // Búsqueda
        busqueda.setPlaceholder("Ingrese CI o nombre...");
        busqueda.setClearButtonVisible(true);
        busqueda.addValueChangeListener(e -> filtrarDetalles());
    }

    private void configurarGrids() {
        // Grid de resumen por localidad
        gridResumen.addColumn(ResumenLocalidad::getLocalidad)
                .setHeader("Localidad")
                .setAutoWidth(true);

        gridResumen.addColumn(ResumenLocalidad::getTotalRecibos)
                .setHeader("Total Recibos")
                .setAutoWidth(true);

        gridResumen.addColumn(r -> String.format("%.1f%%", r.getPorcentaje()))
                .setHeader("% del Total")
                .setAutoWidth(true);

        gridResumen.addColumn(ResumenLocalidad::getUltimoProcesamiento)
                .setHeader("Último Procesamiento")
                .setAutoWidth(true);

        gridResumen.setSelectionMode(Grid.SelectionMode.SINGLE);
        gridResumen.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        // Cuando se selecciona una localidad en el resumen
        gridResumen.asSingleSelect().addValueChangeListener(e -> {
            ResumenLocalidad seleccionado = e.getValue();
            if (seleccionado != null) {
                cargarDetallesLocalidad(seleccionado.getLocalidadCodigo());
            } else {
                cargarDetallesLocalidad(null);
            }
        });

        // Grid de detalle
        gridDetalle.addColumn(ReciboProcesado::getCi)
                .setHeader("CI")
                .setAutoWidth(true);

        gridDetalle.addColumn(r -> {
                    String nombres = r.getNombres() != null ? r.getNombres() : "";
                    String apellidos = r.getApellidos() != null ? r.getApellidos() : "";
                    return (nombres + " " + apellidos).trim();
                }).setHeader("Nombre")
                .setAutoWidth(true);

        gridDetalle.addColumn(r -> formatearMesAnio(r.getMesAnio()))
                .setHeader("Mes/Año")
                .setAutoWidth(true);

        gridDetalle.addColumn(ReciboProcesado::getTipo)
                .setHeader("Tipo")
                .setAutoWidth(true);

        gridDetalle.addColumn(r -> {
                    Double confianza = r.getConfianza();

                    // Si no tiene confianza, calcularla
                    if (confianza == null || confianza == 0.0) {
                        confianza = calcularConfianzaEnTiempoReal(r);
                    }

                    // Devolver solo el texto formateado (sin Span)
                    return String.format("%.0f%%", confianza * 100);

                }).setHeader("Confianza")
                .setComparator((r1, r2) -> {
                    Double c1 = r1.getConfianza() != null ? r1.getConfianza() : calcularConfianzaEnTiempoReal(r1);
                    Double c2 = r2.getConfianza() != null ? r2.getConfianza() : calcularConfianzaEnTiempoReal(r2);
                    return Double.compare(c1, c2);
                })
                .setAutoWidth(true);

        gridDetalle.addColumn(ReciboProcesado::getNombreArchivo)
                .setHeader("Archivo")
                .setAutoWidth(true);

        gridDetalle.addColumn(r -> r.getProcesadoEn() != null ?
                        r.getProcesadoEn().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "")
                .setHeader("Procesado")
                .setAutoWidth(true);

        gridDetalle.addThemeVariants(GridVariant.LUMO_COMPACT);

        // Ajustar tamaños
        gridResumen.setWidth("40%");
        gridDetalle.setWidth("60%");
    }

    private void cargarDatos() {
        // Obtener período seleccionado
        LocalDateTime fechaDesde = calcularFechaDesde(filtroPeriodo.getValue());

        // Actualizar tarjetas de estadísticas
        actualizarEstadisticas(fechaDesde);

        // Obtener datos del servicio usando métodos reales
        List<ResumenLocalidad> resumen = obtenerResumenPorLocalidad(fechaDesde);

        // Actualizar grid de resumen
        gridResumen.setItems(resumen);

        // Cargar todos los detalles inicialmente
        cargarDetallesLocalidad(null);
    }

    private Double calcularConfianzaEnTiempoReal(ReciboProcesado recibo) {
        double confianza = 0.0;

        // 1. CI válido (40 puntos)
        if (recibo.getCi() != null && !recibo.getCi().equals("desconocido")) {
            if (recibo.getCi().matches("\\d{7,8}-?\\d?")) {
                confianza += 0.4;
            } else {
                confianza += 0.2; // Medio puntos si tiene CI pero formato raro
            }
        }

        // 2. Nombres completos (30 puntos)
        if (recibo.getNombres() != null && !recibo.getNombres().isEmpty() &&
                recibo.getApellidos() != null && !recibo.getApellidos().isEmpty()) {
            confianza += 0.3;
        }

        // 3. Datos básicos presentes (30 puntos)
        if (recibo.getMesAnio() != null && recibo.getMesAnio().length() == 6) {
            confianza += 0.1;
        }
        if (recibo.getTipo() != null && !recibo.getTipo().isEmpty()) {
            confianza += 0.1;
        }
        if (recibo.getLocalidadCodigo() != null && !recibo.getLocalidadCodigo().isEmpty()) {
            confianza += 0.1;
        }

        return confianza;
    }


    private void actualizarEstadisticas(LocalDateTime fechaDesde) {
        List<ReciboProcesado> todosRecibos = reciboService.listarTodos();

        // Filtrar por fecha si es necesario
        if (!fechaDesde.equals(LocalDateTime.of(2000, 1, 1, 0, 0))) {
            todosRecibos = todosRecibos.stream()
                    .filter(r -> r.getProcesadoEn() != null &&
                            r.getProcesadoEn().isAfter(fechaDesde))
                    .collect(Collectors.toList());
        }

        // Filtrar por localidad si está seleccionada
        if (filtroLocalidad.getValue() != null) {
            String codigoLocalidad = filtroLocalidad.getValue().getCodigo();
            todosRecibos = todosRecibos.stream()
                    .filter(r -> r.getLocalidadCodigo() != null &&
                            r.getLocalidadCodigo().equals(codigoLocalidad))
                    .collect(Collectors.toList());
        }


        // Estadísticas básicas
        long total = todosRecibos.size();

        // Recibos hoy
        long hoy = todosRecibos.stream()
                .filter(r -> r.getProcesadoEn() != null &&
                        r.getProcesadoEn().toLocalDate().equals(LocalDate.now()))
                .count();

        // Recibos este mes
        long esteMes = todosRecibos.stream()
                .filter(r -> r.getProcesadoEn() != null &&
                        r.getProcesadoEn().getMonth() == LocalDateTime.now().getMonth() &&
                        r.getProcesadoEn().getYear() == LocalDateTime.now().getYear())
                .count();

        // Localidades activas
        long localidadesActivas = todosRecibos.stream()
                .filter(r -> r.getLocalidadCodigo() != null)
                .map(ReciboProcesado::getLocalidadCodigo)
                .distinct()
                .count();

        double confianzaPromedio = todosRecibos.stream()
                .mapToDouble(r -> {
                    Double confianza = r.getConfianza();
                    if (confianza == null || confianza == 0.0) {
                        // Calcular en tiempo real si no tiene
                        return calcularConfianzaEnTiempoReal(r);
                    }
                    return confianza;
                })
                .average()
                .orElse(0.0);

        // Actualizar tarjetas
        cardTotalRecibos.setText(String.valueOf(total));
        cardFiabilidadPromedio.setText(String.format("%.1f%%", confianzaPromedio * 100));
        cardRecibosHoy.setText(String.valueOf(hoy));
        cardLocalidades.setText(String.valueOf(localidadesActivas));
    }

    private List<ResumenLocalidad> obtenerResumenPorLocalidad(LocalDateTime fechaDesde) {
        List<ReciboProcesado> todosRecibos = reciboService.listarTodos();

        // Filtrar por fecha si es necesario
        if (!fechaDesde.equals(LocalDateTime.of(2000, 1, 1, 0, 0))) {
            todosRecibos = todosRecibos.stream()
                    .filter(r -> r.getProcesadoEn() != null &&
                            r.getProcesadoEn().isAfter(fechaDesde))
                    .collect(Collectors.toList());
        }

        // Filtrar por localidad si está seleccionada
        if (filtroLocalidad.getValue() != null) {
            String codigoLocalidad = filtroLocalidad.getValue().getCodigo();
            todosRecibos = todosRecibos.stream()
                    .filter(r -> r.getLocalidadCodigo() != null &&
                            r.getLocalidadCodigo().equals(codigoLocalidad))
                    .collect(Collectors.toList());
        }

        // Agrupar por localidad
        Map<String, List<ReciboProcesado>> recibosPorLocalidad = todosRecibos.stream()
                .filter(r -> r.getLocalidadCodigo() != null)
                .collect(Collectors.groupingBy(ReciboProcesado::getLocalidadCodigo));

        long totalRecibos = todosRecibos.size();

        List<ResumenLocalidad> resumen = new ArrayList<>();

        // Para cada localidad, crear un resumen
        for (Map.Entry<String, List<ReciboProcesado>> entry : recibosPorLocalidad.entrySet()) {
            String codigoLocalidad = entry.getKey();
            List<ReciboProcesado> recibos = entry.getValue();

            // Obtener nombre de localidad
            String nombreLocalidad = localidadService.listarTodas().stream()
                    .filter(l -> l.getCodigo().equals(codigoLocalidad))
                    .map(Localidad::getNombre)
                    .findFirst()
                    .orElse(codigoLocalidad);

            // Calcular porcentaje
            double porcentaje = totalRecibos > 0 ?
                    (recibos.size() * 100.0) / totalRecibos : 0;

            // Encontrar último procesamiento
            LocalDateTime ultimoProcesamiento = recibos.stream()
                    .map(ReciboProcesado::getProcesadoEn)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);

            resumen.add(new ResumenLocalidad(
                    nombreLocalidad,
                    codigoLocalidad,
                    recibos.size(),
                    porcentaje,
                    ultimoProcesamiento
            ));
        }

        // Ordenar por cantidad de recibos (descendente)
        resumen.sort((a, b) -> Long.compare(b.getTotalRecibos(), a.getTotalRecibos()));

        return resumen;
    }

    private void cargarDetallesLocalidad(String codigoLocalidad) {
        List<ReciboProcesado> detalles;

        if (codigoLocalidad == null) {
            // Cargar todos los recibos
            detalles = reciboService.listarTodos();
        } else {
            // Filtrar por localidad
            detalles = reciboService.listarTodos().stream()
                    .filter(r -> r.getLocalidadCodigo() != null &&
                            r.getLocalidadCodigo().equals(codigoLocalidad))
                    .collect(Collectors.toList());
        }

        // Aplicar filtro de fecha
        LocalDateTime fechaDesde = calcularFechaDesde(filtroPeriodo.getValue());
        if (!fechaDesde.equals(LocalDateTime.of(2000, 1, 1, 0, 0))) {
            detalles = detalles.stream()
                    .filter(r -> r.getProcesadoEn() != null &&
                            r.getProcesadoEn().isAfter(fechaDesde))
                    .collect(Collectors.toList());
        }

        // Limitar a 100 registros
        detalles = detalles.stream()
                .limit(100)
                .collect(Collectors.toList());

        gridDetalle.setItems(detalles);
    }

    private void filtrarDetalles() {
        String busquedaTexto = busqueda.getValue().trim().toLowerCase();

        List<ReciboProcesado> todos = reciboService.listarTodos();

        if (busquedaTexto.isEmpty()) {
            cargarDetallesLocalidad(
                    filtroLocalidad.getValue() != null ?
                            filtroLocalidad.getValue().getCodigo() : null
            );
        } else {
            List<ReciboProcesado> filtrados = todos.stream()
                    .filter(r -> {
                        boolean coincideCI = r.getCi() != null &&
                                r.getCi().toLowerCase().contains(busquedaTexto);

                        boolean coincideNombre = false;
                        if (r.getNombres() != null && r.getApellidos() != null) {
                            String nombreCompleto = (r.getNombres() + " " + r.getApellidos()).toLowerCase();
                            coincideNombre = nombreCompleto.contains(busquedaTexto);
                        }

                        return coincideCI || coincideNombre;
                    })
                    .collect(Collectors.toList());
            gridDetalle.setItems(filtrados);
        }
    }

    private LocalDateTime calcularFechaDesde(String periodo) {
        LocalDateTime ahora = LocalDateTime.now();

        switch (periodo) {
            case "Hoy":
                return LocalDate.now().atStartOfDay();
            case "Esta semana":
                return ahora.minusDays(7);
            case "Este mes":
                return ahora.minusMonths(1);
            case "Últimos 3 meses":
                return ahora.minusMonths(3);
            case "Últimos 6 meses":
                return ahora.minusMonths(6);
            case "Este año":
                return LocalDateTime.of(ahora.getYear(), 1, 1, 0, 0);
            default:
                return LocalDateTime.of(2000, 1, 1, 0, 0); // Todos
        }
    }

    private String formatearMesAnio(String mesAnio) {
        if (mesAnio != null && mesAnio.length() == 6) {
            String mes = mesAnio.substring(0, 2);
            String anio = mesAnio.substring(2);
            return mes + "/" + anio;
        }
        return mesAnio != null ? mesAnio : "";
    }

    // Clase interna para el resumen
    public static class ResumenLocalidad {
        private String localidad;
        private String localidadCodigo;
        private long totalRecibos;
        private double porcentaje;
        private String ultimoProcesamiento;

        // Constructor
        public ResumenLocalidad(String localidad, String codigo, long total,
                                double porcentaje, LocalDateTime ultimo) {
            this.localidad = localidad;
            this.localidadCodigo = codigo;
            this.totalRecibos = total;
            this.porcentaje = porcentaje;
            this.ultimoProcesamiento = ultimo != null ?
                    ultimo.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) :
                    "Nunca";
        }

        // Getters
        public String getLocalidad() { return localidad; }
        public String getLocalidadCodigo() { return localidadCodigo; }
        public long getTotalRecibos() { return totalRecibos; }
        public double getPorcentaje() { return porcentaje; }
        public String getUltimoProcesamiento() { return ultimoProcesamiento; }
    }
}