package org.vaadin.example.views.configuraciones; // Mueve al mismo paquete

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.example.data.Localidad;
import org.vaadin.example.services.LocalidadService;

import java.util.List;

// QUITA ESTAS ANOTACIONES:
// @PageTitle("Gestión de Localidades")
// @Route(value = "localidades", layout = MainLayout.class)
// @Menu(order = 4, icon = LineAwesomeIconUrl.MAP_MARKER_ALT_SOLID)
// @RolesAllowed({"ADMIN"})

public class LocalidadView extends VerticalLayout { // Solo extiende VerticalLayout

    private final LocalidadService localidadService;
    private final Grid<Localidad> grid = new Grid<>(Localidad.class, false);
    private final Button nuevoBtn = new Button("Nueva Localidad", new Icon(VaadinIcon.PLUS));

    @Autowired // Si necesitas inyección, mantén @Autowired
    public LocalidadView(LocalidadService localidadService) {
        this.localidadService = localidadService;

        setSizeFull(); // Importante para que ocupe todo el espacio en la pestaña
        setPadding(false); // Puedes ajustar según prefieras
        setSpacing(true);

        H2 titulo = new H2("🏙️ Gestión de Localidades");

        // Configurar grid
        grid.addColumn(Localidad::getCodigo).setHeader("Código").setAutoWidth(true);
        grid.addColumn(Localidad::getNombre).setHeader("Nombre").setAutoWidth(true);
        grid.addColumn(l -> l.getFuncionarios() != null ? l.getFuncionarios().size() : 0)
                .setHeader("Funcionarios").setAutoWidth(true);
        grid.addColumn(l -> l.getRecibos() != null ? l.getRecibos().size() : 0)
                .setHeader("Recibos").setAutoWidth(true);

        grid.addComponentColumn(localidad -> {
            HorizontalLayout acciones = new HorizontalLayout();
            acciones.setSpacing(true);

            Button editar = new Button(new Icon(VaadinIcon.EDIT));
            editar.addClickListener(e -> abrirFormularioEdicion(localidad));

            Button eliminar = new Button(new Icon(VaadinIcon.TRASH));
            eliminar.getElement().getThemeList().add("error");
            eliminar.addClickListener(e -> eliminarLocalidad(localidad));

            acciones.add(editar, eliminar);
            return acciones;
        }).setHeader("Acciones").setAutoWidth(true);

        // Botón nuevo
        nuevoBtn.addClickListener(e -> abrirFormularioNuevo());

        // Cargar datos
        cargarLocalidades();

        // Usa un layout vertical con expansión del grid
        VerticalLayout contentLayout = new VerticalLayout(titulo, nuevoBtn, grid);
        contentLayout.setSizeFull();
        contentLayout.setPadding(false);
        contentLayout.setSpacing(true);

        add(contentLayout);
        expand(grid);
    }

    private void cargarLocalidades() {
        // Usa el método que inicializa las colecciones lazy
        List<Localidad> localidades = localidadService.listarTodasConRecuentos();;
        grid.setItems(localidades);
    }

    private void abrirFormularioNuevo() {
        abrirFormulario(null);
    }

    private void abrirFormularioEdicion(Localidad localidad) {
        abrirFormulario(localidad);
    }

    private void abrirFormulario(Localidad localidadExistente) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setHeaderTitle(localidadExistente == null ?
                "Nueva Localidad" : "Editar Localidad");

        FormLayout form = new FormLayout();

        TextField codigoField = new TextField("Código");
        codigoField.setRequired(true);
        codigoField.setMaxLength(5);
        codigoField.setHelperText("Ej: RN, PA, CL (máx 5 caracteres)");

        TextField nombreField = new TextField("Nombre");
        nombreField.setRequired(true);
        nombreField.setMaxLength(100);

        // Binder
        Binder<Localidad> binder = new Binder<>(Localidad.class);
        binder.forField(codigoField)
                .asRequired("Código requerido")
                .bind(Localidad::getCodigo, Localidad::setCodigo);

        binder.forField(nombreField)
                .asRequired("Nombre requerido")
                .bind(Localidad::getNombre, Localidad::setNombre);

        if (localidadExistente != null) {
            binder.readBean(localidadExistente);
            codigoField.setEnabled(false); // No cambiar código en edición
        } else {
            binder.setBean(new Localidad());
        }

        // Botones
        Button guardar = new Button("Guardar", new Icon(VaadinIcon.CHECK));
        guardar.getElement().getThemeList().add("primary");
        guardar.addClickListener(e -> {
            try {
                Localidad localidad = localidadExistente != null ?
                        localidadExistente : new Localidad();

                binder.writeBean(localidad);

                localidadService.guardar(localidad);
                dialog.close();
                cargarLocalidades();
                Notification.show("✅ Localidad guardada correctamente");

            } catch (ValidationException ex) {
                Notification.show("❌ Error en los datos: " + ex.getMessage(), 3000,
                        Notification.Position.MIDDLE);
            } catch (IllegalArgumentException ex) {
                Notification.show("❌ Error: " + ex.getMessage(), 3000,
                        Notification.Position.MIDDLE);
            }
        });

        Button cancelar = new Button("Cancelar", new Icon(VaadinIcon.CLOSE));
        cancelar.addClickListener(e -> dialog.close());

        HorizontalLayout botones = new HorizontalLayout(guardar, cancelar);
        botones.setSpacing(true);

        form.add(codigoField, nombreField, botones);
        dialog.add(form);
        dialog.open();
    }

    private void eliminarLocalidad(Localidad localidad) {
        try {
            localidadService.eliminar(localidad.getId());
            cargarLocalidades();
            Notification.show("✅ Localidad eliminada correctamente");
        } catch (IllegalStateException e) {
            Notification.show("❌ No se puede eliminar: " + e.getMessage(), 5000,
                    Notification.Position.MIDDLE);
        } catch (Exception e) {
            Notification.show("❌ Error al eliminar: " + e.getMessage(), 3000,
                    Notification.Position.MIDDLE);
        }
    }
}