package org.vaadin.example.views.gestionDeTurnos;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.example.data.Turno;
import org.vaadin.example.data.User;
import org.vaadin.example.services.TurnoService;
import org.vaadin.example.services.UserService;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.util.Optional;

public class GestionTurnosView extends VerticalLayout {

    private final Grid<Turno> turnoGrid = new Grid<>(Turno.class, false);
    private final Button llamarSiguienteButton = new Button("Llamar Siguiente");
    private final Button marcarAtendidoButton = new Button("Marcar Atendido");
    private final Button marcarResueltoButton = new Button("Marcar Resuelto");
    private final TextField puestoTextField = new TextField("Puesto");
    private final TurnoService turnoService;
    private User currentUser;

    @PageTitle("Panel de Turnos")
    @Route("panelDeTurnos")
    @Menu(order = 6, icon = LineAwesomeIconUrl.CONCIERGE_BELL_SOLID)
    @RolesAllowed("USER")
    //@Uses(Icon.class)
    public GestionTurnosView(TurnoService turnoService, UserService userService) {
        this.turnoService = turnoService;

        // Obtener usuario autenticado
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            Notification.show("No estás autenticado. Por favor, inicia sesión.");
            return;
        }

        String username = authentication.getName();
        Optional<User> userOptional = userService.findByUsername(username);

        if (userOptional.isPresent()) {
            this.currentUser = userOptional.get();
        } else {
            Notification.show("No se encontró el usuario autenticado en la base de datos.");
            return;
        }

        verificarYPedirPuesto(userService);

        setupUI();
        refreshTurnoGrid();
    }

    private void setupUI() {
        setSizeFull();
        addClassName("gestion-turnos-view");

        // Configurar grid de turnos
        turnoGrid.addColumn(turno -> turno.getSector().getNombre()).setHeader("Sector").setAutoWidth(true);
        turnoGrid.addColumn(Turno::getFechaHora).setHeader("Fecha y Hora").setAutoWidth(true);
        turnoGrid.addColumn(Turno::getEstado).setHeader("Estado").setAutoWidth(true);
        turnoGrid.addColumn(Turno::getCodigoTurno).setHeader("Código").setAutoWidth(true);

        // Botón en cada fila para tomar turno aleatorio
        turnoGrid.addComponentColumn(turno -> {
            Button tomarTurnoButton = new Button("Tomar Turno");
            tomarTurnoButton.addClickListener(event -> tomarTurnoAleatorio(turno));
            return tomarTurnoButton;
        }).setHeader("Acciones");

        // Configuración de layout y botones
        puestoTextField.setWidthFull();
        HorizontalLayout infoLayout = new HorizontalLayout(puestoTextField);
        infoLayout.setWidthFull();

        llamarSiguienteButton.addClickListener(event -> llamarSiguienteTurno());
        HorizontalLayout buttonsLayout = new HorizontalLayout(llamarSiguienteButton);

        add(turnoGrid, infoLayout, buttonsLayout);
    }

    // Lógica para llamar al siguiente turno
    private void llamarSiguienteTurno() {
        Optional<Turno> siguienteTurno = turnoService.obtenerSiguienteTurnoEnEspera(currentUser.getSector());

        if (siguienteTurno.isPresent()) {
            Turno turno = siguienteTurno.get();
            turno.setEstado(EstadoTurno.LLAMANDO);
            turno.setActuadoPor(currentUser);
            turnoService.actualizarTurno(turno);

            refreshTurnoGrid(); // Actualizar la grilla con los datos actualizados

            // Crear el diálogo
            abrirDialogoTurno(turno);
        } else {
            Notification.show("No hay turnos en espera.", 3000, Notification.Position.MIDDLE);
        }
    }


    private void refreshTurnoGrid() {
        turnoGrid.setItems(turnoService.findTurnosDelDiaPorSectorYEstados(
                currentUser.getSector(), EstadoTurno.EN_ESPERA, EstadoTurno.LLAMANDO, EstadoTurno.ATENDIDO)
        );

        // Deseleccionar cualquier turno previamente seleccionado
        turnoGrid.deselectAll();
        disableButtons();
    }


    private void enableButtons(Turno turno) {
        llamarSiguienteButton.setEnabled(false); // No se puede llamar otro turno hasta finalizar el actual
        marcarAtendidoButton.setEnabled(true);
        marcarResueltoButton.setEnabled(true);
    }

    private void disableButtons() {
        llamarSiguienteButton.setEnabled(true); // Permitir llamar al siguiente turno
        marcarAtendidoButton.setEnabled(false);
        marcarResueltoButton.setEnabled(false);
    }

    private void verificarYPedirPuesto(UserService userService) {
        if (currentUser.getPuesto() == null || currentUser.getPuesto().isEmpty()) {
            Dialog dialog = new Dialog();
            TextField puestoInput = new TextField("Ingrese su puesto");
            Button confirmarButton = new Button("Confirmar", e -> {
                String puesto = puestoInput.getValue();
                if (puesto != null && !puesto.isEmpty()) {
                    currentUser.setPuesto(puesto);
                    userService.update(currentUser);
                    Notification.show("Puesto asignado: " + puesto);
                    puestoTextField.setValue(puesto);
                    dialog.close();
                } else {
                    Notification.show("Debe ingresar un puesto válido.");
                }
            });

            dialog.add(puestoInput, confirmarButton);
            dialog.open();
        } else {
            puestoTextField.setValue(currentUser.getPuesto());
        }
    }
    private void tomarTurnoAleatorio(Turno turno) {
        turno.setEstado(EstadoTurno.LLAMANDO);
        turno.setActuadoPor(currentUser);
        turnoService.actualizarTurno(turno);
        refreshTurnoGrid();
        abrirDialogoTurno(turno);
    }

    private void abrirDialogoTurno(Turno turno) {
        Dialog dialog = new Dialog();
        dialog.setWidth("500px");
        dialog.setHeight("300px");

        // Título dinámico con el código del turno
        H3 titulo = new H3("Turno: " + turno.getCodigoTurno());
        titulo.getStyle().set("margin", "0").set("padding", "0");

        TextArea descripcionField = new TextArea("Descripción");
        descripcionField.setWidthFull();
        descripcionField.setValue(turno.getDescripcion() != null ? turno.getDescripcion() : "");

        Button marcarAtendidoButton = new Button("Marcar Atendido", e -> {
            turno.setEstado(EstadoTurno.ATENDIDO);
            turno.setDescripcion(descripcionField.getValue());
            turnoService.actualizarTurno(turno);
            refreshTurnoGrid();
            Notification.show("Turno marcado como atendido.", 3000, Notification.Position.BOTTOM_START);
        });

        Button marcarResueltoButton = new Button("Marcar Resuelto", e -> {
            turno.setEstado(EstadoTurno.RESUELTO);
            turno.setDescripcion(descripcionField.getValue());
            turnoService.actualizarTurno(turno);
            refreshTurnoGrid();
            dialog.close();
            Notification.show("Turno marcado como resuelto.", 3000, Notification.Position.BOTTOM_START);
        });

        Button cerrarButton = new Button("Cerrar", e -> dialog.close());

        HorizontalLayout botonesLayout = new HorizontalLayout(marcarAtendidoButton, marcarResueltoButton, cerrarButton);
        botonesLayout.setWidthFull();

        dialog.add(new VerticalLayout(titulo, descripcionField, botonesLayout));
        dialog.open();
    }


}