package org.vaadin.example.views.configuraciones;

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
import org.vaadin.example.data.TipoRecibo;
import org.vaadin.example.services.TipoReciboService;

import java.util.List;

public class TipoReciboView extends VerticalLayout {

    private final TipoReciboService tipoReciboService;
    private final Grid<TipoRecibo> grid = new Grid<>(TipoRecibo.class, false);
    private final Button nuevoBtn = new Button("Nuevo Tipo de Recibo", new Icon(VaadinIcon.PLUS));

    @Autowired
    public TipoReciboView(TipoReciboService tipoReciboService) {
        this.tipoReciboService = tipoReciboService;

        setSizeFull();
        setPadding(false);
        setSpacing(true);

        H2 titulo = new H2("🧾 Gestión de Tipos de Recibo");

        // Configurar grid
        grid.addColumn(TipoRecibo::getCodigo).setHeader("Código").setAutoWidth(true);
        grid.addColumn(TipoRecibo::getNombre).setHeader("Nombre").setAutoWidth(true);
        grid.addColumn(TipoRecibo::getDescripcion).setHeader("Descripción").setAutoWidth(true);
        grid.addColumn(t -> t.getRecibos() != null ? t.getRecibos().size() : 0)
                .setHeader("Recibos Asociados").setAutoWidth(true);

        grid.addComponentColumn(tipoRecibo -> {
            HorizontalLayout acciones = new HorizontalLayout();
            acciones.setSpacing(true);

            Button editar = new Button(new Icon(VaadinIcon.EDIT));
            editar.addClickListener(e -> abrirFormularioEdicion(tipoRecibo));

            Button eliminar = new Button(new Icon(VaadinIcon.TRASH));
            eliminar.getElement().getThemeList().add("error");
            eliminar.addClickListener(e -> eliminarTipoRecibo(tipoRecibo));

            acciones.add(editar, eliminar);
            return acciones;
        }).setHeader("Acciones").setAutoWidth(true);

        // Botón nuevo
        nuevoBtn.addClickListener(e -> abrirFormularioNuevo());

        // Cargar datos
        cargarTiposRecibo();

        // Usa un layout vertical con expansión del grid
        VerticalLayout contentLayout = new VerticalLayout(titulo, nuevoBtn, grid);
        contentLayout.setSizeFull();
        contentLayout.setPadding(false);
        contentLayout.setSpacing(true);

        add(contentLayout);
        expand(grid);
    }

    private void cargarTiposRecibo() {
        List<TipoRecibo> tiposRecibo = tipoReciboService.listarTodosConRecuentos();
        grid.setItems(tiposRecibo);
    }

    private void abrirFormularioNuevo() {
        abrirFormulario(null);
    }

    private void abrirFormularioEdicion(TipoRecibo tipoRecibo) {
        abrirFormulario(tipoRecibo);
    }

    private void abrirFormulario(TipoRecibo tipoReciboExistente) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setHeaderTitle(tipoReciboExistente == null ?
                "Nuevo Tipo de Recibo" : "Editar Tipo de Recibo");

        FormLayout form = new FormLayout();

        TextField codigoField = new TextField("Código");
        codigoField.setRequired(true);
        codigoField.setMaxLength(10);
        codigoField.setHelperText("Código único para el tipo de recibo");

        TextField nombreField = new TextField("Nombre");
        nombreField.setRequired(true);
        nombreField.setMaxLength(100);

        TextField descripcionField = new TextField("Descripción");
        descripcionField.setMaxLength(255);

        // Binder
        Binder<TipoRecibo> binder = new Binder<>(TipoRecibo.class);
        binder.forField(codigoField)
                .asRequired("Código requerido")
                .bind(TipoRecibo::getCodigo, TipoRecibo::setCodigo);

        binder.forField(nombreField)
                .asRequired("Nombre requerido")
                .bind(TipoRecibo::getNombre, TipoRecibo::setNombre);

        binder.forField(descripcionField)
                .bind(TipoRecibo::getDescripcion, TipoRecibo::setDescripcion);

        if (tipoReciboExistente != null) {
            binder.readBean(tipoReciboExistente);
            codigoField.setEnabled(false); // No cambiar código en edición
        } else {
            binder.setBean(new TipoRecibo());
        }

        // Botones
        Button guardar = new Button("Guardar", new Icon(VaadinIcon.CHECK));
        guardar.getElement().getThemeList().add("primary");
        guardar.addClickListener(e -> {
            try {
                TipoRecibo tipoRecibo = tipoReciboExistente != null ?
                        tipoReciboExistente : new TipoRecibo();

                binder.writeBean(tipoRecibo);

                tipoReciboService.guardar(tipoRecibo);
                dialog.close();
                cargarTiposRecibo();
                Notification.show("✅ Tipo de Recibo guardado correctamente");

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

        form.add(codigoField, nombreField, descripcionField, botones);
        dialog.add(form);
        dialog.open();
    }

    private void eliminarTipoRecibo(TipoRecibo tipoRecibo) {
        try {
            tipoReciboService.eliminar(tipoRecibo.getId());
            cargarTiposRecibo();
            Notification.show("✅ Tipo de Recibo eliminado correctamente");
        } catch (IllegalStateException e) {
            Notification.show("❌ No se puede eliminar: " + e.getMessage(), 5000,
                    Notification.Position.MIDDLE);
        } catch (Exception e) {
            Notification.show("❌ Error al eliminar: " + e.getMessage(), 3000,
                    Notification.Position.MIDDLE);
        }
    }
}