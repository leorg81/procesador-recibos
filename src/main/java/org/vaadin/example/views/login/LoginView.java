package org.vaadin.example.views.login;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.login.LoginOverlay;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.internal.RouteUtil;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.vaadin.example.data.User;
import org.vaadin.example.security.AuthenticatedUser;
import org.vaadin.example.services.CaptchaService;
import org.vaadin.example.services.EmailService;
import org.vaadin.example.services.UserService;

import java.util.Optional;
import java.util.Random;

@AnonymousAllowed
@PageTitle("Login")
@Route(value = "login")
public class LoginView extends LoginOverlay implements BeforeEnterObserver {

    private final AuthenticatedUser authenticatedUser;
    private final UserService userService;
    private final EmailService emailService;
    private final CaptchaService captchaService;

    @Autowired
    private Environment env;


    public LoginView(AuthenticatedUser authenticatedUser,
                     UserService userService,
                     @Lazy EmailService emailService,
                     CaptchaService captchaService) {
        this.authenticatedUser = authenticatedUser;
        this.userService = userService;
        this.emailService = emailService;
        this.captchaService = captchaService;


        setAction(RouteUtil.getRoutePath(VaadinService.getCurrent().getContext(), getClass()));
        configureLoginOverlay();
        addForgotPasswordListener(e -> showCombinedDialog());
    }

    private void configureLoginOverlay() {
        LoginI18n i18n = LoginI18n.createDefault();

        LoginI18n.Header header = new LoginI18n.Header();
        header.setTitle("SPR");
        header.setDescription("Intendencia de Río Negro ");
        i18n.setHeader(header);

        LoginI18n.Form form = i18n.getForm();
        form.setTitle("Iniciar Sesión");
        form.setUsername("Usuario / Documento");
        form.setPassword("Contraseña");
        form.setSubmit("Ingresar");
        form.setForgotPassword("Recuperar contraseña");
        i18n.setForm(form);

        setI18n(i18n);
        setOpened(true);
    }

    private void showCombinedDialog() {
        Dialog dialog = new Dialog();
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);

        // Contenido de las pestañas
        VerticalLayout recoveryContent = createRecoveryContent(dialog);

        recoveryContent.setVisible(true);

        dialog.add( recoveryContent);
        dialog.open();
    }

    private VerticalLayout createRecoveryContent(Dialog dialog) {
        EmailField emailField = new EmailField("Correo electrónico");
        emailField.setWidthFull();
        emailField.setRequired(true);

        // Generar nuevo desafío CAPTCHA para recuperación
        CaptchaService.CaptchaChallenge recoveryChallenge = captchaService.generateMathChallenge();
        Span recoveryCaptchaQuestion = new Span("CAPTCHA: " + recoveryChallenge.question());
        TextField recoveryCaptchaAnswer = new TextField("Respuesta");
        recoveryCaptchaAnswer.setRequired(true);

        Button submitButton = new Button("Recuperar contraseña", e -> {
            if (emailField.isEmpty() || emailField.isInvalid()) {
                Notification.show("Ingrese un correo válido");
                return;
            } else if (!captchaService.validateChallenge(recoveryCaptchaAnswer.getValue(), recoveryChallenge.answer())) {
                Notification.show("Respuesta CAPTCHA incorrecta", 3000, Notification.Position.MIDDLE);
                return;
            }

            recoveryPassword(emailField.getValue());
            dialog.close();
        });

        VerticalLayout layout = new VerticalLayout(
                new Paragraph("Ingrese su correo para recuperar su cuenta"),
                emailField,
                recoveryCaptchaQuestion,
                recoveryCaptchaAnswer,
                submitButton
        );
        layout.setSpacing(true);
        layout.setPadding(false);
        layout.setWidth("400px");

        return layout;
    }





    private void recoveryPassword(String email) {
        try {
            Optional<User> userOptional = userService.findByCorreo(email);
            if (userOptional.isEmpty()) {
                // Mismo mensaje aunque no exista el usuario (por seguridad)
                Notification.show("Si el correo está registrado, recibirá instrucciones en breve",
                        3000, Notification.Position.MIDDLE);
                return;
            }

            User user = userOptional.get();
            String tempPassword = generateRandomPassword();

            // 1. Actualizar contraseña en base de datos
            userService.updatePassword(user, tempPassword);

            // 2. Enviar email con la nueva contraseña
            emailService.sendPasswordRecoveryEmail(user.getCorreo(), tempPassword);

            Notification.show("Se ha enviado una contraseña temporal a tu correo",
                    5000, Notification.Position.MIDDLE);

        } catch (Exception e) {
            Notification.show("Error al procesar la solicitud. Intente nuevamente más tarde.",
                    3000, Notification.Position.MIDDLE);
        }
    }

    private String generateRandomPassword() {
        String upper = RandomStringUtils.random(2, 65, 90, true, true); // 2 letras mayúsculas
        String lower = RandomStringUtils.random(2, 97, 122, true, true); // 2 letras minúsculas
        String numbers = RandomStringUtils.randomNumeric(2); // 2 números
        String special = RandomStringUtils.random(2, 33, 47, false, false); // 2 caracteres especiales

        String combined = upper + lower + numbers + special;
        char[] password = combined.toCharArray();

        // Mezclar los caracteres para mayor seguridad
        Random random = new Random();
        for (int i = 0; i < password.length; i++) {
            int randomIndex = random.nextInt(password.length);
            char temp = password[i];
            password[i] = password[randomIndex];
            password[randomIndex] = temp;
        }

        return new String(password);
    }
    @Override
    public void beforeEnter(BeforeEnterEvent event) {

        if (authenticatedUser.get().isPresent()) {
            setOpened(false);
            event.forwardTo("");
        }
        setError(event.getLocation().getQueryParameters().getParameters().containsKey("error"));
    }
}