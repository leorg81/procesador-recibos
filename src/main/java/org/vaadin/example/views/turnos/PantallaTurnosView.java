package org.vaadin.example.views.turnos;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.dialog.DialogVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.dom.ThemeList;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.Theme;
import org.vaadin.example.data.*;
import org.vaadin.example.security.SecurityService;
import org.vaadin.example.services.LocalidadService;
import org.vaadin.example.services.NoticiaService;
import org.vaadin.example.services.TurnoService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Timer;
import java.util.TimerTask;

@Push
@Route("turnos/:localidadId")
@PageTitle("Pantalla de Turnos")
@CssImport("./styles/views/pantalla-turnos-view.css")
public class PantallaTurnosView extends VerticalLayout {

    private final SecurityService securityService;
    private final Grid<Turno> turnoGrid = new Grid<>(Turno.class, false);
    private final H1 titulo = new H1("Turnos");
    private final Label fechaHoraLabel = new Label();
    private final Button cambiarLocalidadButton = new Button(VaadinIcon.EXCHANGE.create());
    private final TurnoService turnoService;
    private List<Turno> turnosCacheados = new ArrayList<>();
    private final LocalidadService localidadService;
    private final NoticiaService noticiaService;
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final HorizontalLayout carruselLayout = new HorizontalLayout(); // Carrusel de imágenes
    private final int CARRUSEL_HEIGHT = 300; // Tamaño del carrusel en píxeles (ajustable)
    private List<Noticia> noticias;
    private int currentIndex = 0;
    private static final int NOTICIAS_POR_PASO = 3;
    private WeakReference<UI> uiRef = new WeakReference<>(UI.getCurrent());
    private Localidad localidad;
    private List<Sector> sectoresSeleccionados = new ArrayList<Sector>();
    private final Dialog dialogTurnosLlamando = new Dialog();


    public PantallaTurnosView(SecurityService securityService, TurnoService turnoService, LocalidadService localidadService, NoticiaService noticiaService) {
        this.securityService = securityService;
        this.noticiaService = noticiaService;
        this.turnoService = turnoService;
        this.localidadService = localidadService;

        Long localidadId = (Long) UI.getCurrent().getSession().getAttribute("localidadId");
        sectoresSeleccionados = (ArrayList<Sector>) VaadinSession.getCurrent().getAttribute("sectoresSeleccionados");

        if (localidadId != null) {
            this.localidad = localidadService.findById(localidadId).orElse(null);
            if (this.localidad != null) {
                titulo.setText("Turnos - " + localidad.getNombre() );
                refreshTurnoGrid(localidad);
            } else {
                titulo.setText("Turnos");
            }
        } else {
            titulo.setText("Turnos");
        }

        setSizeFull();
        addClassName("pantalla-turnos-view");

        configureFechaHora();
        configureGrid();
        updateCarrusel();
        configureBotonCambioLocalidad();

        VerticalLayout header = new VerticalLayout(new Div(titulo, fechaHoraLabel));
        header.setAlignItems(Alignment.CENTER);
        header.setHeight("150px"); // Puedes ajustar el valor según lo que necesites
        header.getStyle().set("padding", "5px 0");
        add(header, turnoGrid, carruselLayout, cambiarLocalidadButton);

        // Programar las tareas
        programarTareas();
    }


    private void scheduleTask(Runnable task, long initialDelay, long period, TimeUnit unit) {
        executorService.scheduleAtFixedRate(() -> {
            try {
                UI ui = uiRef.get();
                if (ui != null && ui.getSession() !=null) {
                    ui.access((Command) () -> task.run());
                } else {
                    System.err.println("UI ya no está disponible.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, initialDelay, period, unit);
    }


    private void programarTareas() {
        scheduleTask(() -> refreshTurnoGrid(localidad), 0, 5, TimeUnit.SECONDS);
        scheduleTask(() -> updateCarrusel(), 0, 5, TimeUnit.MINUTES);
        scheduleTask(() -> configureFechaHora(), 0, 1, TimeUnit.MINUTES);
    }



    private void configureFechaHora() {
        fechaHoraLabel.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        fechaHoraLabel.getStyle().set("font-size","25px");
    }


    private void configureGrid() {
        turnoGrid.getStyle()
                .set("font-size", "25px") // Cambiar tamaño de la fuente
                .set("line-height", "2"); // Ajustar la altura de las filas

        turnoGrid.setHeight("600px"); // Ajustar la altura de la grilla
        turnoGrid.setWidth("100%"); //

        turnoGrid.addColumn(turno -> turno.getActuadoPor() != null ? turno.getActuadoPor().getPuesto() : "-")
                .setHeader(createStyledHeader("Puesto"))
                .setAutoWidth(true);

        turnoGrid.addColumn(Turno::getCodigoTurno)
                .setHeader(createStyledHeader("Código de Turno"))
                .setAutoWidth(true);

        turnoGrid.addColumn(Turno::getEstado)
                .setHeader(createStyledHeader("Estado"))
                .setAutoWidth(true);

        turnoGrid.addColumn(turno -> turno.getFechaHora() != null
                        ? turno.getFechaHora().format(DateTimeFormatter.ofPattern("HH:mm"))
                        : "Sin hora")
                .setHeader(createStyledHeader("Hora de Creación"))
                .setAutoWidth(true);

        // Aplicar la clase dinámicamente sólo para las filas con el estado "LLAMANDO"
        turnoGrid.setClassNameGenerator(turno ->
                EstadoTurno.LLAMANDO.equals(turno.getEstado()) ? "llamando-row" : null
        );

        turnoGrid.setSizeFull(); // Establecer tamaño completo en la pantalla
    }



    private Component createStyledHeader(String text) {
        Span header = new Span(text);
        header.getElement().getStyle()
                .set("font-size", "25px") // Cambiar tamaño del texto
                .set("color", "black")     // Color del texto
                .set("height", "30px");   // Altura del header
        return header;
    }

    private void configureBotonCambioLocalidad() {
        cambiarLocalidadButton.addClickListener(event -> UI.getCurrent().navigate("turnos/pantallas"));
        cambiarLocalidadButton.addClassName("floating-button");
        cambiarLocalidadButton.getStyle()
                .set("position", "fixed")
                .set("bottom", "1em")
                .set("right", "1em")
                .set("border-radius", "50%")
                .set("width", "50px")
                .set("height", "50px");
    }


    private void refreshTurnoGrid(Localidad localidadSeleccionada) {
        try {
            if (localidadSeleccionada != null) {
                turnosCacheados = turnoService.findTurnosDelDiaByLocalidadAndEstados(
                        localidadSeleccionada, EstadoTurno.EN_ESPERA, EstadoTurno.LLAMANDO
                );

                List<Turno> turnosFiltrados = turnosCacheados.stream()
                        .filter(turno -> sectoresSeleccionados.contains(turno.getSector()))
                        .collect(Collectors.toList());

                turnoGrid.setItems(turnosFiltrados);
                List<Turno> turnosEnLlamando = turnosFiltrados.stream()
                        .filter(turno -> EstadoTurno.LLAMANDO.equals(turno.getEstado()))
                        .collect(Collectors.toList());

                if (!turnosEnLlamando.isEmpty()) {
                    openDialogLlamando(turnosEnLlamando);
                }


            } else {
                turnoGrid.setItems(Collections.emptyList());
            }
        } catch (Exception e) {
            Notification.show("Error al actualizar los turnos: " + e.getMessage(), 5000, Notification.Position.BOTTOM_START);
            e.printStackTrace();
        }
    }

    private void updateCarrusel() {
        carruselLayout.setWidthFull();
        carruselLayout.getStyle().set("overflow", "hidden");
        carruselLayout.removeAll();
        // Cargar noticias activas desde el servicio
        try {
            noticias = noticiaService.findNoticiasActivas();
        } catch (Exception e) {
            Notification.show("Error al cargar noticias: " + e.getMessage(), 3000, Notification.Position.BOTTOM_START);
            return;
        }

        if (noticias != null && !noticias.isEmpty()) {
            // Mostrar solo las noticias correspondientes al índice actual
            noticias.stream()
                    .skip(currentIndex * NOTICIAS_POR_PASO) // Saltar las noticias ya mostradas
                    .limit(NOTICIAS_POR_PASO) // Mostrar las siguientes 3
                    .forEach(noticia -> carruselLayout.add(createCarruselItem(noticia)));

            // Ajustar el índice para la próxima rotación
            currentIndex = (currentIndex + 1) % ((noticias.size() + NOTICIAS_POR_PASO - 1) / NOTICIAS_POR_PASO);
        } else {
            carruselLayout.add(new Label("No hay noticias activas para mostrar."));
        }
    }

    private Component createCarruselItem(Noticia noticia) {
        // Crear la imagen con el recurso proporcionado
        Image image = new Image(
                new StreamResource(
                        noticia.getDescripcion(),
                        () -> new ByteArrayInputStream(noticia.getFoto())
                ),
                noticia.getDescripcion()
        );

        // Configurar tamaño de la imagen para que ocupe todo el ancho del contenedor
        image.setWidth("100%"); // Hacer que la imagen se ajuste al 100% del ancho
        image.setHeight("300px"); // Definir una altura fija para el carrusel
        image.getStyle().set("object-fit", "cover"); // Mantener la proporción y ajustarse al contenedor

        Div container = new Div(image);

        // Estilizar el contenedor
        container.setWidth("100%");
        container.getStyle().set("margin", "0");

        return container;
    }

    private void openDialogLlamando(List<Turno> turnosEnLlamando) {
        dialogTurnosLlamando.removeAll(); // Limpiar cualquier contenido anterior

        dialogTurnosLlamando.addThemeVariants(DialogVariant.LUMO_NO_PADDING);

        dialogTurnosLlamando.setWidth("600px");
        //dialogTurnosLlamando.setHeight("400px");

        try {
            UI ui = uiRef.get();
            if (ui != null && ui.getSession() !=null) {

                ui.getCurrent().getPage().executeJs(
                        "const audio = new Audio('dingdong.wav'); audio.play();"
                );
            } else {
                System.err.println("UI ya no está disponible.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }



        // Crear un contenedor para los turnos en estado llamando
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);

        Label encabezado = new Label("Atención llamando!!");
        encabezado.addClassName("dialog-header");

        // Agregar el encabezado al diálogo
        dialogTurnosLlamando.add(encabezado);

        // Agregar una línea para cada turno
        for (Turno turno : turnosEnLlamando) {
            Label lineaTurno = new Label(
                    String.format("Turno: %s        Puesto: %s",
                            turno.getCodigoTurno(), turno.getActuadoPor().getPuesto())
            );
            lineaTurno.getStyle().set("color", "darkblue").set("font-size", "25px");
            layout.add(lineaTurno);
        }

        // Agregar el layout al diálogo
        dialogTurnosLlamando.add(layout);
        dialogTurnosLlamando.open();

        // Usar java.util.Timer para cerrar el diálogo después de 3 segundos
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                UI ui = uiRef.get();
                if (ui != null && ui.getSession() != null) {
                    uiRef.get().access(() -> dialogTurnosLlamando.close());

                }
            }
        }, 3000); // Retraso de 3 segundos
    }



    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        executorService.shutdown();
    }
    @PostConstruct
    public void init() {
        if (!securityService.hasRole("GENERATOR")) {
            UI.getCurrent().navigate("access-denied"); // Redirigir si no tiene acceso
        }
    }

    @PreDestroy
    private void cleanUp() {
        executorService.shutdownNow(); // Garantizar cierre incluso si onDetach no es llamado
    }


}

