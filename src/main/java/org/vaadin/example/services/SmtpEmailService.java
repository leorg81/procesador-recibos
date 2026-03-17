package org.vaadin.example.services;


import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;


@Service
@Slf4j
public class SmtpEmailService implements EmailService {

    private  final JavaMailSender mailSender;

    private static final String APP_NAME = "Sistema de Gestión de Turnos";


    @Value("${spring.mail.username}")
    private String fromEmail;

    public SmtpEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;

        // Debug: Verifica configuración cargada
        if(mailSender instanceof JavaMailSenderImpl) {
            JavaMailSenderImpl impl = (JavaMailSenderImpl) mailSender;
            System.out.println("Configuración SMTP:");
            System.out.println("Host: " + impl.getHost());
            System.out.println("Port: " + impl.getPort());
            System.out.println("Username: " + impl.getUsername());
        }
    }
    @Override
    @Async
    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            log.info("Configuración SMTP detectada: {}",
                    ((JavaMailSenderImpl)mailSender).getHost());

            log.info("Enviando correo a: {} - Asunto: {}", to, subject);
            mailSender.send(message);
            log.info("Correo enviado exitosamente a: {}", to);
        } catch (Exception e) {
            log.error("Error al enviar correo a {}: {}", to, e.getMessage());
            throw new RuntimeException("Error al enviar correo: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendPasswordRecoveryEmail(String to, String temporaryPassword) {
        String subject = String.format("%s - Recuperación de Contraseña", APP_NAME);
        String body = String.format(
                "Hola,\n\n" +
                        "Has solicitado un restablecimiento de contraseña para tu cuenta en %s.\n\n" +
                        "Tu contraseña temporal es: %s\n\n" +
                        "Por favor inicia sesión con esta contraseña y cámbiala inmediatamente en la configuración de tu perfil.\n\n" +
                        "Si no solicitaste esto, por favor ignora este correo.\n\n" +
                        "Saludos,\n" +
                        "El equipo de %s",
                APP_NAME, temporaryPassword, APP_NAME
        );

        sendEmail(to, subject, body);
    }



    @Override
    public void sendAccountActivationEmail(String to, String username, String activationLink) {
        String subject = String.format("Activa tu cuenta en %s", APP_NAME);
        String body = String.format(
                "Hola %s,\n\n" +
                        "Gracias por registrarte en %s. Por favor activa tu cuenta haciendo clic en el siguiente enlace:\n\n" +
                        "%s\n\n" +
                        "Este enlace expirará en 24 horas.\n\n" +
                        "Si no solicitaste este registro, por favor ignora este correo.\n\n" +
                        "Saludos,\n" +
                        "El equipo de %s",
                username, APP_NAME, activationLink, APP_NAME
        );

        sendEmail(to, subject, body);
    }

    @Async
    public void sendEmailWithAttachment(String to, String subject, String body, File attachment) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true); // true = multipart

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);

            if (attachment != null && attachment.exists()) {
                helper.addAttachment(attachment.getName(), attachment);
            }

            log.info("Enviando correo con adjunto a: {} - Asunto: {}", to, subject);
            mailSender.send(message);
            log.info("Correo con adjunto enviado exitosamente a: {}", to);

        } catch (Exception e) {
            log.error("Error al enviar correo con adjunto a {}: {}", to, e.getMessage());
            throw new RuntimeException("Error al enviar correo con adjunto: " + e.getMessage(), e);
        }
    }


}