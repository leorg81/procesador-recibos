package org.vaadin.example.views.configuraciones;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.example.data.Noticia;
import org.vaadin.example.services.NoticiaService;
import org.vaadin.example.views.MainView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

@Route(value = "admin/noticias", layout = MainView.class)
@PageTitle("Administración de Noticias")
public class NoticiasView extends VerticalLayout {

    private final NoticiaService noticiaService;
    private final Grid<Noticia> noticiaGrid = new Grid<>(Noticia.class);
    private final ListDataProvider<Noticia> dataProvider;

    @Autowired
    public NoticiasView(NoticiaService noticiaService) {
        this.noticiaService = noticiaService;
        this.dataProvider = new ListDataProvider<>(noticiaService.findAll());

        setUpGrid();
        add(createHeader(), noticiaGrid);
    }

    private HorizontalLayout createHeader() {
        Button addButton = new Button("Agregar Noticia", e -> openNoticiaDialog(null));
        return new HorizontalLayout(addButton);
    }

    private void setUpGrid() {
        noticiaGrid.setDataProvider(dataProvider);
        noticiaGrid.setColumns("descripcion", "activo");
        noticiaGrid.addComponentColumn(noticia -> createImage(noticia.getFoto()))
                .setHeader("Foto");
        noticiaGrid.addComponentColumn(noticia -> createActions(noticia))
                .setHeader("Acciones");
    }

    private HorizontalLayout createActions(Noticia noticia) {
        Button editButton = new Button("Editar", e -> openNoticiaDialog(noticia));
        Button deleteButton = new Button("Eliminar", e -> {
            noticiaService.delete(noticia.getId());
            dataProvider.getItems().remove(noticia);
            dataProvider.refreshAll();
            Notification.show("Noticia eliminada.");
        });
        return new HorizontalLayout(editButton, deleteButton);
    }

    private void openNoticiaDialog(Noticia noticia) {
        Dialog dialog = new Dialog();

        // Crear los campos de entrada
        TextField descripcionField = new TextField("Descripción");
        Checkbox activoField = new Checkbox("Activo");

        // Crear un FileBuffer para cargar la foto
        FileBuffer fileBuffer = new FileBuffer();
        Upload upload = new Upload(fileBuffer);
        ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream();
        Image imagePreview = new Image();
        imagePreview.setWidth("200px"); // Ajustar el tamaño de la imagen de vista previa

        // Establecemos los tipos de archivo aceptados para la foto
        upload.setAcceptedFileTypes("image/jpeg", "image/png");
        upload.addSucceededListener(event -> {
            try {
                InputStream fileInputStream = fileBuffer.getInputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    imageBuffer.write(buffer, 0, bytesRead);
                }
                // Mostrar la imagen cargada en la vista previa
                String base64Image = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(imageBuffer.toByteArray());
                imagePreview.setSrc(base64Image);
            } catch (IOException e) {
                Notification.show("Error al cargar la imagen.");
            }
        });

        // Si la noticia es null, inicializamos una nueva
        if (noticia == null) {
            noticia = new Noticia();
        }

        // Prellenar los campos con los datos de la noticia (si no son null)
        descripcionField.setValue(noticia.getDescripcion() != null ? noticia.getDescripcion() : "");
        activoField.setValue(noticia.isActivo());

        // Previsualizar la imagen si ya existe
        if (noticia.getFoto() != null && noticia.getFoto().length > 0) {
            String base64Image = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(noticia.getFoto());
            imagePreview.setSrc(base64Image);
        }

        // Botón para guardar los datos de la noticia
        Noticia finalNoticia = noticia;
        Button saveButton = new Button("Guardar", e -> {
            if (descripcionField.getValue().isEmpty()) {
                Notification.show("La descripción no puede estar vacía.");
                return;
            }

            // Actualizar los datos de la noticia
            finalNoticia.setDescripcion(descripcionField.getValue());
            finalNoticia.setActivo(activoField.getValue());

            // Si se cargó una imagen, la asignamos a la noticia
            if (imageBuffer.size() > 0) {
                finalNoticia.setFoto(imageBuffer.toByteArray());
            }

            // Guardar los datos en la base de datos
            noticiaService.update(finalNoticia);

            // Actualizar la vista con los cambios
            dataProvider.getItems().clear();
            dataProvider.getItems().addAll(noticiaService.findAll());
            dataProvider.refreshAll();

            Notification.show("Noticia guardada.");
            dialog.close();
        });

        // Botón para cancelar la edición
        Button cancelButton = new Button("Cancelar", e -> dialog.close());

        // Crear el layout para los campos del formulario
        FormLayout formLayout = new FormLayout(
                descripcionField, activoField, upload, imagePreview
        );

        // Establecer el layout desplazable
        formLayout.setSizeFull();
        formLayout.setHeight("400px");  // Ajusta la altura según sea necesario

        // Layout para los botones
        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
        buttonLayout.setSpacing(true);
        buttonLayout.setWidthFull();

        SplitLayout dialogLayout = new SplitLayout(formLayout, buttonLayout);
        dialogLayout.setOrientation(SplitLayout.Orientation.VERTICAL);
        dialogLayout.setWidthFull();

        dialog.add(dialogLayout);
        dialog.open();
    }


    private Image createImage(byte[] foto) {
        if (foto == null || foto.length == 0) {
            return new Image("https://via.placeholder.com/50", "Sin imagen");
        }
        String base64Image = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(foto);
        return new Image(base64Image, "Foto");
    }
}
