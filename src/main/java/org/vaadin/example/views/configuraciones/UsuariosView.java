package org.vaadin.example.views.configuraciones;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
import com.vaadin.flow.data.provider.ListDataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.vaadin.example.data.Role;
import org.vaadin.example.data.User;
import org.vaadin.example.services.UserService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public class UsuariosView extends VerticalLayout {

    private final UserService userService;
    private final Grid<User> userGrid = new Grid<>(User.class, false);
    private final ListDataProvider<User> dataProvider;

    @Autowired
    public UsuariosView(UserService userService) {
        this.userService = userService;
        this.dataProvider = new ListDataProvider<>(userService.findAll());

        configureGrid(); // Mantenemos la configuración original de la grilla
        add(createHeader(), userGrid);
        setSizeFull();
    }

    private HorizontalLayout createHeader() {
        Button addButton = new Button("Agregar Usuario", e -> openUserDialog(null));
        return new HorizontalLayout(addButton);
    }

    private void configureGrid() {
        // Configuración original que funcionaba
        userGrid.setDataProvider(dataProvider);
        userGrid.setColumns("username", "name", "correo", "roles");
        userGrid.addComponentColumn(user -> createImage(user.getProfilePicture()))
                .setHeader("Foto");
        userGrid.addComponentColumn(this::createActions)
                .setHeader("Acciones");

        userGrid.getColumns().forEach(col -> col.setAutoWidth(true));
    }

    private HorizontalLayout createActions(User user) {
        Button editButton = new Button("Editar", e -> openUserDialog(user));
        editButton.getElement().getThemeList().add("primary");

        Button deleteButton = new Button("Eliminar", e -> {
            // Confirmar eliminación
            Dialog confirmDialog = new Dialog();
            confirmDialog.setHeaderTitle("Confirmar eliminación");
            confirmDialog.add(new Span("¿Está seguro que desea eliminar al usuario " + user.getUsername() + "?"));

            Button confirmButton = new Button("Eliminar", ev -> {
                userService.delete(user.getId());
                actualizarGrid();
                Notification.show("Usuario eliminado.");
                confirmDialog.close();
            });
            confirmButton.getElement().getThemeList().add("error");

            Button cancelButton = new Button("Cancelar", ev -> confirmDialog.close());

            confirmDialog.add(new HorizontalLayout(confirmButton, cancelButton));
            confirmDialog.open();
        });
        deleteButton.getElement().getThemeList().add("error");

        return new HorizontalLayout(editButton, deleteButton);
    }

    private void openUserDialog(User user) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(user == null ? "Nuevo Usuario" : "Editar Usuario");
        dialog.setModal(true);
        dialog.setCloseOnOutsideClick(false);

        // Campos del formulario
        TextField usernameField = new TextField("Usuario");
        usernameField.setRequired(true);
        usernameField.setRequiredIndicatorVisible(true);

        TextField nombreField = new TextField("Nombre");
        nombreField.setRequired(true);
        nombreField.setRequiredIndicatorVisible(true);

        TextField correoField = new TextField("Correo");
        correoField.setRequired(true);
        correoField.setRequiredIndicatorVisible(true);

        // MultiSelectComboBox para roles
        MultiSelectComboBox<Role> rolComboBox = new MultiSelectComboBox<>("Roles");
        rolComboBox.setItems(Role.values());
        rolComboBox.setItemLabelGenerator(Role::name);
        rolComboBox.setRequired(true);
        rolComboBox.setRequiredIndicatorVisible(true);

        // Campo de contraseña - OPCIONAL para usuarios existentes
        PasswordField passwordField = new PasswordField("Contraseña");
        PasswordField confirmPasswordField = new PasswordField("Confirmar Contraseña");

        // Para usuarios NUEVOS, la contraseña es requerida
        boolean isNewUser = (user == null);

        if (isNewUser) {
            passwordField.setRequired(true);
            passwordField.setRequiredIndicatorVisible(true);
            confirmPasswordField.setRequired(true);
            confirmPasswordField.setRequiredIndicatorVisible(true);
        } else {
            // Para edición, la contraseña es opcional
            passwordField.setRequired(false);
            passwordField.setRequiredIndicatorVisible(false);
            passwordField.setPlaceholder("Dejar en blanco para no cambiar");
            confirmPasswordField.setRequired(false);
            confirmPasswordField.setRequiredIndicatorVisible(false);
            confirmPasswordField.setPlaceholder("Dejar en blanco para no cambiar");
        }

        // Configuración de imagen
        FileBuffer fileBuffer = new FileBuffer();
        Upload upload = new Upload(fileBuffer);
        upload.setAcceptedFileTypes("image/jpeg", "image/png", "image/jpg");
        upload.setMaxFileSize(5 * 1024 * 1024);

        ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream();
        Image imagePreview = new Image();
        imagePreview.setWidth("100px");
        imagePreview.setHeight("100px");
        imagePreview.getStyle().set("object-fit", "cover");
        imagePreview.setVisible(false);

        upload.addSucceededListener(event -> {
            try (InputStream fileInputStream = fileBuffer.getInputStream()) {
                imageBuffer.reset();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    imageBuffer.write(buffer, 0, bytesRead);
                }
                String base64Image = "data:image/png;base64," +
                        java.util.Base64.getEncoder().encodeToString(imageBuffer.toByteArray());
                imagePreview.setSrc(base64Image);
                imagePreview.setVisible(true);
            } catch (IOException e) {
                Notification.show("Error al cargar la imagen: " + e.getMessage());
            }
        });

        // Precargar datos si es edición
        if (!isNewUser) {
            usernameField.setValue(user.getUsername());
            usernameField.setEnabled(false); // No permitir cambiar username
            nombreField.setValue(user.getName());
            correoField.setValue(user.getCorreo());
            rolComboBox.setValue(user.getRoles());

            if (user.getProfilePicture() != null && user.getProfilePicture().length > 0) {
                String base64Image = "data:image/png;base64," +
                        java.util.Base64.getEncoder().encodeToString(user.getProfilePicture());
                imagePreview.setSrc(base64Image);
                imagePreview.setVisible(true);
            }
        }

        // Botones
        Button saveButton = new Button("Guardar", e -> {
            // Validaciones básicas
            if (usernameField.isEmpty()) {
                Notification.show("El nombre de usuario es requerido");
                return;
            }
            if (nombreField.isEmpty()) {
                Notification.show("El nombre es requerido");
                return;
            }
            if (correoField.isEmpty()) {
                Notification.show("El correo es requerido");
                return;
            }
            if (rolComboBox.isEmpty()) {
                Notification.show("Debe seleccionar al menos un rol");
                return;
            }

            // Validación de contraseña para NUEVOS usuarios
            if (isNewUser) {
                if (passwordField.isEmpty()) {
                    Notification.show("La contraseña es requerida para nuevos usuarios");
                    return;
                }
                if (!passwordField.getValue().equals(confirmPasswordField.getValue())) {
                    Notification.show("Las contraseñas no coinciden");
                    return;
                }
            } else {
                // Para edición, solo validar si se ingresó una contraseña
                if (!passwordField.isEmpty() || !confirmPasswordField.isEmpty()) {
                    if (passwordField.isEmpty() || confirmPasswordField.isEmpty()) {
                        Notification.show("Debe completar ambos campos de contraseña para cambiarla");
                        return;
                    }
                    if (!passwordField.getValue().equals(confirmPasswordField.getValue())) {
                        Notification.show("Las contraseñas no coinciden");
                        return;
                    }
                }
            }

            try {
                User userToSave = isNewUser ? new User() : user;

                userToSave.setUsername(usernameField.getValue());
                userToSave.setName(nombreField.getValue());
                userToSave.setCorreo(correoField.getValue());
                userToSave.setRoles(rolComboBox.getValue());

                // Manejo de contraseña
                if (isNewUser) {
                    // Nuevo usuario: siempre guardar contraseña
                    userToSave.setHashedPassword(
                            userService.encodePassword(passwordField.getValue())
                    );
                } else if (!passwordField.isEmpty()) {
                    // Edición y se ingresó nueva contraseña
                    userToSave.setHashedPassword(
                            userService.encodePassword(passwordField.getValue())
                    );
                }
                // Si es edición y passwordField está vacío, no modificamos la contraseña existente

                // Manejo de imagen
                if (imageBuffer.size() > 0) {
                    userToSave.setProfilePicture(imageBuffer.toByteArray());
                }

                // Guardar
                userService.update(userToSave);
                actualizarGrid();

                Notification.show(isNewUser ? "Usuario creado exitosamente" : "Usuario actualizado exitosamente");
                dialog.close();

            } catch (Exception ex) {
                Notification.show("Error al guardar: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        Button cancelButton = new Button("Cancelar", e -> dialog.close());

        // Layout del formulario
        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("500px", 2)
        );

        formLayout.add(usernameField, 2);
        formLayout.add(nombreField, 2);
        formLayout.add(correoField, 2);
        formLayout.add(rolComboBox, 2);

        // Los campos de contraseña en una sola fila
        formLayout.add(passwordField, 1);
        formLayout.add(confirmPasswordField, 1);

        formLayout.add(upload, 1);
        formLayout.add(imagePreview, 1);

        HorizontalLayout buttonLayout = new HorizontalLayout(saveButton, cancelButton);
        buttonLayout.setJustifyContentMode(JustifyContentMode.END);
        buttonLayout.setWidthFull();

        VerticalLayout dialogLayout = new VerticalLayout(formLayout, buttonLayout);
        dialogLayout.setPadding(true);
        dialogLayout.setSpacing(true);

        dialog.add(dialogLayout);
        dialog.setWidth("600px");
        dialog.open();
    }

    private Image createImage(byte[] foto) {
        if (foto == null || foto.length == 0) {
            return new Image("https://via.placeholder.com/50", "Sin imagen");
        }
        String base64Image = "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(foto);
        Image image = new Image(base64Image, "Foto");
        image.setWidth("50px");
        image.setHeight("50px");
        image.getStyle().set("object-fit", "cover");
        return image;
    }

    private void actualizarGrid() {
        dataProvider.getItems().clear();
        dataProvider.getItems().addAll(userService.findAll());
        dataProvider.refreshAll();
    }
}