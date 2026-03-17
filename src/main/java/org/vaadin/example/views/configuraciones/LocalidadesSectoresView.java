package org.vaadin.example.views.configuraciones;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.vaadin.example.data.Localidad;
import org.vaadin.example.data.Sector;
import org.vaadin.example.services.LocalidadService;
import org.vaadin.example.services.SectorService;
import org.vaadin.example.views.MainView;

import java.util.Collections;

@Route(value = "admin/localidades-sectores", layout = MainView.class)
@PageTitle("Administración de Localidades y Sectores")
public class LocalidadesSectoresView extends Div {

    private final Grid<Localidad> localidadesGrid = new Grid<>(Localidad.class, false);
    private final Grid<Sector> sectoresGrid = new Grid<>(Sector.class, false);

    private final TextField localidadNombre = new TextField("Nombre de la Localidad");
    private final TextField sectorNombre = new TextField("Nombre del Sector");

    private final Button addLocalidad = new Button("Agregar Localidad");
    private final Button addSector = new Button("Agregar Sector");

    private final LocalidadService localidadService;
    private final SectorService sectorService;

    private Localidad selectedLocalidad;

    public LocalidadesSectoresView(LocalidadService localidadService, SectorService sectorService) {
        this.localidadService = localidadService;
        this.sectorService = sectorService;

        setSizeFull();
        addClassName("localidades-sectores-view");

        SplitLayout layout = new SplitLayout();
        layout.setSizeFull();

        setupLocalidadesGrid();
        setupSectoresGrid();

        // Configurar Layouts
        VerticalLayout localidadesLayout = new VerticalLayout(localidadesGrid, localidadNombre, addLocalidad);
        localidadesLayout.setWidth("50%");
        localidadesLayout.setSpacing(true);

        VerticalLayout sectoresLayout = new VerticalLayout(sectoresGrid, sectorNombre, addSector);
        sectoresLayout.setWidth("50%");
        sectoresLayout.setSpacing(true);

        layout.addToPrimary(localidadesLayout);
        layout.addToSecondary(sectoresLayout);

        add(layout);

        // Botón para agregar localidad
        addLocalidad.addClickListener(event -> {
            if (!localidadNombre.getValue().isEmpty()) {
                Localidad newLocalidad = new Localidad();
                newLocalidad.setNombre(localidadNombre.getValue());
                localidadService.save(newLocalidad);
                refreshLocalidadesGrid();
                localidadNombre.clear();
            }
        });

        // Botón para agregar sector
        addSector.addClickListener(event -> {
            if (selectedLocalidad == null) {
                Notification.show("Debe seleccionar una localidad para agregar un sector.");
            } else if (!sectorNombre.getValue().isEmpty()) {
                Sector newSector = new Sector();
                newSector.setNombre(sectorNombre.getValue());
                newSector.setLocalidad(selectedLocalidad);
                sectorService.save(newSector);
                refreshSectoresGrid(selectedLocalidad);
                sectorNombre.clear();
            }
        });

        refreshLocalidadesGrid();
    }

    private void setupLocalidadesGrid() {
        localidadesGrid.addColumn(Localidad::getNombre).setHeader("Localidades").setAutoWidth(true);
        localidadesGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedLocalidad = event.getValue();
            if (selectedLocalidad != null) {
                refreshSectoresGrid(selectedLocalidad);
            } else {
                sectoresGrid.setItems(Collections.emptyList());
            }
        });

        // Botón de edición en diálogo
        localidadesGrid.addComponentColumn(localidad -> {
            Button editButton = new Button("Editar", click -> openEditLocalidadDialog(localidad));
            return editButton;
        }).setHeader("Acciones");
    }

    private void setupSectoresGrid() {
        sectoresGrid.addColumn(Sector::getNombre).setHeader("Sectores").setAutoWidth(true);

        // Botón de edición en diálogo
        sectoresGrid.addComponentColumn(sector -> {
            Button editButton = new Button("Editar", click -> openEditSectorDialog(sector));
            return editButton;
        }).setHeader("Acciones");
    }

    private void openEditLocalidadDialog(Localidad localidad) {
        Dialog dialog = new Dialog();
        TextField nombreField = new TextField("Nombre de la Localidad", localidad.getNombre());
        Button saveButton = new Button("Guardar", event -> {
            localidad.setNombre(nombreField.getValue());
            localidadService.save(localidad);
            refreshLocalidadesGrid();
            dialog.close();
        });
        Button cancelButton = new Button("Cancelar", event -> dialog.close());
        dialog.add(new VerticalLayout(nombreField, new HorizontalLayout(saveButton, cancelButton)));
        dialog.open();
    }

    private void openEditSectorDialog(Sector sector) {
        Dialog dialog = new Dialog();
        TextField nombreField = new TextField("Nombre del Sector", sector.getNombre());
        Button saveButton = new Button("Guardar", event -> {
            sector.setNombre(nombreField.getValue());
            sectorService.save(sector);
            refreshSectoresGrid(sector.getLocalidad());
            dialog.close();
        });
        Button cancelButton = new Button("Cancelar", event -> dialog.close());
        dialog.add(new VerticalLayout(nombreField, new HorizontalLayout(saveButton, cancelButton)));
        dialog.open();
    }

    private void refreshLocalidadesGrid() {
        localidadesGrid.setItems(localidadService.findAll());
    }

    private void refreshSectoresGrid(Localidad localidad) {
        sectoresGrid.setItems(sectorService.findByLocalidad(localidad));
    }
}
