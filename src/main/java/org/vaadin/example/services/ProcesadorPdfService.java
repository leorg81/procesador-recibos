package org.vaadin.example.services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.vaadin.example.data.ReciboProcesado;
import org.vaadin.example.data.ReciboProcesadoRepository;
import org.vaadin.example.data.TipoRecibo;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProcesadorPdfService {

    private final ReciboProcesadoRepository repository;

    // Patrón mejorado para detectar CI (con o sin guión)
    private static final Pattern PATRON_CI = Pattern.compile(
            "C\\.I\\.:\\s*(\\d{7,8}-?\\d?)|\\b\\d{7,8}-?\\d?\\b",
            Pattern.CASE_INSENSITIVE
    );

    public ProcesadorPdfService(ReciboProcesadoRepository repository) {
        this.repository = repository;
    }

    /**
     * Procesa un PDF dividiendo por cada CI encontrado
     */
    public void procesarRecibos(
            InputStream inputStream,
            String localidadCodigo,
            TipoRecibo tipoRecibo,
            String mes,
            String anio,
            String outputDirPath
    ) {
        try (PDDocument documentoCompleto = PDDocument.load(inputStream)) {
            int totalPaginas = documentoCompleto.getNumberOfPages();

            // Crear directorio de salida si no existe
            File directorio = new File(outputDirPath);
            if (!directorio.exists()) {
                directorio.mkdirs();
            }

            String mesAnio = mes + anio;

            // Procesar cada página
            for (int i = 0; i < totalPaginas; i++) {
                // Extraer texto de la página
                String textoPagina = extraerTextoPagina(documentoCompleto, i);

                // Buscar CI en la página
                String ci = extraerCI(textoPagina);

                // Crear nombre de archivo
                String nombreArchivo = String.format("%s-%s-%s.pdf",
                        ci, tipoRecibo.getCodigo().toLowerCase(), mesAnio);

                // Ruta completa del archivo
                String rutaArchivo = outputDirPath + File.separator + nombreArchivo;

                // Guardar página individual como PDF
                guardarPaginaIndividual(documentoCompleto, i, rutaArchivo);

                // Guardar en base de datos
                guardarReciboEnBD(ci, tipoRecibo, mesAnio, nombreArchivo,
                        rutaArchivo, localidadCodigo, textoPagina);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error procesando PDF: " + e.getMessage(), e);
        }
    }

    private String extraerTextoPagina(PDDocument documento, int numeroPagina) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(numeroPagina + 1);
        stripper.setEndPage(numeroPagina + 1);
        return stripper.getText(documento);
    }

    private String extraerCI(String texto) {
        Matcher matcher = PATRON_CI.matcher(texto);
        if (matcher.find()) {
            String ciEncontrado = matcher.group();
            // Limpiar el CI (quitar "C.I.:" y espacios)
            return ciEncontrado.replaceAll("C\\.I\\.:", "")
                    .replaceAll("[^\\d-]", "")
                    .trim();
        }
        return "desconocido";
    }



    /**
     * Devuelve la primera palabra de una cadena (separador: espacio en blanco).
     * Si la cadena es nula o vacía, retorna una cadena vacía.
     */
    private String obtenerPrimerToken(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return "";
        }
        String[] partes = texto.trim().split("\\s+");
        return partes[0];
    }


    private void guardarPaginaIndividual(PDDocument documento, int numeroPagina, String rutaArchivo) throws Exception {
        try (PDDocument paginaIndividual = new PDDocument()) {
            paginaIndividual.addPage(documento.getPage(numeroPagina));
            paginaIndividual.save(rutaArchivo);
        }
    }

    private void guardarReciboEnBD(
            String ci,
            TipoRecibo tipoRecibo,
            String mesAnio,
            String nombreArchivo,
            String rutaArchivo,
            String localidadCodigo,
            String textoPagina
    ) {
        ReciboProcesado recibo = new ReciboProcesado();
        recibo.setCi(ci);
        recibo.setTipoRecibo(tipoRecibo);
        recibo.setTipo(tipoRecibo.getCodigo());
        recibo.setMesAnio(mesAnio);
        recibo.setNombreArchivo(nombreArchivo);
        recibo.setRutaArchivo(rutaArchivo);
        recibo.setProcesadoEn(LocalDateTime.now());
        recibo.setLocalidadCodigo(localidadCodigo);

        // Extraer nombre del funcionario si está en el texto
        extraerNombreFuncionario(textoPagina, recibo);

        // ¡CALCULAR CONFIANZA! - Agrega esta línea:
        calcularYAsignarConfianza(recibo, textoPagina);

        repository.save(recibo);
    }

    private void calcularYAsignarConfianza(ReciboProcesado recibo, String textoPagina) {
        double confianza = 0.0; // Base 0%

        // 1. Puntos por CI válido (30 puntos máximo)
        if (recibo.getCi() != null && !recibo.getCi().equals("desconocido")) {
            if (recibo.getCi().matches("\\d{7,8}-?\\d?")) {
                confianza += 0.3; // 30%
            }
        }

        // 2. Puntos por nombres extraídos (30 puntos máximo)
        if (recibo.getPrimerNombre() != null && !recibo.getPrimerNombre().isEmpty() &&
                recibo.getPrimerApellido() != null && !recibo.getPrimerApellido().isEmpty()) {
            confianza += 0.3; // 30%
        }

        // 3. Puntos por contenido válido en el texto (40 puntos máximo)
        if (textoPagina != null && textoPagina.length() > 100) {
            String textoLower = textoPagina.toLowerCase();

            // Verificar palabras clave comunes en recibos
            String[] keywords = {"recibo", "pago", "sueldo", "liquidación",
                    "mes", "año", "total", "monto", "cédula"};
            int keywordsEncontradas = 0;

            for (String keyword : keywords) {
                if (textoLower.contains(keyword)) {
                    keywordsEncontradas++;
                }
            }

            // Si encuentra al menos 3 keywords, sumar puntos
            if (keywordsEncontradas >= 3) {
                confianza += 0.4; // 40%
            } else if (keywordsEncontradas > 0) {
                confianza += (keywordsEncontradas * 0.1); // 10% por cada keyword
            }
        }

        // Asegurar que esté entre 0 y 1
        recibo.setConfianza(Math.max(0.0, Math.min(1.0, confianza)));

        // Si después de todo sigue en 0, asignar un mínimo
        if (recibo.getConfianza() == 0.0 &&
                (recibo.getCi() != null || recibo.getPrimerNombre() != null)) {
            recibo.setConfianza(0.1); // 10% mínimo si tiene algún dato
        }
    }

    private void extraerNombreFuncionario(String texto, ReciboProcesado recibo) {
        Pattern patronNombre = Pattern.compile(
                "Nombres:\\s*(.+?)\\s+Apellidos:\\s*(.+)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = patronNombre.matcher(texto);
        if (matcher.find()) {
            String nombresCompleto = matcher.group(1).trim();
            String apellidosCompleto = matcher.group(2).trim();

            // Guardar los valores completos
            recibo.setNombres(nombresCompleto);
            recibo.setApellidos(apellidosCompleto);

            // Opcional: si aún quieres mantener los campos de primer nombre/apellido
            // los puedes calcular también
            recibo.setPrimerNombre(obtenerPrimerToken(nombresCompleto));
            recibo.setPrimerApellido(obtenerPrimerToken(apellidosCompleto));
        }
    }
}