package org.vaadin.example.views.perfil;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.vaadin.example.data.User;
import org.vaadin.example.security.AuthenticatedUser;
import org.vaadin.example.services.UserService;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@PageTitle("Perfil")
@Route(value = "perfil")
@Menu(order = 8, icon = LineAwesomeIconUrl.USER)
@RolesAllowed({"LIQUIDACIONES","ADMIN","PERSONAL"})
public class PerfilView extends VerticalLayout {

    private final UserService userService;
    private final User currentUser;
    private final AuthenticatedUser authenticatedUser;

    @Autowired
    public PerfilView(UserService userService, AuthenticatedUser authenticatedUser) {
        this.userService = userService;
        this.authenticatedUser = authenticatedUser;

        // Simulación: en tu app, obtené el usuario desde la sesión o seguridad
        this.currentUser = authenticatedUser.get().orElseThrow(() ->
                new RuntimeException("No se pudo obtener el usuario autenticado"));                //userService.getCurrentUser(); // <-- deberías implementar este método

        setAlignItems(Alignment.CENTER);
        setWidthFull();

        add(createForm());
    }

    private VerticalLayout createForm() {
        VerticalLayout layout = new VerticalLayout();
        layout.setWidth("100%");
        layout.setMaxWidth("800px");

        H3 title = new H3("Información personal");
        layout.add(title);

        FileBuffer fileBuffer = new FileBuffer();
        Upload upload = new Upload(fileBuffer);
        ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream();
        Image imagePreview = new Image();
        imagePreview.setWidth("100px"); // Ajustar el tamaño de la imagen de vista previa



        if (currentUser.getProfilePicture() != null && currentUser.getProfilePicture().length > 0) {
            String base64 = Base64.getEncoder().encodeToString(currentUser.getProfilePicture());
            imagePreview.setSrc("data:image/png;base64," + base64);
        }


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



        TextField usernameField = new TextField("Usuario");
        usernameField.setValue(currentUser.getUsername());
        usernameField.setReadOnly(true);

        TextField nameField = new TextField("Nombre completo");
        nameField.setValue(currentUser.getName());

        TextField correoField = new TextField("Correo");

        correoField.setValue(currentUser.getCorreo() != null ? currentUser.getCorreo() : "");




        TextField rolesField = new TextField("Roles");
        rolesField.setValue(currentUser.getRoles().toString());
        rolesField.setReadOnly(true);






        // Cambio de contraseña
        PasswordField newPasswordField = new PasswordField("Nueva contraseña");
        PasswordField confirmPasswordField = new PasswordField("Confirmar nueva contraseña");

        // Botones
        Button saveButton = new Button("Guardar", e -> {
            if (!newPasswordField.isEmpty()) {
                if (!newPasswordField.getValue().equals(confirmPasswordField.getValue())) {
                    Notification.show("Las contraseñas no coinciden.", 3000, Notification.Position.MIDDLE)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                } else {
                    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
                    currentUser.setHashedPassword(encoder.encode(newPasswordField.getValue()));
                }
            }
            if (imageBuffer.size() > 0) {
                currentUser.setProfilePicture(imageBuffer.toByteArray());
            }
            currentUser.setName(nameField.getValue());
            currentUser.setCorreo(correoField.getValue());

            userService.update(currentUser);
            Notification.show("Perfil actualizado.", 2000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });

        Button cancelButton = new Button("Cancelar", e -> {
            nameField.clear(); //currentUser.getName());
            correoField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
        });

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout buttons = new HorizontalLayout(saveButton, cancelButton);
        VerticalLayout unc = new VerticalLayout(usernameField,nameField);
        VerticalLayout ima = new VerticalLayout(imagePreview,upload);
        FormLayout formLayout = new FormLayout(unc, ima,correoField,newPasswordField,confirmPasswordField);
        formLayout.setWidthFull();
        layout.add(formLayout, buttons);
        return layout;
    }
}
