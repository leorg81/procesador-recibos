package org.vaadin.example.services;

import jakarta.mail.*;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vaadin.example.data.*;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReciboCorreoListener {

    @Value("${mail.imap.host}")
    private String imapHost;

    @Value("${mail.imap.port}")
    private int imapPort;

    @Value("${mail.imap.user}")
    private String imapUser;

    @Value("${mail.imap.password}")
    private String imapPassword;

    @Value("${mail.imap.folder}")
    private String imapFolder;

    private final ReciboProcesadoRepository reciboRepo;
    private final EmailService emailService;
    private final FuncionarioRepository funcionarioRepo;
    private final TipoReciboRepository tipoReciboRepo;

    // Mapeo de códigos numéricos a tipos de recibo
    private Map<String, TipoRecibo> tiposPorCodigoNumCache;
    private List<TipoRecibo> tiposReciboCache;

    public ReciboCorreoListener(ReciboProcesadoRepository reciboRepo,
                                EmailService emailService,
                                FuncionarioRepository funcionarioRepo,
                                TipoReciboRepository tipoReciboRepo) {
        this.reciboRepo = reciboRepo;
        this.emailService = emailService;
        this.funcionarioRepo = funcionarioRepo;
        this.tipoReciboRepo = tipoReciboRepo;
    }

    @PostConstruct
    public void init() {
        inicializarCaches();
    }

    private void inicializarCaches() {
        // Cargar todos los tipos de recibo desde BD
        tiposReciboCache = tipoReciboRepo.findAllByOrderByNombreAsc();
        tiposPorCodigoNumCache = new HashMap<>();

        for (TipoRecibo tipo : tiposReciboCache) {
            // Usamos el código (que ahora es numérico como string)
            tiposPorCodigoNumCache.put(tipo.getCodigo(), tipo);
        }

        System.out.println("✅ Tipos de recibo cargados desde BD (códigos numéricos):");
        for (TipoRecibo tipo : tiposReciboCache) {
            System.out.println("   - Código: " + tipo.getCodigo() + " -> Nombre: " + tipo.getNombre());
        }
    }

    private String generarPatronRegex() {
        if (tiposReciboCache.isEmpty()) {
            return "a^"; // Patrón que nunca coincide
        }

        // Crear lista de códigos numéricos para el patrón
        String patronCodigos = tiposReciboCache.stream()
                .map(TipoRecibo::getCodigo)
                .map(codigo -> codigo.replaceAll("([\\[\\]\\{\\}\\$\\(\\)\\*\\+\\?\\.\\|])", "\\\\$1"))
                .collect(Collectors.joining("|"));

        return "(" + patronCodigos + ")-\\d{2}-\\d{4}";
    }

    private boolean esTipoValido(String codigoSolicitado) {
        return tiposPorCodigoNumCache.containsKey(codigoSolicitado.trim());
    }

    /**
     * Obtiene el tipo de recibo por código numérico
     */
    private TipoRecibo obtenerTipoPorCodigo(String codigo) {
        return tiposPorCodigoNumCache.get(codigo.trim());
    }

    /**
     * Obtiene tipos válidos para mostrar en mensajes de error
     */
    private List<String> obtenerCodigosValidosParaCorreo() {
        return tiposReciboCache.stream()
                .map(TipoRecibo::getCodigo)
                .collect(Collectors.toList());
    }

    @Scheduled(fixedDelay = 120000)
    public void revisarYResponder() {
        System.out.println("🔁 Revisando correos...");

        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imap.auth.mechanisms", "LOGIN PLAIN");
            System.setProperty("mail.debug", "false");
            Session session = Session.getInstance(props);
            Store store = session.getStore();
            store.connect(imapHost, imapUser, imapPassword);

            Folder inbox = store.getFolder(imapFolder);
            inbox.open(Folder.READ_WRITE);

            Message[] mensajes = inbox.getMessages();
            for (Message mensaje : mensajes) {
                if (!mensaje.isSet(Flags.Flag.SEEN)) {
                    String from = ((InternetAddress) mensaje.getFrom()[0]).getAddress();
                    String asunto = mensaje.getSubject();

                    System.out.println("📨 Nuevo mensaje de: " + from + " | Asunto: " + asunto);
                    procesarMensaje(from, asunto);

                    mensaje.setFlag(Flags.Flag.SEEN, true);
                }
            }

            inbox.close(false);
            store.close();
        } catch (Exception e) {
            System.err.println("❌ Error revisando correos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void procesarMensaje(String remitente, String asunto) {
        try {
            System.out.println("📧 Buscando funcionario por correo: " + remitente);
            Optional<Funcionario> funcionarioOpt = funcionarioRepo.findByCorreoIgnoreCase(remitente);

            if (funcionarioOpt.isPresent()) {
                System.out.println("🔍 Funcionario encontrado: " +
                        funcionarioOpt.get().getNombre() + " (CI: " + funcionarioOpt.get().getCi() + ")");
            } else {
                System.out.println("🔍 Funcionario NO encontrado para correo: " + remitente);
            }

            // Verificar formato del asunto
            String patron = generarPatronRegex();

            if (!asunto.matches(patron)) {
                List<String> codigosValidos = obtenerCodigosValidosParaCorreo();

                String mensajeError = "El asunto debe tener el formato: [Código Recibo]-[Mes]-[Año]\n\n" +
                        "Ejemplos:\n";

                // Agregar ejemplos con los primeros códigos
                for (int i = 0; i < Math.min(2, codigosValidos.size()); i++) {
                    String codigoEjemplo = codigosValidos.get(i);
                    String nombreEjemplo = obtenerTipoPorCodigo(codigoEjemplo) != null ?
                            obtenerTipoPorCodigo(codigoEjemplo).getNombre() : codigoEjemplo;
                    mensajeError += "• " + codigoEjemplo + "-11-2024 (Recibo " + nombreEjemplo + ")\n";
                }

                if (!codigosValidos.isEmpty()) {
                    String listaTipos = tiposReciboCache.stream()
                            .map(tipo -> tipo.getCodigo() + " = " + tipo.getNombre())
                            .collect(Collectors.joining("\n"));

                    mensajeError += "\nCódigos de recibo válidos:\n" + listaTipos +
                            "\n\nEjemplo de asunto correcto:\n" + codigosValidos.get(0) + "-02-2026";
                }

                emailService.sendEmail(
                        remitente,
                        "Formato incorrecto del asunto",
                        mensajeError
                );

                System.out.println("❌ Formato de asunto incorrecto: " + asunto);
                return;
            }

            String[] partes = asunto.split("-");
            String codigoSolicitado = partes[0].trim();
            String mes = partes[1].trim();
            String anio = partes[2].trim();
            String mesAnio = mes + anio;

            // Verificar que el código solicitado existe
            TipoRecibo tipoRecibo = obtenerTipoPorCodigo(codigoSolicitado);
            if (tipoRecibo == null) {
                List<String> codigosValidos = obtenerCodigosValidosParaCorreo();
                String listaTipos = tiposReciboCache.stream()
                        .map(tipo -> tipo.getCodigo() + " = " + tipo.getNombre())
                        .collect(Collectors.joining("\n"));

                emailService.sendEmail(
                        remitente,
                        "Código de recibo no válido",
                        "El código de recibo '" + codigoSolicitado + "' no es válido.\n\n" +
                                "Códigos de recibo válidos:\n" + listaTipos +
                                "\n\nRecuerda usar solo el código numérico."
                );

                System.out.println("❌ Código de recibo no válido: " + codigoSolicitado);
                return;
            }

            if (funcionarioOpt.isEmpty()) {
                emailService.sendEmail(
                        remitente,
                        "Funcionario no encontrado",
                        "Tu dirección de correo no está registrada en el sistema.\n" +
                                "Comunicate con Recursos Humanos para actualizar tus datos.\n\n" +
                                "Correo no registrado: " + remitente
                );

                System.out.println("❌ Funcionario no encontrado para correo: " + remitente);
                return;
            }

            Funcionario funcionario = funcionarioOpt.get();
            String ci = funcionario.getCi();

            System.out.println("🔍 Buscando recibo:");
            System.out.println("   - Código: " + tipoRecibo.getCodigo() + " (" + tipoRecibo.getNombre() + ")");
            System.out.println("   - Mes/Año: " + mesAnio);
            System.out.println("   - CI: " + ci);

            // Buscar recibo en BD usando el código del tipo
            List<ReciboProcesado> recibos = reciboRepo.findByTipoAndMesAnioAndCi(
                    tipoRecibo.getCodigo(),
                    mesAnio,
                    ci
            );

            if (!recibos.isEmpty()) {
                ReciboProcesado recibo = recibos.get(0);
                File archivo = new File(recibo.getRutaArchivo());

                if (!archivo.exists()) {
                    String mensajeError = "El recibo solicitado existe en la base de datos pero no se encuentra el archivo.\n" +
                            "Por favor, contacta al departamento de Sistemas.\n\n" +
                            "Detalles:\n" +
                            "• Tipo: " + tipoRecibo.getNombre() + " (Código: " + tipoRecibo.getCodigo() + ")\n" +
                            "• Período: " + mes + "/" + anio + "\n" +
                            "• CI: " + ci + "\n" +
                            "• Archivo: " + recibo.getNombreArchivo() + "\n" +
                            "• Ruta: " + recibo.getRutaArchivo();

                    emailService.sendEmail(
                            remitente,
                            "Recibo no disponible en el sistema",
                            mensajeError
                    );

                    System.out.println("❌ Archivo no encontrado en ruta: " + recibo.getRutaArchivo());
                    return;
                }

                emailService.sendEmailWithAttachment(
                        remitente,
                        "Tu recibo: " + tipoRecibo.getNombre() + " - " + mes + "/" + anio,
                        "Hola " + funcionario.getNombre() + ",\n\n" +
                                "Adjuntamos tu recibo solicitado:\n" +
                                "• Tipo: " + tipoRecibo.getNombre() + " (Código: " + tipoRecibo.getCodigo() + ")\n" +
                                "• Período: " + mes + "/" + anio + "\n" +
                                "• CI: " + ci + "\n\n" +
                                "Saludos cordiales,\n" +
                                "Departamento de Recursos Humanos",
                        archivo
                );

                System.out.println("✅ Recibo enviado a " + remitente);
                System.out.println("   - Tipo: " + tipoRecibo.getNombre() + " (" + tipoRecibo.getCodigo() + ")");
                System.out.println("   - Período: " + mes + "/" + anio);
                System.out.println("   - Archivo: " + recibo.getNombreArchivo());

            } else {
                String mensajeNoEncontrado = "No se encontró ningún recibo con los datos proporcionados.\n\n" +
                        "Detalles de búsqueda:\n" +
                        "• Tipo: " + tipoRecibo.getNombre() + " (Código: " + tipoRecibo.getCodigo() + ")\n" +
                        "• Período: " + mes + "/" + anio + "\n" +
                        "• CI: " + ci + "\n" +
                        "• Nombre: " + funcionario.getNombre() + "\n\n" +
                        "Posibles causas:\n" +
                        "1. El recibo aún no ha sido procesado\n" +
                        "2. Los datos no coinciden exactamente\n" +
                        "3. El recibo corresponde a otra localidad\n\n" +
                        "Verifica que los datos sean correctos o contacta a Recursos Humanos.";

                emailService.sendEmail(
                        remitente,
                        "Recibo no encontrado",
                        mensajeNoEncontrado
                );

                System.out.println("❌ Recibo no encontrado para los criterios especificados");
            }

        } catch (Exception ex) {
            System.err.println("❌ Error procesando mensaje:");
            System.err.println("   - Remitente: " + remitente);
            System.err.println("   - Asunto: " + asunto);
            System.err.println("   - Error: " + ex.getMessage());
            ex.printStackTrace();

            // Enviar correo de error al administrador
            try {
                emailService.sendEmail(
                        "admin@tuempresa.com",
                        "Error en procesamiento de recibo",
                        "Error al procesar solicitud de recibo:\n" +
                                "• Remitente: " + remitente + "\n" +
                                "• Asunto: " + asunto + "\n" +
                                "• Error: " + ex.getMessage() + "\n" +
                                "• Hora: " + new Date()
                );
            } catch (Exception e) {
                System.err.println("❌ Error enviando correo de error: " + e.getMessage());
            }
        }
    }

    /**
     * Método para debug/verificación de configuración
     */
    public Map<String, Object> obtenerConfiguracion() {
        Map<String, Object> config = new HashMap<>();
        config.put("tiposRecibo", tiposReciboCache);
        config.put("codigosDisponibles", tiposPorCodigoNumCache.keySet());
        config.put("patronRegex", generarPatronRegex());
        return config;
    }
}