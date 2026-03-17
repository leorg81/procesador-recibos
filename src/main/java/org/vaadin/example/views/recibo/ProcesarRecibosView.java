package org.vaadin.example.views.recibo;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.RolesAllowed;
import org.vaadin.example.data.Localidad;
import org.vaadin.example.data.ReciboProcesado;
import org.vaadin.example.data.ReciboProcesadoRepository;
import org.vaadin.example.data.TipoRecibo;
import org.vaadin.example.services.LocalidadService;
import org.vaadin.example.services.ProcesadorPdfService;
import org.vaadin.example.services.TipoReciboService;
import org.vaadin.example.views.MainLayout;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@PageTitle("Procesar Liquidaciones")
@Route(value = "procesar-recibos", layout = MainLayout.class)
@Menu(order = 2, icon = LineAwesomeIconUrl.TICKET_ALT_SOLID)
@RolesAllowed({"ADMIN", "LIQUIDACIONES"})
public class ProcesarRecibosView extends VerticalLayout {

    private final ProcesadorPdfService procesadorService;
    private final ReciboProcesadoRepository repository;
    private final LocalidadService localidadService;
    private final TipoReciboService tipoReciboService;

    // Componentes del formulario
    private final ComboBox<Localidad> comboLocalidad = new ComboBox<>("Localidad");
    private final ComboBox<TipoRecibo> comboTipoRecibo = new ComboBox<>("Tipo de Recibo");
    private final ComboBox<String> comboMes = new ComboBox<>("Mes");
    private final ComboBox<String> comboAnio = new ComboBox<>("Año");
    private final MemoryBuffer buffer = new MemoryBuffer();
    private final Upload upload = new Upload(buffer);
    private final Button btnProcesar = new Button("Procesar Recibos", new Icon(VaadinIcon.PLAY));

    // Grids
    private final Grid<ProcesoDTO> gridProcesos = new Grid<>();
    private final Grid<ReciboDetalleDTO> gridDetalles = new Grid<>();

    // Estado
    private String archivoSubido = null;

    public ProcesarRecibosView(ProcesadorPdfService procesadorService,
                               ReciboProcesadoRepository repository,
                               LocalidadService localidadService,
                               TipoReciboService tipoReciboService) {
        this.procesadorService = procesadorService;
        this.repository = repository;
        this.localidadService = localidadService;
        this.tipoReciboService = tipoReciboService;

        setPadding(true);
        setSpacing(true);
        setSizeFull();

        // Configurar componentes
        configurarFormulario();
        configurarGrids();

        // Crear layout en 3 secciones verticales
        crearLayoutTresSecciones();

        // Cargar datos iniciales
        cargarProcesos();
    }

    private void configurarFormulario() {
        // Configurar localidades
        comboLocalidad.setItems(localidadService.listarTodas());
        comboLocalidad.setItemLabelGenerator(localidad ->
                localidad.getNombre() + " (" + localidad.getCodigo() + ")"
        );
        comboLocalidad.setPlaceholder("Seleccione localidad");
        comboLocalidad.setWidth("100%");

        // Configurar tipos de recibo
        comboTipoRecibo.setItems(tipoReciboService.listarTodos());
        comboTipoRecibo.setItemLabelGenerator(TipoRecibo::getNombre);
        comboTipoRecibo.setPlaceholder("Seleccione tipo de recibo");
        comboTipoRecibo.setWidth("100%");

        // Configurar meses
        comboMes.setItems(
                "01 - Enero", "02 - Febrero", "03 - Marzo",
                "04 - Abril", "05 - Mayo", "06 - Junio",
                "07 - Julio", "08 - Agosto", "09 - Septiembre",
                "10 - Octubre", "11 - Noviembre", "12 - Diciembre"
        );
        comboMes.setPlaceholder("Seleccione mes");
        comboMes.setWidth("100%");

        // Configurar años (últimos 5 años y próximo)
        int anioActual = LocalDateTime.now().getYear();
        String[] anios = new String[6];
        for (int i = 0; i < 6; i++) {
            anios[i] = String.valueOf(anioActual - i + 1);
        }
        comboAnio.setItems(anios);
        comboAnio.setPlaceholder("Seleccione año");
        comboAnio.setValue(String.valueOf(anioActual));
        comboAnio.setWidth("100%");

        // Configurar upload
        configurarUpload();

        // Configurar botón procesar
        btnProcesar.getElement().setAttribute("theme", "primary success");
        btnProcesar.setWidthFull();
        btnProcesar.addClickListener(e -> procesarArchivo());
        btnProcesar.setEnabled(false);

        // Habilitar botón solo cuando todos los campos estén completos
        Runnable validarCampos = () -> {
            boolean camposCompletos = !comboLocalidad.isEmpty() &&
                    !comboTipoRecibo.isEmpty() &&
                    !comboMes.isEmpty() &&
                    !comboAnio.isEmpty() &&
                    archivoSubido != null;
            btnProcesar.setEnabled(camposCompletos);
        };

        comboLocalidad.addValueChangeListener(e -> validarCampos.run());
        comboTipoRecibo.addValueChangeListener(e -> validarCampos.run());
        comboMes.addValueChangeListener(e -> validarCampos.run());
        comboAnio.addValueChangeListener(e -> validarCampos.run());
    }

    private void configurarUpload() {
        upload.setAcceptedFileTypes(".pdf", "application/pdf");
        upload.setMaxFileSize(100 * 1024 * 1024); // 100MB
        upload.setDropLabel(new Span("Arrastra el archivo PDF aquí"));
        upload.setUploadButton(new Button("Seleccionar archivo"));
        upload.setWidthFull();

        // Limpiar el label de upload
        upload.getElement().executeJs("""
        this.shadowRoot.querySelector('vaadin-upload-file-list').style.display = 'none';
    """);

        upload.addStartedListener(event -> {
            // Resetear estado anterior
            archivoSubido = null;
            btnProcesar.setEnabled(false);
        });

        upload.addSucceededListener(event -> {
            archivoSubido = event.getFileName();

            // Mostrar nombre del archivo
            Notification.show("✅ Archivo listo: " + archivoSubido,
                    2000, Notification.Position.BOTTOM_END);

            // Validar campos
            btnProcesar.setEnabled(!comboLocalidad.isEmpty() &&
                    !comboTipoRecibo.isEmpty() &&
                    !comboMes.isEmpty() &&
                    !comboAnio.isEmpty());
        });

        upload.addFileRejectedListener(event -> {
            Notification.show("❌ " + event.getErrorMessage(),
                    3000, Notification.Position.MIDDLE);
            archivoSubido = null;
            btnProcesar.setEnabled(false);
        });

        upload.addFailedListener(event -> {
            Notification.show("❌ Error al subir archivo",
                    3000, Notification.Position.MIDDLE);
            archivoSubido = null;
            btnProcesar.setEnabled(false);
        });
    }

    private void resetearUpload() {
        archivoSubido = null;
        // Resetear el componente Upload
        upload.getElement().executeJs("""
        this.files = [];
        this.clearFiles();
        const event = new Event('change', {bubbles: true});
        this.dispatchEvent(event);
    """);
        btnProcesar.setEnabled(false);
    }

    private void configurarGrids() {
        // Configurar grid de procesos
        gridProcesos.addColumn(ProcesoDTO::getNombreProceso)
                .setHeader("Proceso")
                .setAutoWidth(true)
                .setFlexGrow(0);

        gridProcesos.addColumn(ProcesoDTO::getCantidadRecibos)
                .setHeader("Recibos")
                .setAutoWidth(true)
                .setFlexGrow(0);

        gridProcesos.addColumn(ProcesoDTO::getFechaProcesamiento)
                .setHeader("Procesado")
                .setAutoWidth(true)
                .setFlexGrow(0)
                .setSortable(true);

        // Columna de acciones para procesos
        gridProcesos.addComponentColumn(proceso -> {
            HorizontalLayout acciones = new HorizontalLayout();
            acciones.setSpacing(true);

            Button verDetalles = new Button("Ver", new Icon(VaadinIcon.EYE));
            verDetalles.getElement().setAttribute("theme", "small primary");
            verDetalles.addClickListener(e -> cargarDetallesProceso(proceso));
            verDetalles.setTooltipText("Ver recibos de este proceso");

            Button eliminar = new Button(new Icon(VaadinIcon.TRASH));
            eliminar.getElement().setAttribute("theme", "small error icon");
            eliminar.addClickListener(e -> eliminarProceso(proceso));
            eliminar.setTooltipText("Eliminar proceso");

            acciones.add(verDetalles, eliminar);
            return acciones;
        }).setHeader("Acciones").setAutoWidth(true).setFlexGrow(0);

        gridProcesos.setSizeFull();
        gridProcesos.setSelectionMode(Grid.SelectionMode.SINGLE);

        // Cuando se selecciona un proceso, cargar sus detalles
        gridProcesos.addSelectionListener(event -> {
            if (event.getFirstSelectedItem().isPresent()) {
                ProcesoDTO proceso = event.getFirstSelectedItem().get();
                cargarDetallesProceso(proceso);
            } else {
                gridDetalles.setItems(List.of());
            }
        });

        // Configurar grid de detalles
        gridDetalles.addColumn(ReciboDetalleDTO::getCi)
                .setHeader("Cédula")
                .setAutoWidth(true)
                .setFlexGrow(0);

        gridDetalles.addColumn(ReciboDetalleDTO::getNombreArchivo)
                .setHeader("Archivo PDF")
                .setAutoWidth(true);

        // Columna para acciones en detalles
        gridDetalles.addComponentColumn(detalle -> {
            Button ver = new Button("Ver PDF", new Icon(VaadinIcon.EYE));
            ver.getElement().setAttribute("theme", "small primary");
            ver.addClickListener(e -> mostrarRecibo(detalle));
            ver.setTooltipText("Ver recibo PDF");
            return ver;
        }).setHeader("Acción").setAutoWidth(true).setFlexGrow(0);

        gridDetalles.setSizeFull();
    }

    private void crearLayoutTresSecciones() {
        // SECCIÓN 1: FORMULARIO DE PROCESAMIENTO (arriba a lo ancho)
        VerticalLayout seccionFormulario = crearSeccionFormulario();

        // SECCIÓN 2: PROCESOS EXISTENTES (mitad izquierda)
        VerticalLayout seccionProcesos = crearSeccionProcesos();

        // SECCIÓN 3: DETALLES DEL PROCESO SELECCIONADO (mitad derecha)
        VerticalLayout seccionDetalles = crearSeccionDetalles();

        // Layout horizontal para las dos grillas (secciones 2 y 3)
        HorizontalLayout seccionGrillas = new HorizontalLayout(seccionProcesos, seccionDetalles);
        seccionGrillas.setSizeFull();
        seccionGrillas.setSpacing(true);
        seccionGrillas.setPadding(false);
        seccionGrillas.setFlexGrow(1, seccionProcesos); // 50%
        seccionGrillas.setFlexGrow(1, seccionDetalles); // 50%

        // Layout principal vertical
        add(seccionFormulario, seccionGrillas);
        setFlexGrow(1, seccionGrillas); // Las grillas ocupan el espacio restante
    }

    private VerticalLayout crearSeccionFormulario() {
        VerticalLayout seccion = new VerticalLayout();
        seccion.setSpacing(true);
        seccion.setPadding(true);
        seccion.getStyle()
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "8px")
                .set("background", "white")
                .set("margin-bottom", "1rem");
        seccion.setWidthFull();

        H3 titulo = new H3("📤 Nuevo Proceso de Liquidación");
        titulo.getStyle()
                .set("margin-top", "0")
                .set("margin-bottom", "1rem")
                .set("color", "var(--lumo-primary-text-color)");

        // Layout para combos en 2 filas
        // Fila 1: Localidad y Tipo de Recibo
        HorizontalLayout fila1 = new HorizontalLayout(comboLocalidad, comboTipoRecibo);
        fila1.setSpacing(true);
        fila1.setWidthFull();
        fila1.setFlexGrow(1, comboLocalidad);
        fila1.setFlexGrow(1, comboTipoRecibo);

        // Fila 2: Mes, Año y Upload
        HorizontalLayout fila2 = new HorizontalLayout(comboMes, comboAnio, upload);
        fila2.setSpacing(true);
        fila2.setWidthFull();
        fila2.setFlexGrow(0.3, comboMes);
        fila2.setFlexGrow(0.3, comboAnio);
        fila2.setFlexGrow(1, upload);

        // Fila 3: Botón procesar
        HorizontalLayout fila3 = new HorizontalLayout(btnProcesar);
        fila3.setWidthFull();
        fila3.setJustifyContentMode(JustifyContentMode.END);

        seccion.add(titulo, fila1, fila2, fila3);
        return seccion;
    }

    private VerticalLayout crearSeccionProcesos() {
        VerticalLayout seccion = new VerticalLayout();
        seccion.setSpacing(true);
        seccion.setPadding(true);
        seccion.getStyle()
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "8px")
                .set("background", "white");
        seccion.setSizeFull();

        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "center")
                .set("width", "100%");

        H3 titulo = new H3("📋 Procesos Existentes");
        titulo.getStyle()
                .set("margin", "0")
                .set("color", "var(--lumo-primary-text-color)");

        Span contador = new Span();
        contador.getStyle()
                .set("font-size", "0.875rem")
                .set("color", "var(--lumo-secondary-text-color)");

        header.add(titulo, contador);

        // Actualizar contador cuando cambien los procesos
        gridProcesos.getDataProvider().addDataProviderListener(event -> {
            long total = gridProcesos.getDataProvider().size(new Query<>());
            contador.setText(total + " procesos");
        });

        seccion.add(header, gridProcesos);
        return seccion;
    }

    private VerticalLayout crearSeccionDetalles() {
        VerticalLayout seccion = new VerticalLayout();
        seccion.setSpacing(true);
        seccion.setPadding(true);
        seccion.getStyle()
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "8px")
                .set("background", "white");
        seccion.setSizeFull();

        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "center")
                .set("width", "100%");

        H3 titulo = new H3("📄 Recibos del Proceso");
        titulo.getStyle()
                .set("margin", "0")
                .set("color", "var(--lumo-primary-text-color)");

        Span contador = new Span();
        contador.getStyle()
                .set("font-size", "0.875rem")
                .set("color", "var(--lumo-secondary-text-color)");

        // Botón para limpiar selección
        Button btnLimpiar = new Button("Limpiar", new Icon(VaadinIcon.CLOSE_SMALL));
        btnLimpiar.getElement().setAttribute("theme", "small tertiary");
        btnLimpiar.setVisible(false);
        btnLimpiar.addClickListener(e -> {
            gridProcesos.deselectAll();
            contador.setText("0 recibos");
            btnLimpiar.setVisible(false);
        });

        header.add(titulo, contador, btnLimpiar);

        // Actualizar contador cuando cambien los detalles
        gridDetalles.getDataProvider().addDataProviderListener(event -> {
            long total = gridDetalles.getDataProvider().size(new Query<>());
            contador.setText(total + " recibos");
            btnLimpiar.setVisible(total > 0);
        });

        // Cuando se selecciona un proceso, mostrar el botón limpiar
        gridProcesos.addSelectionListener(event -> {
            btnLimpiar.setVisible(event.getFirstSelectedItem().isPresent());
        });

        seccion.add(header, gridDetalles);
        return seccion;
    }

    private void cargarDetallesProceso(ProcesoDTO proceso) {
        List<ReciboDetalleDTO> detalles = repository
                .findByLocalidadCodigoAndTipoAndMesAnio(
                        proceso.getLocalidadCodigo(),
                        proceso.getTipo(),
                        proceso.getMesAnio()
                ).stream()
                .map(ReciboDetalleDTO::new)
                .sorted(Comparator.comparing(ReciboDetalleDTO::getApellidos)
                        .thenComparing(ReciboDetalleDTO::getNombres))
                .collect(Collectors.toList());

        gridDetalles.setItems(detalles);

        // Resaltar visualmente que se cargaron detalles
        Notification.show("📂 Cargados " + detalles.size() + " recibos de " +
                proceso.getNombreProceso(), 2000, Notification.Position.BOTTOM_START);
    }


    private void configurarGridDetalles() {
        gridDetalles.addColumn(ReciboDetalleDTO::getCi)
                .setHeader("CI")
                .setAutoWidth(true);


        gridDetalles.addColumn(ReciboDetalleDTO::getNombreArchivo)
                .setHeader("Archivo")
                .setAutoWidth(true);

        // Columna para ver recibo
        gridDetalles.addComponentColumn(detalle -> {
            Button ver = new Button("Ver", new Icon(VaadinIcon.EYE));
            ver.getElement().setAttribute("theme", "small primary");
            ver.addClickListener(e -> mostrarRecibo(detalle));
            ver.setTooltipText("Ver recibo PDF");
            return ver;
        }).setHeader("Acción").setAutoWidth(true);

        gridDetalles.setWidth("100%");
        gridDetalles.setHeight("400px");
        gridDetalles.setVisible(false);
    }

    private void crearLayout() {
        // Panel de formulario (izquierda - 40%)
        VerticalLayout panelFormulario = new VerticalLayout();
        panelFormulario.setSpacing(true);
        panelFormulario.setPadding(true);
        panelFormulario.getStyle()
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "8px")
                .set("background", "white");
        panelFormulario.setWidth("40%");

        H3 tituloForm = new H3("Nuevo Proceso de Liquidación");
        tituloForm.getStyle()
                .set("margin-top", "0")
                .set("color", "var(--lumo-primary-text-color)");

        panelFormulario.add(
                tituloForm,
                comboLocalidad,
                comboTipoRecibo,
                new HorizontalLayout(comboMes, comboAnio),
                upload,
                btnProcesar
        );

        // Panel de procesos (centro - 35%)
        VerticalLayout panelProcesos = new VerticalLayout();
        panelProcesos.setSpacing(true);
        panelProcesos.setPadding(false);
        panelProcesos.setWidth("35%");

        H3 tituloProcesos = new H3("Procesos Existentes");
        tituloProcesos.getStyle()
                .set("margin-top", "0")
                .set("color", "var(--lumo-primary-text-color)");

        panelProcesos.add(tituloProcesos, gridProcesos);

        // Panel de detalles (derecha - 25%)
        VerticalLayout panelDetalles = new VerticalLayout();
        panelDetalles.setSpacing(true);
        panelDetalles.setPadding(false);
        panelDetalles.setWidth("25%");
        panelDetalles.setVisible(false);

        Div tituloDetallesContainer = new Div();
        tituloDetallesContainer.getStyle()
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "center");

        H3 tituloDetalles = new H3("Detalles");
        Span contador = new Span();
        contador.getStyle()
                .set("font-size", "0.875rem")
                .set("color", "var(--lumo-secondary-text-color)");

        tituloDetallesContainer.add(tituloDetalles, contador);
        panelDetalles.add(tituloDetallesContainer, gridDetalles);

        // Cuando se selecciona un proceso, actualizar detalles
        gridProcesos.addSelectionListener(event -> {
            if (event.getFirstSelectedItem().isPresent()) {
                ProcesoDTO proceso = event.getFirstSelectedItem().get();
                cargarDetallesProceso(proceso);
                contador.setText(proceso.getCantidadRecibos() + " recibos");
                panelDetalles.setVisible(true);
            } else {
                panelDetalles.setVisible(false);
            }
        });

        // Layout principal horizontal
        HorizontalLayout layoutPrincipal = new HorizontalLayout();
        layoutPrincipal.setSpacing(true);
        layoutPrincipal.setPadding(false);
        layoutPrincipal.setSizeFull();
        layoutPrincipal.add(panelFormulario, panelProcesos, panelDetalles);

        add(new H2("📋 Procesar Liquidaciones"), layoutPrincipal);
    }

    private void procesarArchivo() {
        if (comboLocalidad.isEmpty() || comboTipoRecibo.isEmpty() ||
                comboMes.isEmpty() || comboAnio.isEmpty() || archivoSubido == null) {
            Notification.show("❌ Completa todos los campos y sube un archivo",
                    3000, Notification.Position.MIDDLE);
            return;
        }

        try {
            InputStream inputStream = buffer.getInputStream();

            // IMPORTANTE: Verificar que el InputStream tenga datos
            if (inputStream.available() <= 0) {
                Notification.show("❌ El archivo está vacío o no se pudo leer",
                        3000, Notification.Position.MIDDLE);
                return;
            }

            // Extraer datos del formulario
            Localidad localidad = comboLocalidad.getValue();
            TipoRecibo tipoRecibo = comboTipoRecibo.getValue();
            String mes = comboMes.getValue().substring(0, 2);
            String anio = comboAnio.getValue();

            // Crear nombre del proceso
            String nombreProceso = String.format("%s-%s-%s%s",
                    localidad.getCodigo(),
                    tipoRecibo.getCodigo(),
                    mes, anio);

            // Mostrar progreso
            Notification.show("⏳ Procesando archivo... Esto puede tomar unos momentos",
                    5000, Notification.Position.MIDDLE);

            btnProcesar.setEnabled(false);
            btnProcesar.setText("Procesando...");

            // Procesar el archivo de forma síncrona (ya que PDFBox bloquea el hilo)
            procesadorService.procesarRecibos(
                    inputStream,
                    localidad.getCodigo(),
                    tipoRecibo,
                    mes,
                    anio,
                    "recibos_procesados/" + nombreProceso
            );

            // Contar recibos generados
            long recibosGenerados = repository.countByLocalidadCodigoAndTipoAndMesAnio(
                    localidad.getCodigo(),
                    tipoRecibo.getCodigo(),
                    mes + anio
            );

            // Limpiar formulario
            comboLocalidad.clear();
            comboTipoRecibo.clear();
            comboMes.clear();
            comboAnio.clear();
            resetearUpload();
            archivoSubido = null;

            // Resetear el upload
            upload.getElement().executeJs("""
            this.files = [];
            this.dispatchEvent(new Event('change', {bubbles: true}));
        """);

            // Actualizar UI
            Notification.show(String.format(
                    "✅ Procesado exitosamente: %d recibos generados",
                    recibosGenerados
            ), 5000, Notification.Position.MIDDLE);


            cargarProcesos();

        } catch (Exception e) {
            Notification.show("❌ Error procesando archivo: " + e.getMessage(),
                    5000, Notification.Position.MIDDLE);
            e.printStackTrace();
        } finally {
            btnProcesar.setEnabled(true);
            btnProcesar.setText("Procesar Recibos");
        }
    }

    private void cargarProcesos() {
        List<ProcesoDTO> procesos = repository.findAll().stream()
                .filter(r -> r.getLocalidadCodigo() != null &&
                        r.getTipo() != null &&
                        r.getMesAnio() != null)
                .collect(Collectors.groupingBy(r ->
                        r.getLocalidadCodigo() + "|" + r.getTipo() + "|" + r.getMesAnio()
                ))
                .entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|");
                    String localidadCodigo = parts[0];
                    String tipo = parts[1];
                    String mesAnio = parts[2];

                    List<ReciboProcesado> recibos = entry.getValue();

                    // Buscar nombre de localidad
                    String localidadNombre = localidadService.listarTodas().stream()
                            .filter(l -> l.getCodigo().equals(localidadCodigo))
                            .map(Localidad::getNombre)
                            .findFirst()
                            .orElse(localidadCodigo);

                    // Buscar nombre del tipo de recibo
                    String tipoNombre = tipoReciboService.listarTodos().stream()
                            .filter(t -> t.getCodigo().equals(tipo))
                            .map(TipoRecibo::getNombre)
                            .findFirst()
                            .orElse(tipo);

                    // Fecha del último procesamiento
                    LocalDateTime ultimaFecha = recibos.stream()
                            .map(ReciboProcesado::getProcesadoEn)
                            .filter(d -> d != null)
                            .max(Comparator.naturalOrder())
                            .orElse(null);

                    return new ProcesoDTO(
                            localidadCodigo,
                            localidadNombre,
                            tipo,
                            tipoNombre,
                            mesAnio,
                            recibos.size(),
                            ultimaFecha
                    );
                })
                .sorted((a, b) -> {
                    // Ordenar por fecha descendente
                    if (a.getFechaProcesamiento() == null && b.getFechaProcesamiento() == null) return 0;
                    if (a.getFechaProcesamiento() == null) return 1;
                    if (b.getFechaProcesamiento() == null) return -1;
                    return b.getFechaProcesamiento().compareTo(a.getFechaProcesamiento());
                })
                .collect(Collectors.toList());

        gridProcesos.setItems(procesos);
    }

    /*
    private void cargarDetallesProceso(ProcesoDTO proceso) {
        List<ReciboDetalleDTO> detalles = repository
                .findByLocalidadCodigoAndTipoAndMesAnio(
                        proceso.getLocalidadCodigo(),
                        proceso.getTipo(),
                        proceso.getMesAnio()
                ).stream()
                .map(ReciboDetalleDTO::new)
                .collect(Collectors.toList());

        gridDetalles.setItems(detalles);
    }*/

    private void eliminarProceso(ProcesoDTO proceso) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setModal(true);
        confirmDialog.setHeaderTitle("Confirmar eliminación");

        VerticalLayout contenido = new VerticalLayout();
        contenido.setSpacing(true);
        contenido.setPadding(true);

        contenido.add(new Paragraph("¿Eliminar este proceso?"));
        contenido.add(new Paragraph("Proceso: " + proceso.getNombreProceso()));
        contenido.add(new Paragraph("Recibos: " + proceso.getCantidadRecibos() + " archivos"));

        HorizontalLayout botones = new HorizontalLayout();
        botones.setSpacing(true);

        Button confirmar = new Button("Eliminar", new Icon(VaadinIcon.TRASH));
        confirmar.getElement().setAttribute("theme", "error primary");
        confirmar.addClickListener(e -> {
            // Obtener todos los recibos del proceso
            List<ReciboProcesado> recibos = repository
                    .findByLocalidadCodigoAndTipoAndMesAnio(
                            proceso.getLocalidadCodigo(),
                            proceso.getTipo(),
                            proceso.getMesAnio()
                    );

            // Eliminar archivos físicos
            recibos.forEach(recibo -> {
                if (recibo.getRutaArchivo() != null) {
                    try {
                        File archivo = new File(recibo.getRutaArchivo());
                        if (archivo.exists()) {
                            archivo.delete();
                        }
                    } catch (Exception ex) {
                        // Continuar aunque falle algún archivo
                    }
                }
            });

            // Eliminar de la base de datos
            repository.deleteAll(recibos);

            // Intentar eliminar el directorio si está vacío
            try {
                String dirPath = "recibos_procesados/" + proceso.getLocalidadCodigo() +
                        "-" + proceso.getTipo() + "-" + proceso.getMesAnio();
                File directorio = new File(dirPath);
                if (directorio.exists() && directorio.listFiles().length == 0) {
                    directorio.delete();
                }
            } catch (Exception ex) {
                // Ignorar error al eliminar directorio
            }

            confirmDialog.close();
            Notification.show("✅ Proceso eliminado: " + proceso.getNombreProceso(),
                    3000, Notification.Position.MIDDLE);

            cargarProcesos();
            gridDetalles.setVisible(false);
        });

        Button cancelar = new Button("Cancelar");
        cancelar.addClickListener(e -> confirmDialog.close());

        botones.add(confirmar, cancelar);
        contenido.add(botones);

        confirmDialog.add(contenido);
        confirmDialog.open();
    }

    private void mostrarRecibo(ReciboDetalleDTO detalle) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setWidth("600px");
        dialog.setHeaderTitle("Recibo: " + detalle.getNombreCompleto());

        VerticalLayout contenido = new VerticalLayout();
        contenido.setSpacing(true);
        contenido.setPadding(true);

        try {
            // Cargar archivo PDF
            byte[] data = Files.readAllBytes(Paths.get(detalle.getRutaArchivo()));

            // Crear recurso stream
            StreamResource resource = new StreamResource(
                    detalle.getNombreArchivo(),
                    () -> new ByteArrayInputStream(data)
            );

            // Opción A: Botón para descargar/abrir
            Button descargarBtn = new Button("Abrir recibo PDF", new Icon(VaadinIcon.DOWNLOAD));
            descargarBtn.getElement().setAttribute("theme", "primary");
            Anchor anchor = new Anchor(resource, "");
            anchor.getElement().setAttribute("download", true);
            anchor.getElement().setAttribute("target", "_blank");
            anchor.add(descargarBtn);

            // Opción B: Mostrar información del recibo
            Div info = new Div();
            info.setText("Recibo disponible para descarga:");
            info.getStyle().set("margin-bottom", "1rem");

            // Lista de detalles
            UnorderedList lista = new UnorderedList();
            lista.add(new ListItem("CI: " + detalle.getCi()));
            lista.add(new ListItem("Nombre: " + detalle.getNombreCompleto()));
            lista.add(new ListItem("Archivo: " + detalle.getNombreArchivo()));

            contenido.add(info, lista, anchor);

        } catch (IOException e) {
            contenido.add(new Paragraph("❌ Error al cargar el recibo: " + e.getMessage()));
        }

        Button cerrar = new Button("Cerrar", new Icon(VaadinIcon.CLOSE));
        cerrar.addClickListener(e -> dialog.close());

        contenido.add(cerrar);
        contenido.setHorizontalComponentAlignment(Alignment.END, cerrar);

        dialog.add(contenido);
        dialog.open();
    }

    // Clases DTO
    public static class ProcesoDTO {
        private final String localidadCodigo;
        private final String localidadNombre;
        private final String tipoCodigo;
        private final String tipoNombre;
        private final String mesAnio;
        private final int cantidadRecibos;
        private final LocalDateTime fechaProcesamiento;

        public ProcesoDTO(String localidadCodigo, String localidadNombre,
                          String tipoCodigo, String tipoNombre, String mesAnio,
                          int cantidadRecibos, LocalDateTime fechaProcesamiento) {
            this.localidadCodigo = localidadCodigo;
            this.localidadNombre = localidadNombre;
            this.tipoCodigo = tipoCodigo;
            this.tipoNombre = tipoNombre;
            this.mesAnio = mesAnio;
            this.cantidadRecibos = cantidadRecibos;
            this.fechaProcesamiento = fechaProcesamiento;
        }

        public String getLocalidadCodigo() { return localidadCodigo; }
        public String getTipo() { return tipoCodigo; }
        public String getMesAnio() { return mesAnio; }
        public int getCantidadRecibos() { return cantidadRecibos; }
        public LocalDateTime getFechaProcesamiento() { return fechaProcesamiento; }

        public String getNombreProceso() {
            return String.format("%s - %s - %s",
                    localidadNombre, tipoNombre, getMesAnioFormateado());
        }

        public String getMesAnioFormateado() {
            if (mesAnio != null && mesAnio.length() == 6) {
                String mes = mesAnio.substring(0, 2);
                String anio = mesAnio.substring(2);
                return mes + "/" + anio;
            }
            return mesAnio;
        }
    }

    public static class ReciboDetalleDTO {
        private final String ci;
        private final String nombres;
        private final String apellidos;
        private final String nombreArchivo;
        private final String rutaArchivo;

        public ReciboDetalleDTO(ReciboProcesado recibo) {
            this.ci = recibo.getCi();
            this.nombres = recibo.getNombres();
            this.apellidos = recibo.getApellidos();
            this.nombreArchivo = recibo.getNombreArchivo();
            this.rutaArchivo = recibo.getRutaArchivo();
        }

        public String getCi() { return ci; }
        public String getNombres() { return nombres != null ? nombres : ""; }
        public String getApellidos() { return apellidos != null ? apellidos : ""; }
        public String getNombreArchivo() { return nombreArchivo; }
        public String getRutaArchivo() { return rutaArchivo; }

        public String getNombreCompleto() {
            return (getNombres() + " " + getApellidos()).trim();
        }
    }
}