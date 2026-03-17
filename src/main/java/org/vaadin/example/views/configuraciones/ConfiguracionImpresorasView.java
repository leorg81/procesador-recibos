package org.vaadin.example.views.configuraciones;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class AjustesGeneralesView extends VerticalLayout {
    public AjustesGeneralesView() {
        setPadding(true);
        setSpacing(true);
        add(new Paragraph("Aquí podrás agregar ajustes generales del sistema próximamente."));
    }
}