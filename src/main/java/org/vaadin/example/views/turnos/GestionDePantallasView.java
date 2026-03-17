package org.vaadin.example.views.turnos;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.vaadin.example.services.LocalidadService;
import org.vaadin.example.services.SectorService;
import org.vaadin.example.data.Localidad;
import org.vaadin.example.data.Sector;
import org.vaadin.example.views.MainView;

import java.util.ArrayList;
import java.util.List;

@Route(value = "turnos/pantallas", layout = MainView.class)
@PageTitle("Gestión de Pantallas de Turnos")
public class GestionDePantallasView extends VerticalLayout {

    private Long localidadId=null;
    private final Label titulo = new Label("Seleccione localidad y sectores para desplegar pantalla de turnos");

    public GestionDePantallasView(LocalidadService localidadService, SectorService sectorService) {
        addClassName("container");
        titulo.getStyle().set("font-size", "24px").set("font-weight", "bold");
        add(titulo);

        // Obtiene las localidades
        List<Localidad> localidades = localidadService.findAll();
        HorizontalLayout botonesLayout = new HorizontalLayout();
        botonesLayout.setSpacing(true);
        botonesLayout.setJustifyContentMode(JustifyContentMode.CENTER);

        CheckboxGroup<Sector> checkboxGroup = new CheckboxGroup<>();
        checkboxGroup.setLabel("Seleccione los sectores:");
        checkboxGroup.setVisible(false);

        Button botonAceptar = new Button("Aceptar");
        botonAceptar.setVisible(false);

        // Crea un botón para cada localidad
        localidades.forEach(localidad -> {
            Button button = new Button(localidad.getNombre());
            button.addClassName("localidad-button");
            button.addClickListener(event -> {
                // Guarda la localidad seleccionada
                VaadinSession.getCurrent().setAttribute("localidadSeleccionada", localidad);

                // Muestra los sectores disponibles
                List<Sector> sectores = localidad.getSectores();
                localidadId = localidad.getId();

                checkboxGroup.setItems(sectores);
                checkboxGroup.setItemLabelGenerator(Sector::getNombre);
                checkboxGroup.setVisible(true);
                botonAceptar.setVisible(true);
            });
            botonesLayout.add(button);
        });

        // Botón aceptar
        botonAceptar.addClickListener(event -> {
            ArrayList<Sector> sectoresSeleccionados = new ArrayList<>(checkboxGroup.getSelectedItems());
            VaadinSession.getCurrent().setAttribute("sectoresSeleccionados", sectoresSeleccionados);

            // Navega a la pantalla de turnos
            UI.getCurrent().getSession().setAttribute("localidadId", localidadId);
            UI.getCurrent().navigate("turnos/" + localidadId);
          //  UI.getCurrent().navigate("pantalla-turnos");
        });

        add(botonesLayout, checkboxGroup, botonAceptar);
        setAlignItems(Alignment.CENTER);
        getStyle().set("padding", "20px");
    }
}
