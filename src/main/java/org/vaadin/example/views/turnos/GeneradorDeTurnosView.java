package org.vaadin.example.views.turnos;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.example.data.GeneradorPrinterConfig;
import org.vaadin.example.data.Localidad;
import org.vaadin.example.data.Sector;
import org.vaadin.example.data.Turno;
import org.vaadin.example.services.GeneradorPrinterService;
import org.vaadin.example.security.SecurityService;
import org.vaadin.example.services.LocalidadService;
import org.vaadin.example.services.SectorService;
import org.vaadin.example.services.TurnoService;
import org.vaadin.example.views.MainView;


import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "turnos/generador", layout = MainView.class)
@PageTitle("Generador de Turnos")
@CssImport("./styles/views/generador-de-turnos-view.css")
@JavaScript("https://cdn.jsdelivr.net/npm/qz-tray@2.1.0/qz-tray.js")
@JavaScript("https://cdn.jsdelivr.net/npm/rsvp@4.8.5/dist/rsvp.min.js")
@JavaScript("https://cdnjs.cloudflare.com/ajax/libs/crypto-js/4.1.1/crypto-js.min.js")
@JavaScript("./print-utils.js")
public class GeneradorDeTurnosView extends VerticalLayout {

    private final TurnoService turnoService;
    private final LocalidadService localidadService;
    private final SectorService sectorService;
    private final GeneradorPrinterService printerService;
    private final SecurityService securityService;
    private final Label titleLabel = new Label("Seleccione localidad para generar turnos");

    @Autowired
    public GeneradorDeTurnosView(TurnoService turnoService,
                                 LocalidadService localidadService,
                                 SectorService sectorService,
                                 GeneradorPrinterService printerService,
                                 SecurityService securityService) {
        this.turnoService = turnoService;
        this.localidadService = localidadService;
        this.sectorService = sectorService;
        this.printerService = printerService;
        this.securityService = securityService;

        addClassName("generador-turnos-view");
        titleLabel.getStyle()
                .set("font-size", "24px")
                .set("font-weight", "bold");
        showLocations();
    }

    private void showLocations() {
        removeAll();
        List<Localidad> locations = localidadService.findAll();

        HorizontalLayout locationsLayout = new HorizontalLayout();
        locationsLayout.setSpacing(true);
        locationsLayout.setJustifyContentMode(JustifyContentMode.CENTER);

        locations.forEach(location -> {
            Button locationButton = new Button(location.getNombre());
            locationButton.addClassName("localidad-button");
            locationButton.addClickListener(event -> showSectors(location));
            locationsLayout.add(locationButton);
        });

        add(titleLabel, locationsLayout);
    }

    private void showSectors(Localidad location) {
        removeAll();
        List<Sector> sectors = sectorService.findByLocalidad(location);

        if (sectors.isEmpty()) {
            Notification.show("No hay sectores disponibles para esta localidad.");
            showLocations();
            return;
        }

        VerticalLayout sectorsLayout = new VerticalLayout();
        sectorsLayout.setSpacing(true);
        sectorsLayout.setDefaultHorizontalComponentAlignment(Alignment.CENTER);

        sectors.forEach(sector -> {
            Button sectorButton = new Button(sector.getNombre());
            sectorButton.addClassName("sector-button");
            sectorButton.addClickListener(event -> {
                try {
                    generateNewAppointment(location, sector);
                } catch (Exception e) {
                    Notification.show("Error al generar ticket: " + e.getMessage());
                }
            });
            sectorsLayout.add(sectorButton);
        });

        Button backButton = new Button("Volver", click -> showLocations());
        backButton.addClassName("volver-button");

        add(sectorsLayout, backButton);
    }

    private void generateNewAppointment(Localidad location, Sector sector) {
        Turno appointment = turnoService.crearTurno(location, sector);
        Notification.show("Turno generado: " + appointment.getCodigoTurno());

        // Obtener usuario actual
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // Verificar configuración de impresora
        GeneradorPrinterConfig config = printerService.getConfigByUser(username);

        if (config == null || config.getPrinterPath() == null) {
            Notification.show("Configure su impresora primero", 3000, Notification.Position.BOTTOM_CENTER);
            UI.getCurrent().navigate("configurar-impresora");
            return;
        }

        try {
            // Generar contenido del ticket
            String ticketContent = generateTicketContent(appointment);
            UI.getCurrent().getPage().executeJs("window.imprimirTicket($0)", ticketContent);
        } catch (Exception e) {
            Notification.show("Error al imprimir: " + e.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }


    private String generateTicketContent(Turno appointment) {
        // Configuración inicial
        StringBuilder sb = new StringBuilder()
                .append("\u001B@")        // Reset printer
                .append("\u001B!\u0010")  // Fuente tamaño aumentado (16x24 dots)
                .append("\u001Ba\u0001"); // Centrado habilitado (1 = center)

        // Líneas centradas
        sb.append("\n   TICKET DE TURNO\n\n")
                .append(centerLine("Fecha: " + appointment.getFechaHora()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))))
                .append("\n\n")
                .append("\u001B!\u0038")
                .append(centerLine("----------------"))
                .append(centerLine("|   " + centerText(appointment.getCodigoTurno(), 10) + "  |"))
                .append(centerLine("----------------"))
                .append("\n")
                .append("\u001B!\u0010")
                .append(("Localidad: " + appointment.getLocalidad().getNombre()))
                .append("\n")
                .append(("Sector: " + appointment.getSector().getNombre()))
                .append("\n")
                .append(centerLine("¡Gracias por su visita!"))

                // Reset alineación y corte
                .append("\u001Ba\u0000")    // Alineación izquierda (0 = left)
                .append("\u001DVA\u0000");   // Corte de papel

        return sb.toString();
    }

    private String centerLine(String text) {
        return text + "\n";
    }

    private String centerText(String text, int width) {
        int padding = (width - text.length()) / 2;
        padding = Math.max(padding, 0);
        return String.format("%" + (padding + text.length()) + "s", text);
    }
}