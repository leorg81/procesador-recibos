package org.vaadin.example.views.acercade;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility.Margin;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

@PageTitle("Acerca de")
@Route("acercade")
@Menu(order = 9, icon = LineAwesomeIconUrl.INFO_SOLID)
@AnonymousAllowed
public class AcercadeView extends VerticalLayout {

    public AcercadeView() {
        setSpacing(false);

        Image img = new Image("images/empty-plant.png", "Logo de la aplicación");
        img.setWidth("150px");
        add(img);

        H2 header = new H2("Sistema de Procesamiento de Recibos ");
        header.addClassNames(Margin.Top.XLARGE, Margin.Bottom.MEDIUM);
        add(header);

        add(new Paragraph("Esta aplicación fue desarrollada por Leonardo Rodríguez como una solución para la gestión recibos de sueldo."));


        add(new Paragraph("Tecnologías utilizadas:"));

        UnorderedList tecnologias = new UnorderedList(
                new ListItem("Vaadin 24.7+"),
                new ListItem("Spring Boot 3.4.5"),
                new ListItem("MariaDB 10"),
                new ListItem("Java 21")
        );
        add(tecnologias);

        add(new Paragraph("La aplicación permite:"));

        UnorderedList funcionalidades = new UnorderedList(
                new ListItem("Cargar mensualmente un documento pdf con todos los recibos."),
                new ListItem("Dividir el documento en tantos recibos como encuentre"),
                new ListItem("Enviar recibos a los funcionarios correspondientes."),
                new ListItem("Cargar información de contacto (e-mail, número telefónico) de cada funcionario."),
                new ListItem("Generar reposrtes de envíos y solicitudes."),
                new ListItem("Administrar usuarios y roles."),
                new ListItem("Publica un API Rest para Whatsapp y Telegram"),
                new ListItem("Recuparación de contraseñas autogestionado con envío de correo.")
        );
        add(funcionalidades);

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setDefaultHorizontalComponentAlignment(Alignment.CENTER);
        getStyle().set("text-align", "center");
    }
}
