package org.vaadin.example.services;


import java.io.File;

public interface EmailService {

    void sendEmail(String to, String subject, String body);

    void sendPasswordRecoveryEmail(String to, String temporaryPassword);

    void sendAccountActivationEmail(String to, String username, String activationLink);

    void sendEmailWithAttachment(String to, String subject, String body, File attachment);

}