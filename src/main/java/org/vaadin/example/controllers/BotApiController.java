package org.vaadin.example.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.vaadin.example.data.Funcionario;
import org.vaadin.example.data.ReciboProcesado;
import org.vaadin.example.data.TipoRecibo;
import org.vaadin.example.services.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bot")
@CrossOrigin(origins = "*")
public class BotApiController {

    private final ReciboService reciboService;
    private final FuncionarioService funcionarioService;
    private final TipoReciboService tipoReciboService;
    private final EmailService emailService;
    private final Map<String, Integer> intentosWhatsapp = new HashMap<>();

    @Value("${whatsapp.server.url:http://localhost:3000}")
    private String whatsappServerUrl;

    @Value("${spring.application.name:SpringBootApp}")
    private String appName;

    @Value("${app.public.url.telegram:http://localhost:8080}")
    private String telegramPublicUrl;

    @Value("${app.public.url.whatsapp:http://localhost:8080}")
    private String whatsappPublicUrl;



    public BotApiController(ReciboService reciboService,
                            FuncionarioService funcionarioService,
                            TipoReciboService tipoReciboService,
                            EmailService emailService) {
        this.reciboService = reciboService;
        this.funcionarioService = funcionarioService;
        this.tipoReciboService = tipoReciboService;
        this.emailService = emailService;
    }

    /**
     * ENDPOINT PRINCIPAL: Procesa mensajes de WhatsApp
     * POST /api/whatsapp/mensaje
     */
    @PostMapping("/whatsapp/mensaje")
    public ResponseEntity<Map<String, Object>> procesarMensajeWhatsApp(
            @RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("sessionId", request.get("sessionId"));
        response.put("serverId", request.get("serverId"));
        response.put("application", appName);

        String telefono = request.get("telefono");
        String mensaje = request.get("mensaje");
        String plataforma = "WHATSAPP";

        // Log de entrada
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📨 MENSAJE WHATSAPP RECIBIDO:");
        System.out.println("   📱 Teléfono/LID: " + telefono);
        System.out.println("   💬 Mensaje: " + mensaje);
        System.out.println("   🆔 Session: " + request.get("sessionId"));
        System.out.println("   🖥️ Server: " + request.get("serverId"));
        System.out.println("=".repeat(70));

        try {
            // PASO 1: Detectar si es LID (número muy largo) o teléfono normal

            //boolean esLid = telefono != null && telefono.length() >= 15;
            boolean esLid = telefono != null && (
                    telefono.length() >= 15 ||
                            telefono.contains("@lid") ||
                            !telefono.matches("\\+?\\d{10,13}") // No coincide con formato de teléfono
            );

            System.out.println("🔍 Tipo de identificador: " + (esLid ? "LID" : "TELÉFONO"));

            Optional<Funcionario> funcionarioOpt = Optional.empty();

            if (esLid) {
                // Buscar por LID (igual que Telegram busca por chatId)
                System.out.println("   🔍 Buscando por LID: " + telefono);
                funcionarioOpt = funcionarioService.buscarPorWhatsappLid(telefono);

                if (funcionarioOpt.isPresent()) {
                    System.out.println("   ✅ LID encontrado en BD");
                } else {
                    System.out.println("   ❌ LID no registrado");
                }
            } else {
                // Es teléfono normal - buscar por teléfono (con normalización)
                String telefonoNormalizado = normalizarTelefono(telefono);
                System.out.println("   🔧 Teléfono normalizado: " + telefonoNormalizado);

                funcionarioOpt = funcionarioService.buscarPorTelefono(telefonoNormalizado);

                // Fallback: buscar con formatos alternativos
                if (funcionarioOpt.isEmpty()) {
                    if (!telefonoNormalizado.startsWith("+")) {
                        String conMas = "+" + telefonoNormalizado;
                        System.out.println("   🔍 Buscando con +: " + conMas);
                        funcionarioOpt = funcionarioService.buscarPorTelefono(conMas);
                    } else {
                        String sinMas = telefonoNormalizado.substring(1);
                        System.out.println("   🔍 Buscando sin +: " + sinMas);
                        funcionarioOpt = funcionarioService.buscarPorTelefono(sinMas);
                    }
                }
            }

            // PASO 2: Verificar si existe el funcionario
            if (funcionarioOpt.isEmpty()) {
                System.out.println("   ❌ Funcionario no encontrado");

                if (esLid) {
                    // Para LID no registrado, iniciar flujo de activación (igual que Telegram)
                    return crearRespuestaActivacionWhatsapp(telefono);
                } else {
                    // Para teléfono no registrado, mensaje de error
                    return crearRespuestaError(
                            "❌ No estás registrado en el sistema.\n" +
                                    "Contacta a Recursos Humanos para actualizar tus datos.",
                            "NO_REGISTRADO", null);
                }
            }

            Funcionario funcionario = funcionarioOpt.get();
            System.out.println("   ✅ Funcionario encontrado: " + funcionario.getNombre());
            System.out.println("   🆔 CI: " + funcionario.getCi());

            // PASO 3: Para LID, verificar si está activado (igual que Telegram)
            if (esLid) {
                if (!funcionario.getWhatsappActivado()) {
                    System.out.println("   ⚠️ WhatsApp no activado para este funcionario");
                    return crearRespuestaActivacionPendienteWhatsapp(funcionario);
                }
                System.out.println("   ✅ WhatsApp activado para: " + funcionario.getNombre());
            }

            // PASO 4: Procesar mensaje normalmente
            String mensajeNormalizado = mensaje.trim().toLowerCase();

            // Si el mensaje es un código de 6 dígitos y estamos en proceso de activación
            if (esLid && mensajeNormalizado.matches("\\d{6}") &&
                    !funcionario.getWhatsappActivado() &&
                    funcionario.getWhatsappCodigoActivacion() != null) {

                System.out.println("   🔢 Procesando código de activación: " + mensajeNormalizado);
                Map<String, String> activacionRequest = new HashMap<>();
                activacionRequest.put("lid", telefono);
                activacionRequest.put("codigo", mensajeNormalizado);
                return completarActivacionWhatsapp(activacionRequest);
            }



            // Comandos normales


            // Verificar contexto especial
            String contexto = request.get("contexto");
            if ("seleccion_tipo".equals(contexto)) {
                // Si el bot envía contexto, ignorar comandos y tratar como selección de tipo
                String tipoSeleccionado = request.get("mensaje"); // el mensaje contiene el número del tipo
                if (tipoSeleccionado != null && tipoSeleccionado.matches("\\d+")) {
                    return listarRecibosPorTipo(funcionario, tipoSeleccionado);
                } else {
                    // Si no es válido, devolver error
                    return crearRespuestaError("Selección de tipo inválida", "CONTEXTO_INVALIDO", null);
                }
            }


            if (mensajeNormalizado.equals("menu") || mensajeNormalizado.equals("inicio")) {
                System.out.println("   📋 Comando: MENU");
                return crearMenuPrincipal(funcionario, plataforma);
            }
            else if (mensajeNormalizado.equals("recibos") || mensajeNormalizado.equals("1")) {
                System.out.println("   📋 Comando: RECIBOS");
                return listarTiposRecibo(funcionario, plataforma);
            }
            else if (mensajeNormalizado.equals("tipos") || mensajeNormalizado.equals("2")) {
                System.out.println("   📋 Comando: TIPOS");
                return listarTodosTipos(funcionario, plataforma);
            }
            else if (mensajeNormalizado.equals("ayuda") || mensajeNormalizado.equals("help")) {
                System.out.println("   📋 Comando: AYUDA");
                return crearRespuestaAyuda(funcionario, plataforma);
            }
            else if (mensajeNormalizado.equals("estado") || mensajeNormalizado.equals("4")) {
                System.out.println("   📋 Comando: ESTADO");
                return mostrarInfoFuncionario(funcionario, plataforma);
            }
            else if (mensajeNormalizado.equals("3")) {
                System.out.println("   📋 Comando: AYUDA (por número)");
                return crearRespuestaAyuda(funcionario, plataforma);
            }
            else if (mensajeNormalizado.matches("\\d+")) {
                System.out.println("   🔢 Selección numérica: " + mensajeNormalizado);
                return procesarSeleccionNumerica(funcionario, mensajeNormalizado, plataforma);
            }
            else if (mensajeNormalizado.matches("\\d+-\\d{2}-\\d{4}")) {
                System.out.println("   📅 Solicitud recibo: " + mensajeNormalizado);
                return procesarSolicitudRecibo(funcionario, mensajeNormalizado, plataforma);
            }
            else if (mensajeNormalizado.matches("\\d{2}-\\d{4}")) {
                System.out.println("   📅 Listar por mes: " + mensajeNormalizado);
                return listarRecibosPorMes(funcionario, mensajeNormalizado, plataforma);
            }
            else {
                System.out.println("   🤔 Mensaje no entendido");
                return crearRespuestaDefault(funcionario, plataforma);
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR procesando mensaje WhatsApp: " + e.getMessage());
            e.printStackTrace();
            return crearRespuestaError(
                    "⚠️ Error en el servidor. Intenta más tarde.",
                    "ERROR_SERVIDOR", e);
        }
    }


    /**
     * ENDPOINT PRINCIPAL para Telegram
     * POST /api/bot/telegram/mensaje
     */
    @PostMapping("/telegram/mensaje")
    public ResponseEntity<Map<String, Object>> procesarMensajeTelegram(
            @RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("application", appName);
        response.put("plataforma", "TELEGRAM");

        String chatId = request.get("chatId");
        String mensaje = request.get("mensaje");
        String sessionId = request.get("sessionId");

        System.out.println("\n📨 MENSAJE TELEGRAM RECIBIDO:");
        System.out.println("   Chat ID: " + chatId);
        System.out.println("   Mensaje: " + mensaje);
        System.out.println("   Session: " + sessionId);

        try {
            // 1. Verificar si el chatId está asociado a un funcionario
            Optional<Funcionario> funcionarioOpt = funcionarioService
                    .buscarPorTelegramChatId(chatId);

            if (funcionarioOpt.isEmpty()) {
                System.out.println("   ❌ Chat ID no registrado");
                return crearRespuestaAutenticacionNecesaria(chatId);
            }

            Funcionario funcionario = funcionarioOpt.get();

            // 2. Verificar si Telegram está activado
            if (!funcionario.getTelegramActivado()) {
                System.out.println("   ⚠️ Telegram no activado para este funcionario");
                return crearRespuestaActivacionNecesaria(funcionario);
            }

            // 3. Procesar el mensaje (misma lógica que WhatsApp)
            return procesarMensajeFuncionario(funcionario, mensaje, "TELEGRAM");

        } catch (Exception e) {
            System.err.println("❌ ERROR procesando mensaje Telegram: " + e.getMessage());
            e.printStackTrace();
            return crearRespuestaError(
                    "⚠️ Error en el servidor. Intenta más tarde.",
                    "ERROR_SERVIDOR", e);
        }
    }

    /**
     * Método común para procesar mensajes de funcionarios autenticados
     */
    private ResponseEntity<Map<String, Object>> procesarMensajeFuncionario(
            Funcionario funcionario, String mensaje, String plataforma) {

        String mensajeNormalizado = mensaje.trim().toLowerCase();

        // Lógica común para ambas plataformas
        if (mensajeNormalizado.equals("menu") || mensajeNormalizado.equals("inicio")) {
            System.out.println("   📋 Comando: MENU");
            return crearMenuPrincipal(funcionario, plataforma);
        }
        else if (mensajeNormalizado.equals("recibos") || mensajeNormalizado.equals("1")) {
            System.out.println("   📋 Comando: RECIBOS");
            return listarTiposRecibo(funcionario, plataforma);
        }
        else if (mensajeNormalizado.equals("tipos") || mensajeNormalizado.equals("2")) {
            System.out.println("   📋 Comando: TIPOS");
            return listarTodosTipos(funcionario, plataforma);
        }
        else if (mensajeNormalizado.equals("ayuda") || mensajeNormalizado.equals("help")) {
            System.out.println("   📋 Comando: AYUDA");
            return crearRespuestaAyuda(funcionario, plataforma);
        }
        else if (mensajeNormalizado.equals("estado") || mensajeNormalizado.equals("4")) {
            System.out.println("   📋 Comando: ESTADO");
            return mostrarInfoFuncionario(funcionario, plataforma);
        }
        else if (mensajeNormalizado.matches("\\d+")) {
            System.out.println("   🔢 Selección numérica: " + mensajeNormalizado);
            return procesarSeleccionNumerica(funcionario, mensajeNormalizado, plataforma);
        }
        else if (mensajeNormalizado.matches("\\d+-\\d{2}-\\d{4}")) {
            System.out.println("   📅 Solicitud recibo: " + mensajeNormalizado);
            return procesarSolicitudRecibo(funcionario, mensajeNormalizado, plataforma);
        }
        else if (mensajeNormalizado.matches("\\d{2}-\\d{4}")) {
            System.out.println("   📅 Listar por mes: " + mensajeNormalizado);
            return listarRecibosPorMes(funcionario, mensajeNormalizado, plataforma);
        }
        else {
            System.out.println("   🤔 Mensaje no entendido");
            return crearRespuestaDefault(funcionario, plataforma);
        }
    }

    // ==================== AUTENTICACIÓN TELEGRAM ====================

    /**
     * Iniciar proceso de activación de Telegram
     * POST /api/bot/telegram/iniciar-activacion
     */
    @PostMapping("/telegram/iniciar-activacion")
    public ResponseEntity<Map<String, Object>> iniciarActivacionTelegram(
            @RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("plataforma", "TELEGRAM");

        String chatId = request.get("chatId");
        String correo = request.get("correo");

        System.out.println("\n🔐 INICIANDO ACTIVACIÓN TELEGRAM:");
        System.out.println("   Chat ID: " + chatId);
        System.out.println("   Correo: " + correo);

        try {
            // 1. Buscar funcionario por correo
            Optional<Funcionario> funcionarioOpt = funcionarioService
                    .buscarPorCorreo(correo);

            if (funcionarioOpt.isEmpty()) {
                System.out.println("   ❌ Correo no registrado");
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "Correo no registrado",
                        "mensaje", "El correo no está registrado en el sistema."
                ));
            }

            Funcionario funcionario = funcionarioOpt.get();

            // 2. Verificar si ya está activado
            if (funcionario.isTelegramActivo()) {
                System.out.println("   ⚠️ Telegram ya está activado para este funcionario");
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "ya_activado", true,
                        "mensaje", "Tu cuenta de Telegram ya está activada.",
                        "funcionario", funcionario.getNombre()
                ));
            }

            // 3. Generar código de activación (6 dígitos)
            String codigo = generarCodigo6Digitos();

            // 4. Actualizar funcionario
            funcionario.setTelegramChatId(chatId);
            funcionario.setTelegramCodigoActivacion(codigo);
            funcionario.setTelegramCodigoExpiracion(
                    LocalDateTime.now().plusMinutes(15));
            funcionario.setTelegramActivado(false);

            funcionarioService.guardar(funcionario);

            // 5. Enviar correo con código
            String asunto = "Código de activación para Telegram";
            String cuerpo = "Hola " + funcionario.getNombre() + ",\n\n" +
                    "Has solicitado activar tu cuenta de Telegram para recibir recibos.\n\n" +
                    "Tu código de activación es: *" + codigo + "*\n\n" +
                    "Este código expirará en 15 minutos.\n\n" +
                    "Para completar la activación, envía el código al bot de Telegram.\n\n" +
                    "Saludos cordiales,\n" +
                    "Sistema de Recibos";

            emailService.sendEmail(funcionario.getCorreo(), asunto, cuerpo);

            System.out.println("   ✅ Código generado y enviado por correo");
            System.out.println("   👤 Funcionario: " + funcionario.getNombre());
            System.out.println("   📧 Correo enviado a: " + funcionario.getCorreo());

            response.put("success", true);
            response.put("mensaje", "Código enviado al correo registrado");
            response.put("funcionario", funcionario.getNombre());
            response.put("correo_enviado_a", funcionario.getCorreo());
            response.put("expiracion_minutos", 15);

        } catch (Exception e) {
            System.err.println("❌ ERROR iniciando activación: " + e.getMessage());
            e.printStackTrace();
            return crearRespuestaError(
                    "⚠️ Error iniciando activación de Telegram.",
                    "ERROR_ACTIVACION", e);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Completar activación de Telegram con código
     * POST /api/bot/telegram/completar-activacion
     */
    @PostMapping("/telegram/completar-activacion")
    public ResponseEntity<Map<String, Object>> completarActivacionTelegram(
            @RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("plataforma", "TELEGRAM");

        String chatId = request.get("chatId");
        String codigo = request.get("codigo");

        System.out.println("\n🔐 COMPLETANDO ACTIVACIÓN TELEGRAM:");
        System.out.println("   Chat ID: " + chatId);
        System.out.println("   Código recibido: " + codigo);

        try {
            // 1. Buscar funcionario por chatId
            Optional<Funcionario> funcionarioOpt = funcionarioService
                    .buscarPorTelegramChatId(chatId);
            if (funcionarioOpt.isEmpty()) {
                System.out.println("   ❌ Chat ID no encontrado");
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "error", "Sesión no encontrada",
                        "mensaje", "No se encontró una solicitud de activación. Inicia el proceso nuevamente."
                ));
            }

            Funcionario funcionario = funcionarioOpt.get();

            // 2. Verificar si el código es correcto
            if (!codigo.equals(funcionario.getTelegramCodigoActivacion())) {
                System.out.println("   ❌ Código incorrecto");
                return ResponseEntity.status(400).body(Map.of(
                        "success", false,
                        "error", "Código incorrecto",
                        "mensaje", "El código ingresado no es correcto. Intenta nuevamente."
                ));
            }

            // 3. Verificar si el código está expirado
            if (funcionario.isCodigoActivacionExpirado()) {
                System.out.println("   ❌ Código expirado");
                return ResponseEntity.status(400).body(Map.of(
                        "success", false,
                        "error", "Código expirado",
                        "mensaje", "El código ha expirado. Solicita uno nuevo."
                ));
            }

            // 4. Activar cuenta
            funcionario.setTelegramActivado(true);
            funcionario.setTelegramRegistradoEn(LocalDateTime.now());
            funcionario.setTelegramCodigoActivacion(null); // Limpiar código usado
            funcionario.setTelegramCodigoExpiracion(null);

            funcionarioService.guardar(funcionario);

            System.out.println("   ✅ Cuenta activada exitosamente");
            System.out.println("   👤 Funcionario: " + funcionario.getNombre());
            System.out.println("   🆔 CI: " + funcionario.getCi());

            // 5. Preparar respuesta
            StringBuilder mensajeBienvenida = new StringBuilder();
            mensajeBienvenida.append("🎉 *¡Activación exitosa!*\n\n");
            mensajeBienvenida.append("Hola ").append(funcionario.getNombre()).append("!\n");
            mensajeBienvenida.append("Tu cuenta de Telegram ha sido activada correctamente.\n\n");
            mensajeBienvenida.append("📋 *Datos registrados:*\n");
            mensajeBienvenida.append("• *Nombre:* ").append(funcionario.getNombre()).append("\n");
            mensajeBienvenida.append("• *CI:* ").append(funcionario.getCi()).append("\n");
            mensajeBienvenida.append("• *Correo:* ").append(funcionario.getCorreo()).append("\n\n");
            mensajeBienvenida.append("📱 *Ahora puedes usar los comandos:*\n");
            mensajeBienvenida.append("• *menu* - Menú principal\n");
            mensajeBienvenida.append("• *recibos* - Ver tus recibos\n");
            mensajeBienvenida.append("• *ayuda* - Instrucciones de uso\n\n");
            mensajeBienvenida.append("👉 *Escribe *menu* para comenzar*");

            response.put("success", true);
            response.put("activado", true);
            response.put("mensaje", mensajeBienvenida.toString());
            response.put("funcionario", Map.of(
                    "nombre", funcionario.getNombre(),
                    "ci", funcionario.getCi(),
                    "correo", funcionario.getCorreo()
            ));

        } catch (Exception e) {
            System.err.println("❌ ERROR completando activación: " + e.getMessage());
            e.printStackTrace();
            return crearRespuestaError(
                    "⚠️ Error completando activación de Telegram.",
                    "ERROR_COMPLETAR_ACTIVACION", e);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Verificar estado de activación Telegram
     * GET /api/bot/telegram/estado/{chatId}
     */
    @GetMapping("/telegram/estado/{chatId}")
    public ResponseEntity<Map<String, Object>> verificarEstadoTelegram(
            @PathVariable String chatId) {

        System.out.println("\n🔍 VERIFICANDO ESTADO TELEGRAM:");
        System.out.println("   Chat ID: " + chatId);

        Optional<Funcionario> funcionarioOpt = funcionarioService
                .buscarPorTelegramChatId(chatId);

        Map<String, Object> response = new HashMap<>();
        response.put("chatId", chatId);
        response.put("timestamp", LocalDateTime.now());

        if (funcionarioOpt.isEmpty()) {
            response.put("registrado", false);
            response.put("mensaje", "Chat ID no registrado");
            return ResponseEntity.ok(response);
        }

        Funcionario funcionario = funcionarioOpt.get();
        response.put("registrado", true);
        response.put("funcionario", funcionario.getNombre());
        response.put("ci", funcionario.getCi());
        response.put("telegram_activado", funcionario.getTelegramActivado());
        response.put("telegram_registrado_en", funcionario.getTelegramRegistradoEn());

        if (funcionario.getTelegramActivado()) {
            response.put("estado", "ACTIVADO");
        } else if (funcionario.getTelegramCodigoActivacion() != null) {
            response.put("estado", "PENDIENTE_ACTIVACION");
            response.put("codigo_expiracion", funcionario.getTelegramCodigoExpiracion());
        } else {
            response.put("estado", "REGISTRADO_NO_ACTIVADO");
        }

        return ResponseEntity.ok(response);
    }

    // ==================== MÉTODOS AUXILIARES COMUNES ====================

    private ResponseEntity<Map<String, Object>> crearRespuestaAutenticacionNecesaria(String chatId) {
        Map<String, Object> response = new HashMap<>();

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("🔐 *Autenticación requerida*\n\n");
        mensaje.append("No tienes una cuenta activa asociada a este chat.\n\n");
        mensaje.append("📝 *Para activar tu cuenta:*\n");
        mensaje.append("1. Envía tu correo corporativo\n");
        mensaje.append("2. Recibirás un código por correo\n");
        mensaje.append("3. Ingresa el código aquí\n\n");
        mensaje.append("👉 *Envía tu correo para comenzar*");

        response.put("success", false);
        response.put("autenticacion_requerida", true);
        response.put("mensaje", mensaje.toString());
        response.put("tipo", "AUTENTICACION_REQUERIDA");
        response.put("paso", 1); // Indica que debe enviar correo

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> crearRespuestaActivacionNecesaria(Funcionario funcionario) {
        Map<String, Object> response = new HashMap<>();

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("🔐 *Activación pendiente*\n\n");
        mensaje.append("Hola ").append(funcionario.getNombre()).append("!\n");
        mensaje.append("Tu cuenta está registrada pero requiere activación.\n\n");
        mensaje.append("📧 *Se envió un código a:*\n");
        mensaje.append(funcionario.getCorreo()).append("\n\n");
        mensaje.append("👉 *Envía el código de 6 dígitos para activar*");

        response.put("success", false);
        response.put("activacion_pendiente", true);
        response.put("mensaje", mensaje.toString());
        response.put("tipo", "ACTIVACION_PENDIENTE");
        response.put("paso", 2); // Indica que debe enviar código
        response.put("funcionario", Map.of(
                "nombre", funcionario.getNombre(),
                "correo_oculto", ocultarCorreo(funcionario.getCorreo())
        ));

        return ResponseEntity.ok(response);
    }

    private String generarCodigo6Digitos() {
        Random random = new Random();
        int codigo = 100000 + random.nextInt(900000);
        return String.valueOf(codigo);
    }

    private String ocultarCorreo(String correo) {
        if (correo == null || correo.length() < 5) return "*****";

        int idxArroba = correo.indexOf('@');
        if (idxArroba > 3) {
            String nombre = correo.substring(0, idxArroba);
            String dominio = correo.substring(idxArroba);
            String oculto = nombre.charAt(0) + "***" + nombre.charAt(nombre.length()-1);
            return oculto + dominio;
        }
        return "*****" + correo.substring(Math.max(0, correo.length()-4));
    }

    // ==================== MÉTODOS MODIFICADOS (agregar parámetro plataforma) ====================

    private ResponseEntity<Map<String, Object>> crearMenuPrincipal(Funcionario funcionario, String plataforma) {
        // Mantener misma lógica pero agregar referencia a plataforma
        Map<String, Object> response = new HashMap<>();

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("👋 *Hola ").append(funcionario.getNombre()).append("!*\n\n");
        mensaje.append("📱 *MENÚ PRINCIPAL* (").append(plataforma).append(")\n\n");
        mensaje.append("1️⃣ *Recibos* - Ver/descargar mis recibos\n");
        mensaje.append("2️⃣ *Tipos* - Tipos de recibo disponibles\n");
        mensaje.append("3️⃣ *Ayuda* - Instrucciones de uso\n");
        mensaje.append("4️⃣ *Estado* - Ver mi información\n\n");

        if ("TELEGRAM".equals(plataforma)) {
            mensaje.append("🆔 *Tu cuenta Telegram está activa*\n\n");
        }

        mensaje.append("📝 *Ejemplos de solicitud:*\n");
        mensaje.append("• *(tipo-mes-año)*\n");
        mensaje.append("• *1-12-2025* (mensualidad dic 2025)\n");
        mensaje.append("• *2-12-2025* (aguinaldo dic 2025)\n\n");
        mensaje.append("👉 *Escribe el número de opción o comando:*");

        response.put("success", true);
        response.put("mensaje", mensaje.toString());
        response.put("opciones", Arrays.asList("1", "2", "3", "4", "1-12-2025", "12-2025"));
        response.put("tipo", "MENU_PRINCIPAL");
        response.put("plataforma", plataforma);
        response.put("funcionario", Map.of(
                "nombre", funcionario.getNombre(),
                "ci", funcionario.getCi(),
                "telefono", funcionario.getTelefono()
        ));

        return ResponseEntity.ok(response);
    }

    // ==================== MÉTODOS DE PROCESAMIENTO ====================

    private ResponseEntity<Map<String, Object>> procesarMensajePlataforma(
            Map<String, String> request, String plataforma) {

        // Esta es la lógica original de WhatsAppApiController
        // Se mantiene igual pero se parametriza la plataforma

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        response.put("application", appName);
        response.put("plataforma", plataforma);

        String telefono = request.get("telefono");
        String mensaje = request.get("mensaje");
        String sessionId = request.get("sessionId");

        System.out.println("\n📨 MENSAJE " + plataforma + " RECIBIDO:");
        System.out.println("   Identificador: " + ("WHATSAPP".equals(plataforma) ? telefono : request.get("chatId")));
        System.out.println("   Mensaje: " + mensaje);
        System.out.println("   Session: " + sessionId);

        try {
            if ("WHATSAPP".equals(plataforma)) {
                // Lógica original de WhatsApp
                String telefonoNormalizado = normalizarTelefono(telefono);
                Optional<Funcionario> funcionarioOpt = funcionarioService
                        .buscarPorTelefono(telefonoNormalizado);

                if (funcionarioOpt.isEmpty()) {
                    System.out.println("   ❌ Funcionario no encontrado");
                    return crearRespuestaError(
                            "❌ No estás registrado en el sistema.\n" +
                                    "Contacta a RecContacta a Recursosursos Humanos para actualizar tus datos.",
                            "NO_REGISTRADO", null);
                }

                Funcionario funcionario = funcionarioOpt.get();
                return procesarMensajeFuncionario(funcionario, mensaje, plataforma);
            } else {
                // Telegram ya se maneja en el otro endpoint
                return crearRespuestaError("Plataforma no soportada", "PLATAFORMA_NO_SOPORTADA", null);
            }

        } catch (Exception e) {
            System.err.println("❌ ERROR procesando mensaje " + plataforma + ": " + e.getMessage());
            e.printStackTrace();
            return crearRespuestaError(
                    "⚠️ Error en el servidor. Intenta más tarde.",
                    "ERROR_SERVIDOR", e);
        }
    }

    // ==================== ENDPOINTS DE DESCARGA (mantener igual) ====================

    /**
     * Descargar recibo específico (compatible con WhatsApp y Telegram)
     */
    @GetMapping("/descargar/{ci}/{tipo}/{mesAnio}")
    public ResponseEntity<Resource> descargarRecibo(
            @PathVariable String ci,
            @PathVariable String tipo,
            @PathVariable String mesAnio,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false) String chatId) {

        System.out.println("\n📥 SOLICITUD DESCARGA:");
        System.out.println("   CI: " + ci);
        System.out.println("   Tipo: " + tipo);
        System.out.println("   Mes/Año: " + mesAnio);
        System.out.println("   Teléfono: " + telefono);
        System.out.println("   Chat ID: " + chatId);

        try {
            // Verificar permisos
            Funcionario funcionario = null;

            // Buscar por teléfono (WhatsApp)
            if (telefono != null && !telefono.trim().isEmpty()) {
                String telefonoNormalizado = normalizarTelefono(telefono);
                System.out.println("   🔍 Buscando por teléfono: " + telefonoNormalizado);
                Optional<Funcionario> funcionarioOpt = funcionarioService
                        .buscarPorTelefono(telefonoNormalizado);
                if (funcionarioOpt.isPresent()) {
                    funcionario = funcionarioOpt.get();
                    System.out.println("   ✅ Encontrado por teléfono: " + funcionario.getNombre());
                }
            }

            // PASO 2: Si no se encontró por teléfono, buscar por LID (WhatsApp Business)
            if (funcionario == null && telefono != null && telefono.length() >= 15) {
                System.out.println("   🔍 Buscando por LID: " + telefono);
                Optional<Funcionario> funcionarioOpt = funcionarioService
                        .buscarPorWhatsappLid(telefono);  // ← NUEVA BÚSQUEDA
                if (funcionarioOpt.isPresent()) {
                    funcionario = funcionarioOpt.get();
                    System.out.println("   ✅ Encontrado por LID: " + funcionario.getNombre());
                }
            }

            // Buscar por chatId (Telegram)
            if ((funcionario == null) && chatId != null && !chatId.trim().isEmpty()) {
                Optional<Funcionario> funcionarioOpt = funcionarioService
                        .buscarPorTelegramChatId(chatId);
                if (funcionarioOpt.isPresent()) {
                    funcionario = funcionarioOpt.get();
                }
            }

            if (funcionario == null) {
                System.out.println("   ❌ Funcionario no encontrado");
                return ResponseEntity.status(403).build();
            }

            // Verificar que el CI coincide
            if (!funcionario.getCi().equals(ci)) {
                System.out.println("   ❌ CI no coincide: " + funcionario.getCi() + " != " + ci);
                return ResponseEntity.status(403).build();
            }

            // Buscar recibo
            List<ReciboProcesado> recibos = reciboService.buscarPorCiTipoYMes(ci, tipo, mesAnio);
            if (recibos.isEmpty()) {
                System.out.println("   ❌ Recibo no encontrado");
                return ResponseEntity.notFound().build();
            }

            // Tomar el primer recibo
            ReciboProcesado recibo = recibos.get(0);
            Path path = Paths.get(recibo.getRutaArchivo());
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists()) {
                System.out.println("   ❌ Archivo no existe: " + recibo.getRutaArchivo());
                return ResponseEntity.notFound().build();
            }

            if (!resource.isReadable()) {
                System.out.println("   ❌ Archivo no es legible: " + recibo.getRutaArchivo());
                return ResponseEntity.status(403).build();
            }

            System.out.println("   ✅ Archivo encontrado: " + recibo.getNombreArchivo());
            System.out.println("   📍 Ruta: " + recibo.getRutaArchivo());
            System.out.println("   👤 Descargado por: " + funcionario.getNombre());

            // Registrar descarga
            if (telefono != null) {
                reciboService.registrarDescarga(recibo.getId(), telefono);
            } else if (chatId != null) {
                reciboService.registrarDescarga(recibo.getId(), "telegram:" + chatId);
            }

            // Headers para descarga
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + recibo.getNombreArchivo() + "\"");
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.add(HttpHeaders.PRAGMA, "no-cache");
            headers.add(HttpHeaders.EXPIRES, "0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .contentLength(resource.contentLength())
                    .body(resource);

        } catch (Exception e) {
            System.err.println("❌ ERROR en descarga: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Ver recibo en navegador (compatible con WhatsApp y Telegram)
     */
    @GetMapping("/ver/{ci}/{tipo}/{mesAnio}")
    public ResponseEntity<Resource> verRecibo(
            @PathVariable String ci,
            @PathVariable String tipo,
            @PathVariable String mesAnio,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false) String chatId) {

        System.out.println("\n👁️ SOLICITUD VISUALIZAR:");
        System.out.println("   CI: " + ci);
        System.out.println("   Tipo: " + tipo);
        System.out.println("   Mes/Año: " + mesAnio);
        System.out.println("   Teléfono: " + telefono);
        System.out.println("   Chat ID: " + chatId);

        try {
            // Verificar permisos
            Funcionario funcionario = null;

            if (telefono != null && !telefono.trim().isEmpty()) {
                String telefonoNormalizado = normalizarTelefono(telefono);
                Optional<Funcionario> funcionarioOpt = funcionarioService
                        .buscarPorTelefono(telefonoNormalizado);
                if (funcionarioOpt.isPresent()) {
                    funcionario = funcionarioOpt.get();
                }
            }

            if ((funcionario == null) && chatId != null && !chatId.trim().isEmpty()) {
                Optional<Funcionario> funcionarioOpt = funcionarioService
                        .buscarPorTelegramChatId(chatId);
                if (funcionarioOpt.isPresent()) {
                    funcionario = funcionarioOpt.get();
                }
            }

            if (funcionario == null || !funcionario.getCi().equals(ci)) {
                System.out.println("   ❌ Permiso denegado");
                return ResponseEntity.status(403).build();
            }

            List<ReciboProcesado> recibos = reciboService.buscarPorCiTipoYMes(ci, tipo, mesAnio);
            if (recibos.isEmpty()) {
                System.out.println("   ❌ Recibo no encontrado");
                return ResponseEntity.notFound().build();
            }

            ReciboProcesado recibo = recibos.get(0);
            Path path = Paths.get(recibo.getRutaArchivo());
            Resource resource = new UrlResource(path.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                System.out.println("   ❌ Archivo no accesible");
                return ResponseEntity.notFound().build();
            }

            // Headers para visualización
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + recibo.getNombreArchivo() + "\"");
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");

            System.out.println("   ✅ Visualizando archivo: " + recibo.getNombreArchivo());
            System.out.println("   👤 Usuario: " + funcionario.getNombre());

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);

        } catch (Exception e) {
            System.err.println("❌ ERROR visualizando: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }



    // ==================== MÉTODOS AUXILIARES ====================


    private ResponseEntity<Map<String, Object>> listarTiposRecibo(Funcionario funcionario, String plataforma) {
        Map<String, Object> response = new HashMap<>();
        String ci = funcionario.getCi();

        // Obtener tipos que tiene recibos este funcionario
        List<ReciboProcesado> todosRecibos = reciboService.buscarPorCi(ci);
        Set<String> tiposDisponibles = todosRecibos.stream()
                .map(ReciboProcesado::getTipo)
                .collect(Collectors.toSet());

        // Obtener información de los tipos
        List<Map<String, Object>> tiposConInfo = new ArrayList<>();
        for (String codigoTipo : tiposDisponibles) {
            tipoReciboService.buscarPorCodigo(codigoTipo).ifPresent(tipo -> {
                Map<String, Object> info = new HashMap<>();
                info.put("codigo", tipo.getCodigo());
                info.put("nombre", tipo.getNombre());
                info.put("descripcion", tipo.getDescripcion());
                info.put("cantidad", todosRecibos.stream()
                        .filter(r -> r.getTipo().equals(codigoTipo))
                        .count());
                tiposConInfo.add(info);
            });
        }

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("📋 *TUS TIPOS DE RECIBO*\n\n");

        if (tiposConInfo.isEmpty()) {
            mensaje.append("📭 No tienes recibos disponibles.\n");
        } else {
            for (Map<String, Object> tipo : tiposConInfo) {
                mensaje.append("➡️ *").append(tipo.get("codigo")).append(".* ")
                        .append(tipo.get("nombre")).append("\n");
                if (tipo.get("descripcion") != null && !((String)tipo.get("descripcion")).isEmpty()) {
                    mensaje.append("   ").append(tipo.get("descripcion")).append("\n");
                }
                mensaje.append("   📊 *Recibos:* ").append(tipo.get("cantidad")).append("\n\n");
            }
        }

        mensaje.append("📝 *Para solicitar un recibo:*\n");
        mensaje.append("Escribe *[tipo]-[mes]-[año]*\n");
        mensaje.append("Ejemplo: *1-12-2025*\n\n");
        mensaje.append("🔄 *O escribe:*\n");
        mensaje.append("• *menu* - Volver al menú\n");
        mensaje.append("• *tipos* - Ver todos los tipos\n");
        mensaje.append("• *ayuda* - Instrucciones");

        response.put("success", true);
        response.put("mensaje", mensaje.toString());
        response.put("tipos", tiposConInfo);
        response.put("total_recibos", todosRecibos.size());
        response.put("tipo", "LISTA_TIPOS");
        response.put("funcionario", funcionario.getNombre());

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> procesarSolicitudRecibo(
            Funcionario funcionario, String solicitud, String plataforma) {

        Map<String, Object> response = new HashMap<>();
        String[] partes = solicitud.split("-");
        String tipo = partes[0];
        String mes = partes[1];
        String anio = partes[2];
        String mesAnio = mes + anio;
        String ci = funcionario.getCi();

        System.out.println("   🔍 Buscando recibo: CI=" + ci + ", Tipo=" + tipo + ", Mes/Año=" + mesAnio);

        // Buscar recibo
        List<ReciboProcesado> recibos = reciboService.buscarPorCiTipoYMes(ci, tipo, mesAnio);

        if (recibos.isEmpty()) {
            System.out.println("   ❌ Recibo no encontrado");

            StringBuilder mensaje = new StringBuilder();
            mensaje.append("📭 *Recibo no encontrado*\n\n");
            mensaje.append("No tienes recibo tipo *").append(tipo).append("*\n");
            mensaje.append("para *").append(mes).append("/").append(anio).append("*\n\n");
            mensaje.append("🔍 *Puedes intentar:*\n");
            mensaje.append("1. Verificar el tipo (*tipos*)\n");
            mensaje.append("2. Verificar la fecha\n");
            mensaje.append("3. Contactar a RRHH\n\n");
            mensaje.append("👉 *Escribe *menu* para volver*");

            response.put("success", false);
            response.put("mensaje", mensaje.toString());
            response.put("tipo", "RECIBO_NO_ENCONTRADO");

        } else {
            ReciboProcesado recibo = recibos.get(0);
            String nombreArchivo = recibo.getNombreArchivo();

            System.out.println("   ✅ Recibo encontrado: " + nombreArchivo);

            try {
                // Obtener nombre del tipo
                AtomicReference<String> nombreTipo = new AtomicReference<>(tipo);
                tipoReciboService.buscarPorCodigo(tipo)
                        .ifPresent(tipoInfo -> nombreTipo.set(tipoInfo.getNombre()));

                StringBuilder mensaje = new StringBuilder();

                if ("TELEGRAM".equals(plataforma)) {
                    // PARA TELEGRAM - Enviar URL de descarga
                    // Usar IP local o hostname del servidor Spring Boot
                    String baseUrl = telegramPublicUrl; // O "http://192.168.1.100:8080" según tu red

                    // URL para descargar el archivo (usa el endpoint de descarga existente)
                    String urlDescarga = baseUrl + "/api/bot/descargar/" + ci + "/" + tipo + "/" + mesAnio +
                            "?chatId=" + URLEncoder.encode(String.valueOf(funcionario.getTelegramChatId()), StandardCharsets.UTF_8.name());

                    mensaje.append("✅ *RECIBO ENCONTRADO*\n\n");
                    mensaje.append("👤 *").append(funcionario.getNombre()).append("*\n");
                    mensaje.append("📄 *Archivo:* ").append(nombreArchivo).append("\n");
                    mensaje.append("📋 *Tipo:* ").append(nombreTipo.get()).append(" (").append(tipo).append(")\n");
                    mensaje.append("📅 *Período:* ").append(mes).append("/").append(anio).append("\n");
                    mensaje.append("🆔 *CI:* ").append(ci).append("\n\n");
                    mensaje.append("📤 *Descargando archivo...*\n\n");
                    mensaje.append("⏳ _Por favor espera unos segundos_\n\n");
                    mensaje.append("👉 *El archivo llegará como documento adjunto*");

                    // Enviar URL para que Telegram la descargue
                    response.put("tiene_archivo", true);
                    response.put("archivo_info", Map.of(
                            "url_descarga", urlDescarga,
                            "nombre_archivo", recibo.getNombreArchivo(),
                            "chat_id", funcionario.getTelegramChatId(),
                            "ci", ci,
                            "tipo", tipo,
                            "mes_anio", mesAnio
                    ));

                    System.out.println("   🔗 URL de descarga generada: " + urlDescarga);

                }  else {
                // PARA WHATSAPP - Enviar archivo como documento (igual que Telegram)
                System.out.println("   📤 Preparando envío de archivo para WhatsApp");

                mensaje.append("✅ *RECIBO ENCONTRADO*\n\n");
                mensaje.append("👤 *").append(funcionario.getNombre()).append("*\n");
                mensaje.append("📄 *Archivo:* ").append(nombreArchivo).append("\n");
                mensaje.append("📋 *Tipo:* ").append(nombreTipo.get()).append(" (").append(tipo).append(")\n");
                mensaje.append("📅 *Período:* ").append(mes).append("/").append(anio).append("\n");
                mensaje.append("🆔 *CI:* ").append(ci).append("\n\n");
                mensaje.append("📤 *Enviando archivo...*\n\n");
                mensaje.append("⏳ _Por favor espera unos segundos_\n\n");
                mensaje.append("👉 *El archivo llegará como documento adjunto*");

                // Enviar información del archivo para que el bot lo procese
                response.put("tiene_archivo", true);
                response.put("archivo_info", Map.of(
                        "ci", ci,
                        "tipo", tipo,
                        "mes_anio", mesAnio,
                        "nombre_archivo", recibo.getNombreArchivo()
                ));
            }

                response.put("success", true);
                response.put("mensaje", mensaje.toString());
                response.put("recibo", Map.of(
                        "id", recibo.getId(),
                        "nombre_archivo", nombreArchivo,
                        "tipo", tipo,
                        "mes_anio", mesAnio,
                        "mes_formateado", formatearMesAnio(mesAnio)
                ));
                response.put("tipo", "RECIBO_ENCONTRADO");
                response.put("plataforma", plataforma);

            } catch (Exception e) {
                System.err.println("❌ Error generando respuesta: " + e.getMessage());
                return crearRespuestaError(
                        "⚠️ Error procesando tu solicitud.",
                        "ERROR_PROCESAMIENTO", e);
            }
        }

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> procesarSeleccionNumerica(
            Funcionario funcionario, String numero, String plataforma) {

        // Si es un solo dígito, asumimos que es selección de tipo
        if (numero.length() == 1) {
            return listarRecibosPorTipo(funcionario, numero);
        }

        // Si es "4", mostrar información del funcionario
        if (numero.equals("4")) {
            return mostrarInfoFuncionario(funcionario, plataforma);
        }

        // Si es número más largo, podría ser CI o algo else
        return crearRespuestaDefault(funcionario, plataforma);
    }

    private ResponseEntity<Map<String, Object>> mostrarInfoFuncionario(Funcionario funcionario, String plataforma) {
        Map<String, Object> response = new HashMap<>();

        // Obtener estadísticas
        List<ReciboProcesado> todosRecibos = reciboService.buscarPorCi(funcionario.getCi());
        long totalRecibos = todosRecibos.size();

        Set<String> tipos = todosRecibos.stream()
                .map(ReciboProcesado::getTipo)
                .collect(Collectors.toSet());

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("👤 *TU INFORMACIÓN*\n\n");
        mensaje.append("📋 *Datos personales:*\n");
        mensaje.append("• *Nombre:* ").append(funcionario.getNombre()).append("\n");
        mensaje.append("• *CI:* ").append(funcionario.getCi()).append("\n");
        mensaje.append("• *Teléfono:* ").append(formatearTelefono(funcionario.getTelefono())).append("\n\n");

        mensaje.append("📊 *Estadísticas de recibos:*\n");
        mensaje.append("• *Total recibos:* ").append(totalRecibos).append("\n");
        mensaje.append("• *Tipos disponibles:* ").append(tipos.size()).append("\n");

        if (!tipos.isEmpty()) {
            mensaje.append("• *Tipos:* ");
            mensaje.append(String.join(", ", tipos));
            mensaje.append("\n");
        }

        mensaje.append("\n📝 *Comandos disponibles:*\n");
        mensaje.append("• *menu* - Menú principal\n");
        mensaje.append("• *recibos* - Ver tus recibos\n");
        mensaje.append("• *tipos* - Tipos disponibles\n");
        mensaje.append("• *ayuda* - Instrucciones\n\n");

        mensaje.append("👉 *¿Necesitas ayuda? Contacta a RRHH*");

        response.put("success", true);
        response.put("mensaje", mensaje.toString());
        response.put("tipo", "INFO_FUNCIONARIO");
        response.put("funcionario", Map.of(
                "nombre", funcionario.getNombre(),
                "ci", funcionario.getCi(),
                "telefono", funcionario.getTelefono(),
                "total_recibos", totalRecibos,
                "tipos_disponibles", tipos
        ));

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> listarRecibosPorTipo(
            Funcionario funcionario, String tipo) {

        Map<String, Object> response = new HashMap<>();
        String ci = funcionario.getCi();

        List<ReciboProcesado> recibos = reciboService.buscarPorCiYTipo(ci, tipo);

        StringBuilder mensaje = new StringBuilder();

        // Lista estructurada para que el bot pueda mapear opciones numéricas
        List<Map<String, Object>> listaRecibos = new ArrayList<>();

        if (recibos.isEmpty()) {
            mensaje.append("📭 No tienes recibos tipo *").append(tipo).append("*\n\n");
        } else {
            // Agrupar por mes/año para evitar duplicados (aunque normalmente no los hay)
            Map<String, List<ReciboProcesado>> porMes = recibos.stream()
                    .collect(Collectors.groupingBy(ReciboProcesado::getMesAnio));

            mensaje.append("📋 *TUS RECIBOS - TIPO ").append(tipo).append("*\n");
            mensaje.append("Total: ").append(recibos.size()).append(" recibos\n\n");

            int contador = 1;
            for (Map.Entry<String, List<ReciboProcesado>> entry : porMes.entrySet()) {
                String mesAnio = entry.getKey();
                String mesFormateado = formatearMesAnio(mesAnio); // Ej: "Febrero 2026"

                mensaje.append(contador).append(". *").append(mesFormateado).append("*\n");

                // Extraer número de mes y año para el bot
                int mesNumero = Integer.parseInt(mesAnio.substring(0, 2));
                int anio = Integer.parseInt(mesAnio.substring(2));

                Map<String, Object> item = new HashMap<>();
                item.put("mes", mesFormateado.split(" ")[0]); // Solo el nombre del mes
                item.put("mesNumero", mesNumero);
                item.put("anio", anio);
                listaRecibos.add(item);

                contador++;
            }

            mensaje.append("\n📝 *Para descargar:*\n");
            mensaje.append("Escribe el *número de la opción* (1, 2, etc.)\n");
        }

        mensaje.append("\n🔄 *O escribe:*\n");
        mensaje.append("• *menu* - Volver al menú\n");
        mensaje.append("• *tipos* - Ver todos los tipos\n");
        mensaje.append("• *ayuda* - Instrucciones");

        response.put("success", true);
        response.put("mensaje", mensaje.toString());
        response.put("recibos", listaRecibos);      // Lista estructurada para el bot
        response.put("tipo_recibo", tipo);          // Tipo seleccionado
        response.put("tipo", "LISTA_RECIBOS_TIPO");
        response.put("cantidad", recibos.size());

        return ResponseEntity.ok(response);
    }


    private ResponseEntity<Map<String, Object>> listarRecibosPorMes(
            Funcionario funcionario, String mesAnioStr, String plataforma) {

        Map<String, Object> response = new HashMap<>();
        String ci = funcionario.getCi();

        // Convertir 12-2025 a 122025
        String[] partes = mesAnioStr.split("-");
        String mesAnio = partes[0] + partes[1];

        List<ReciboProcesado> recibos = reciboService.buscarPorCiYMes(ci, mesAnio);

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("📋 *TUS RECIBOS - ").append(formatearMesAnio(mesAnio)).append("*\n\n");

        if (recibos.isEmpty()) {
            mensaje.append("📭 No tienes recibos para este período.\n");
        } else {
            // Agrupar por tipo
            Map<String, List<ReciboProcesado>> porTipo = recibos.stream()
                    .collect(Collectors.groupingBy(ReciboProcesado::getTipo));

            int contador = 1;
            for (Map.Entry<String, List<ReciboProcesado>> entry : porTipo.entrySet()) {
                String tipoCodigo = entry.getKey();
                AtomicReference<String> nombreTipo = new AtomicReference<>(tipoCodigo);

                tipoReciboService.buscarPorCodigo(tipoCodigo)
                        .ifPresent(tipo -> nombreTipo.set(tipo.getNombre()));

                mensaje.append(contador).append(". *").append(nombreTipo.get())
                        .append(" (").append(tipoCodigo).append(")* - ")
                        .append(entry.getValue().size()).append(" recibos\n");
                contador++;
            }

            mensaje.append("\n📝 *Para descargar:*\n");
            mensaje.append("Escribe *[tipo]-").append(partes[0]).append("-").append(partes[1]).append("*\n");
            mensaje.append("Ejemplo: *1-").append(partes[0]).append("-").append(partes[1]).append("*\n");
        }

        mensaje.append("\n🔄 *O escribe:*\n");
        mensaje.append("• *menu* - Volver al menú\n");
        mensaje.append("• *ayuda* - Instrucciones");

        response.put("success", true);
        response.put("mensaje", mensaje.toString());
        response.put("recibos", recibos);
        response.put("tipo", "LISTA_RECIBOS_MES");
        response.put("cantidad", recibos.size());

        return ResponseEntity.ok(response);
    }


    private ResponseEntity<Map<String, Object>> listarTodosTipos(Funcionario funcionario, String plataforma) {
        Map<String, Object> response = new HashMap<>();

        List<TipoRecibo> todosTipos = tipoReciboService.listarTodos();

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("📋 *TIPOS DE RECIBO DISPONIBLES*\n\n");

        for (TipoRecibo tipo : todosTipos) {
            mensaje.append("➡️ *").append(tipo.getCodigo()).append(".* ")
                    .append(tipo.getNombre()).append("\n");
            if (tipo.getDescripcion() != null && !tipo.getDescripcion().isEmpty()) {
                mensaje.append("   ").append(tipo.getDescripcion()).append("\n");
            }
            mensaje.append("\n");
        }

        mensaje.append("📝 *Para solicitar:*\n");
        mensaje.append("Escribe *[código]-[mes]-[año]*\n");
        mensaje.append("Ejemplo: *1-12-2025*\n\n");
        mensaje.append("👉 *Escribe *menu* para volver*");

        response.put("success", true);
        response.put("mensaje", mensaje.toString());
        response.put("tipos", todosTipos.stream()
                .map(t -> Map.of(
                        "codigo", t.getCodigo(),
                        "nombre", t.getNombre(),
                        "descripcion", t.getDescripcion()
                ))
                .collect(Collectors.toList()));
        response.put("tipo", "LISTA_TODOS_TIPOS");

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> crearRespuestaAyuda(Funcionario funcionario, String plataforma) {
        Map<String, Object> response = new HashMap<>();

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("❓ *AYUDA - SISTEMA DE RECIBOS*\n\n");
        mensaje.append("📱 *COMANDOS DISPONIBLES:*\n\n");
        mensaje.append("• *menu* - Menú principal\n");
        mensaje.append("• *recibos* o *1* - Ver mis recibos\n");
        mensaje.append("• *tipos* o *2* - Tipos disponibles\n");
        mensaje.append("• *ayuda* - Esta ayuda\n");
        mensaje.append("• *estado* o *4* - Mi información\n\n");
        mensaje.append("📝 *FORMATOS DE SOLICITUD:*\n\n");
        mensaje.append("1. *[tipo]-[mes]-[año]*\n");
        mensaje.append("   Ej: *1-12-2025*\n");
        mensaje.append("   → Te envía ese recibo específico\n\n");
        mensaje.append("2. *[mes]-[año]*\n");
        mensaje.append("   Ej: *12-2025*\n");
        mensaje.append("   → Lista recibos de ese mes\n\n");
        mensaje.append("3. *[tipo]*\n");
        mensaje.append("   Ej: *1*\n");
        mensaje.append("   → Lista recibos de ese tipo\n\n");
        mensaje.append("👉 *¿Necesitas más ayuda?*\n");
        mensaje.append("Contacta a Recursos Humanos");

        response.put("success", true);
        response.put("mensaje", mensaje.toString());
        response.put("tipo", "AYUDA");

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> crearRespuestaDefault(Funcionario funcionario, String plataforma) {
        Map<String, Object> response = new HashMap<>();

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("🤔 *No entendí tu mensaje*\n\n");
        mensaje.append("Puedo ayudarte con:\n\n");
        mensaje.append("1. Descargar recibos\n");
        mensaje.append("2. Ver tipos disponibles\n");
        mensaje.append("3. Mostrar ayuda\n");
        mensaje.append("4. Ver mi información\n\n");
        mensaje.append("📝 *Ejemplos:*\n");
        mensaje.append("• *menu* - Menú principal\n");
        mensaje.append("• *1-12-2025* - Solicitar recibo\n");
        mensaje.append("• *ayuda* - Instrucciones\n\n");
        mensaje.append("👉 *Escribe *menu* para comenzar*");

        response.put("success", false);
        response.put("mensaje", mensaje.toString());
        response.put("tipo", "MENSAJE_NO_ENTENDIDO");

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> crearRespuestaError(
            String mensajeError, String codigo, Exception e) {

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", true);
        response.put("mensaje", mensajeError);
        response.put("codigo", codigo);
        response.put("timestamp", LocalDateTime.now());
        response.put("application", appName);

        // Log para debugging
        if (e != null) {
            System.err.println("Error procesando mensaje WhatsApp: " + e.getMessage());
            e.printStackTrace();
            response.put("debug_message", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    private String normalizarTelefono(String telefono) {
        if (telefono == null) return "";

        System.out.println("   🔧 Teléfono original: " + telefono);

        // Remover espacios, guiones, paréntesis y el sufijo @c.us
        String limpio = telefono.replaceAll("[\\s\\-\\(\\)]", "");
        if (limpio.endsWith("@c.us")) {
            limpio = limpio.substring(0, limpio.length() - 5);
        }

        // Guardamos una versión solo dígitos para comparaciones flexibles
        String soloDigitos = limpio.replaceAll("[^0-9]", "");

        System.out.println("   🔧 Solo dígitos: " + soloDigitos);
        System.out.println("   🔧 Limpio: " + limpio);

        // Devolvemos el número limpio (puede tener o no +)
        // La búsqueda después probará ambas variantes
        return limpio;
    }

    private String formatearTelefono(String telefono) {
        if (telefono == null || telefono.length() != 11) return telefono;
        // Formato: +598 XX XXX XXX
        return "+" + telefono.substring(0, 3) + " " +
                telefono.substring(3, 5) + " " +
                telefono.substring(5, 8) + " " +
                telefono.substring(8);
    }

    private String formatearMesAnio(String mesAnio) {
        if (mesAnio == null || mesAnio.length() != 6) return mesAnio;
        String mes = mesAnio.substring(0, 2);
        String anio = mesAnio.substring(2);
        Map<String, String> meses = Map.ofEntries(
                Map.entry("01", "Enero"), Map.entry("02", "Febrero"),
                Map.entry("03", "Marzo"), Map.entry("04", "Abril"),
                Map.entry("05", "Mayo"), Map.entry("06", "Junio"),
                Map.entry("07", "Julio"), Map.entry("08", "Agosto"),
                Map.entry("09", "Septiembre"), Map.entry("10", "Octubre"),
                Map.entry("11", "Noviembre"), Map.entry("12", "Diciembre")
        );
        return meses.getOrDefault(mes, mes) + " " + anio;
    }

    private Map<String, Object> convertirReciboAMap(ReciboProcesado recibo) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put("id", recibo.getId());
        mapa.put("ci", recibo.getCi());
        mapa.put("tipo", recibo.getTipo());
        mapa.put("mes_anio", recibo.getMesAnio());
        mapa.put("mes_anio_formateado", formatearMesAnio(recibo.getMesAnio()));
        mapa.put("nombre_archivo", recibo.getNombreArchivo());
        mapa.put("procesado_en", recibo.getProcesadoEn());
        mapa.put("ruta_archivo", recibo.getRutaArchivo());
        return mapa;
    }

    @GetMapping("/debug/funcionarios")
    public ResponseEntity<List<Map<String, String>>> debugFuncionarios() {
        List<Funcionario> todos = funcionarioService.listarTodos();
        List<Map<String, String>> resultado = new ArrayList<>();

        for (Funcionario f : todos) {
            Map<String, String> map = new HashMap<>();
            map.put("nombre", f.getNombre());
            map.put("ci", f.getCi());
            map.put("telefono", f.getTelefono());
            map.put("telefono_length", String.valueOf(f.getTelefono().length()));
            resultado.add(map);
        }

        return ResponseEntity.ok(resultado);
    }

    @PostMapping("/debug/buscar")
    public ResponseEntity<Map<String, Object>> debugBuscarTelefono(
            @RequestBody Map<String, String> request) {

        String telefono = request.get("telefono");
        Map<String, Object> response = new HashMap<>();

        System.out.println("🔍 DEBUG: Buscando teléfono: " + telefono);

        // Probar el servicio
        Optional<Funcionario> funcionarioOpt = funcionarioService.buscarPorTelefono(telefono);

        if (funcionarioOpt.isPresent()) {
            Funcionario f = funcionarioOpt.get();
            response.put("encontrado", true);
            response.put("nombre", f.getNombre());
            response.put("ci", f.getCi());
            response.put("telefono_en_bd", f.getTelefono());
        } else {
            response.put("encontrado", false);
            response.put("mensaje", "No encontrado");

            // Listar todos los teléfonos en la BD para comparar
            List<String> telefonosEnBD = funcionarioService.listarTodosTelefonos();
            response.put("telefonos_en_bd", telefonosEnBD);
        }

        return ResponseEntity.ok(response);
    }

    // ==================== ACTIVACIÓN WHATSAPP ====================

    /**
     * Respuesta para LID no registrado - Solicitar correo
     */
    private ResponseEntity<Map<String, Object>> crearRespuestaActivacionWhatsapp(String lid) {
        Map<String, Object> response = new HashMap<>();

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("🔐 *Activación requerida*\n\n");
        mensaje.append("Para usar el bot de WhatsApp, primero debes activar tu cuenta.\n\n");
        mensaje.append("📝 *Pasos a seguir:*\n");
        mensaje.append("1️⃣ Envía tu *correo corporativo*\n");
        mensaje.append("2️⃣ Recibirás un código de 6 dígitos por email\n");
        mensaje.append("3️⃣ Envía el código para activar tu cuenta\n\n");
        mensaje.append("📧 *Ejemplo:* `nombre@empresa.com`\n\n");
        mensaje.append("👉 *Envía tu correo ahora*");

        response.put("success", false);
        response.put("autenticacion_requerida", true);
        response.put("tipo", "ACTIVACION_WHATSAPP_REQUERIDA");
        response.put("mensaje", mensaje.toString());
        response.put("paso", 1); // Esperando correo

        return ResponseEntity.ok(response);
    }

    /**
     * Respuesta para LID registrado pero no activado - Solicitar código
     */
    private ResponseEntity<Map<String, Object>> crearRespuestaActivacionPendienteWhatsapp(Funcionario funcionario) {
        Map<String, Object> response = new HashMap<>();

        // Si no tiene código pendiente, generar uno nuevo
        if (funcionario.getWhatsappCodigoActivacion() == null) {
            String nuevoCodigo = generarCodigo6Digitos();
            funcionario.setWhatsappCodigoActivacion(nuevoCodigo);
            funcionario.setWhatsappCodigoExpiracion(LocalDateTime.now().plusMinutes(15));
            funcionarioService.guardar(funcionario);

            // Enviar código por email
            String asunto = "Código de activación para WhatsApp";
            String cuerpo = "Hola " + funcionario.getNombre() + ",\n\n" +
                    "Has solicitado activar tu cuenta de WhatsApp.\n\n" +
                    "Tu código de activación es: *" + nuevoCodigo + "*\n\n" +
                    "Este código expirará en 15 minutos.\n\n" +
                    "Saludos cordiales,\n" +
                    "Sistema de Recibos";

            emailService.sendEmail(funcionario.getCorreo(), asunto, cuerpo);

            System.out.println("   📧 Nuevo código enviado a: " + funcionario.getCorreo());
        }

        StringBuilder mensaje = new StringBuilder();
        mensaje.append("🔐 *Activación pendiente*\n\n");
        mensaje.append("Hola ").append(funcionario.getNombre()).append("!\n\n");
        mensaje.append("📧 *Se envió un código a:*\n");
        mensaje.append(ocultarCorreo(funcionario.getCorreo())).append("\n\n");
        mensaje.append("🔢 *Ingresa el código de 6 dígitos* para activar tu WhatsApp.\n\n");
        mensaje.append("⏳ El código expira en 15 minutos");

        response.put("success", false);
        response.put("activacion_pendiente", true);
        response.put("tipo", "ACTIVACION_WHATSAPP_PENDIENTE");
        response.put("mensaje", mensaje.toString());
        response.put("paso", 2); // Esperando código
        response.put("funcionario", Map.of(
                "nombre", funcionario.getNombre(),
                "correo_oculto", ocultarCorreo(funcionario.getCorreo())
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Completar activación de WhatsApp con código
     */
    @PostMapping("/whatsapp/completar-activacion")
    public ResponseEntity<Map<String, Object>> completarActivacionWhatsapp(
            @RequestBody Map<String, String> request) {

        String lid = request.get("lid");
        String codigo = request.get("codigo");

        System.out.println("\n🔐 ENDPOINT COMPLETAR ACTIVACIÓN WHATSAPP:");
        System.out.println("   LID: " + lid);
        System.out.println("   Código: " + codigo);

        try {
            // Activar cuenta usando el service
            boolean activado = funcionarioService.activarWhatsappConCodigo(lid, codigo);

            if (!activado) {
                // Control de intentos fallidos
                int intentos = intentosWhatsapp.getOrDefault(lid, 0) + 1;
                intentosWhatsapp.put(lid, intentos);

                if (intentos >= 3) {
                    funcionarioService.limpiarCodigoActivacionWhatsapp(lid);
                    intentosWhatsapp.remove(lid);

                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "tipo", "DEMASIADOS_INTENTOS",
                            "mensaje", "❌ Demasiados intentos fallidos.\n\n" +
                                    "Envía tu correo nuevamente para obtener un nuevo código."
                    ));
                }

                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "tipo", "CODIGO_INCORRECTO",
                        "mensaje", "❌ Código incorrecto. (Intento " + intentos + "/3)"
                ));
            }

            // Activación exitosa
            intentosWhatsapp.remove(lid);
            Funcionario f = funcionarioService.buscarPorWhatsappLid(lid).get();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "tipo", "ACTIVACION_EXITOSA",
                    "mensaje", "🎉 *¡Activación exitosa!*\n\n" +
                            "Hola " + f.getNombre() + ", tu cuenta de WhatsApp está activada.\n\n" +
                            "👉 Escribe *menu* para comenzar"
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return crearRespuestaError("Error completando activación", "ERROR_COMPLETAR_ACTIVACION", e);
        }
    }

    /**
     * Endpoint para iniciar activación WhatsApp (solicitar correo)
     */
    @PostMapping("/whatsapp/iniciar-activacion")
    public ResponseEntity<Map<String, Object>> iniciarActivacionWhatsapp(
            @RequestBody Map<String, String> request) {

        String lid = request.get("lid");
        String correo = request.get("correo");

        System.out.println("\n🔐 INICIANDO ACTIVACIÓN WHATSAPP:");
        System.out.println("   LID: " + lid);
        System.out.println("   Correo: " + correo);

        try {
            Optional<Funcionario> funcionarioOpt = funcionarioService.buscarPorCorreo(correo);

            if (funcionarioOpt.isEmpty()) {
                System.out.println("   ❌ Correo no registrado");
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "tipo", "CORREO_NO_REGISTRADO",
                        "mensaje", "❌ El correo *" + correo + "* no está registrado.\n\n" +
                                "Verifica que sea tu correo corporativo o contacta a RRHH."
                ));
            }

            Funcionario funcionario = funcionarioOpt.get();

            if (funcionario.getWhatsappActivado()) {
                System.out.println("   ⚠️ WhatsApp ya activado");
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "tipo", "YA_ACTIVADO",
                        "mensaje", "✅ Tu cuenta ya está activada. ¡Bienvenido " + funcionario.getNombre() + "!"
                ));
            }

            // Generar y guardar código (USANDO EL SERVICE)
            String codigo = generarCodigo6Digitos();
            funcionarioService.guardarCodigoActivacionWhatsapp(lid, correo, codigo);

            // Enviar email
            String asunto = "Código de activación para WhatsApp";
            String cuerpo = "Hola " + funcionario.getNombre() + ",\n\n" +
                    "Tu código de activación es: *" + codigo + "*\n" +
                    "Válido por 15 minutos.";

            emailService.sendEmail(funcionario.getCorreo(), asunto, cuerpo);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "tipo", "CODIGO_ENVIADO",
                    "mensaje", "📧 *Código enviado* a " + ocultarCorreo(funcionario.getCorreo()),
                    "expiracion_minutos", 15
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return crearRespuestaError("Error iniciando activación", "ERROR_ACTIVACION", e);
        }
    }

    /**
     * Verificar estado de activación WhatsApp
     */
    @GetMapping("/whatsapp/estado/{lid}")
    public ResponseEntity<Map<String, Object>> verificarEstadoWhatsapp(
            @PathVariable String lid) {

        System.out.println("\n🔍 VERIFICANDO ESTADO WHATSAPP:");
        System.out.println("   LID: " + lid);

        Optional<Funcionario> funcionarioOpt = funcionarioService.buscarPorWhatsappLid(lid);

        Map<String, Object> response = new HashMap<>();
        response.put("lid", lid);
        response.put("timestamp", LocalDateTime.now());

        if (funcionarioOpt.isEmpty()) {
            response.put("registrado", false);
            response.put("mensaje", "LID no registrado");
            return ResponseEntity.ok(response);
        }

        Funcionario funcionario = funcionarioOpt.get();
        response.put("registrado", true);
        response.put("funcionario", funcionario.getNombre());
        response.put("ci", funcionario.getCi());
        response.put("whatsapp_activado", funcionario.getWhatsappActivado());
        response.put("whatsapp_registrado_en", funcionario.getWhatsappRegistradoEn());

        if (funcionario.getWhatsappActivado()) {
            response.put("estado", "ACTIVADO");
        } else if (funcionario.getWhatsappCodigoActivacion() != null) {
            response.put("estado", "PENDIENTE_ACTIVACION");
            response.put("codigo_expiracion", funcionario.getWhatsappCodigoExpiracion());
        } else {
            response.put("estado", "REGISTRADO_NO_ACTIVADO");
        }

        return ResponseEntity.ok(response);
    }
}