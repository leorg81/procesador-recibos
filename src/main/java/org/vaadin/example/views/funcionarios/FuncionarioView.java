package org.vaadin.example.views.funcionarios;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.RolesAllowed;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.vaadin.example.data.Funcionario;
import org.vaadin.example.services.FuncionarioService;
import org.vaadin.example.views.MainLayout;
import org.vaadin.lineawesome.LineAwesomeIconUrl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@PageTitle("Funcionarios")
@Route(value = "", layout = MainLayout.class)
@Menu(order = 1, icon = LineAwesomeIconUrl.USERS_COG_SOLID)
@RolesAllowed({"ADMIN", "LIQUIDACIONES","PERSONAL"})
public class FuncionarioView extends VerticalLayout {

    private final FuncionarioService service;
    private final Grid<Funcionario> grid = new Grid<>(Funcionario.class, false);
    private final Pattern telefonoPattern = Pattern.compile("^\\+598\\d{8}$");
    // NUEVO: Patrón para cédula con formato 7 dígitos + guión + 1 dígito
    private final Pattern ciPattern = Pattern.compile("^\\d{7}-\\d$");

    public FuncionarioView(FuncionarioService service) {
        this.service = service;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Gestión de Funcionarios");
        add(titulo);

        // Configuración de la grid
        configurarGrid();

        // Botones de acción principales
        HorizontalLayout botonesLayout = new HorizontalLayout();
        botonesLayout.setSpacing(true);

        Button nuevoBtn = new Button("Nuevo Funcionario",
                new Icon(VaadinIcon.PLUS_CIRCLE_O));
        nuevoBtn.addClickListener(e -> abrirFormulario(null));

        Button eliminarSeleccionadoBtn = new Button("Eliminar Seleccionado",
                new Icon(VaadinIcon.TRASH));
        eliminarSeleccionadoBtn.addClickListener(e -> eliminarSeleccionado());
        eliminarSeleccionadoBtn.getElement().getThemeList().add("error");

        botonesLayout.add(nuevoBtn, eliminarSeleccionadoBtn);
        add(botonesLayout);

        // Componentes de importación/exportación CSV
        HorizontalLayout csvLayout = new HorizontalLayout();
        csvLayout.setSpacing(true);

        csvLayout.add(crearComponenteCargaCSV());
        csvLayout.add(crearBotonDescargaCSV());

        add(csvLayout);
        add(grid);
    }

    private void configurarGrid() {
        grid.addColumn(Funcionario::getCi).setHeader("Cédula").setAutoWidth(true);
        grid.addColumn(Funcionario::getNombre).setHeader("Nombre").setAutoWidth(true);
        grid.addColumn(Funcionario::getCorreo).setHeader("Correo").setAutoWidth(true);
        grid.addColumn(Funcionario::getTelefono).setHeader("Teléfono").setAutoWidth(true);

        grid.addComponentColumn(funcionario -> {
            Button editarBtn = new Button("Editar", new Icon(VaadinIcon.EDIT));
            editarBtn.addClickListener(e -> abrirFormulario(funcionario));
            editarBtn.getElement().getThemeList().add("primary");
            editarBtn.setWidthFull();
            return editarBtn;
        }).setHeader("Acciones").setAutoWidth(true);

        grid.setItems(service.listarTodos());
        grid.setSizeFull();
    }

    private void actualizarGrid() {
        grid.setItems(service.listarTodos());
    }

    private void eliminarSeleccionado() {
        Funcionario seleccionado = grid.asSingleSelect().getValue();
        if (seleccionado != null) {
            service.eliminar(seleccionado.getCi());
            actualizarGrid();
            Notification.show("Funcionario eliminado correctamente", 3000, Notification.Position.MIDDLE);
        } else {
            Notification.show("Seleccione un funcionario para eliminar", 3000, Notification.Position.MIDDLE);
        }
    }

    private Upload crearComponenteCargaCSV() {
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".csv", "text/csv");
        upload.setMaxFileSize(5 * 1024 * 1024); // 5MB máximo
        upload.setDropLabel(new com.vaadin.flow.component.html.Span("Arrastre archivo CSV o haga click"));

        upload.addSucceededListener(event -> {
            try (var reader = new InputStreamReader(buffer.getInputStream(), StandardCharsets.UTF_8)) {
                CSVParser parser = CSVFormat.DEFAULT
                        .withHeader("ci", "nombre", "correo", "telefono")
                        .withSkipHeaderRecord()
                        .withTrim()
                        .parse(reader);

                List<Funcionario> funcionarios = new ArrayList<>();
                List<String> errores = new ArrayList<>();
                int lineNumber = 1;

                for (CSVRecord record : parser) {
                    lineNumber++;
                    try {
                        Funcionario f = new Funcionario();
                        String ci = record.get("ci").trim();
                        String nombre = record.get("nombre").trim();
                        String correo = record.get("correo").trim();
                        String telefono = record.get("telefono").trim();

                        // Validaciones
                        if (ci.isEmpty()) {
                            throw new IllegalArgumentException("CI no puede estar vacío");
                        }
                        if (!ciPattern.matcher(ci).matches()) {
                            throw new IllegalArgumentException("CI debe tener formato: 1234567-0 (7 dígitos, guión, 1 dígito)");
                        }
                        if (nombre.isEmpty()) {
                            throw new IllegalArgumentException("Nombre no puede estar vacío");
                        }
                        if (correo.isEmpty()) {
                            throw new IllegalArgumentException("Correo no puede estar vacío");
                        }
                        if (!telefono.isEmpty() && !telefonoPattern.matcher(telefono).matches()) {
                            throw new IllegalArgumentException("Teléfono debe tener formato +598XXXXXXXX");
                        }

                        f.setCi(ci);
                        f.setNombre(nombre);
                        f.setCorreo(correo);
                        f.setTelefono(telefono.isEmpty() ? "" : telefono);
                        funcionarios.add(f);

                    } catch (Exception ex) {
                        errores.add("Línea " + lineNumber + ": " + ex.getMessage());
                    }
                }

                if (!errores.isEmpty()) {
                    Notification.show("Errores en CSV: " + String.join("; ", errores), 5000, Notification.Position.MIDDLE);
                }

                if (!funcionarios.isEmpty()) {
                    service.importarDesdeCSV(funcionarios);
                    actualizarGrid();
                    Notification.show("Importación exitosa: " + funcionarios.size() + " funcionarios procesados", 3000, Notification.Position.MIDDLE);
                }

            } catch (Exception ex) {
                Notification.show("Error al procesar CSV: " + ex.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        });

        return upload;
    }

    private String normalizarTelefonoParaGuardar(String telefono) {
        if (telefono == null || telefono.trim().isEmpty()) {
            return "";
        }

        telefono = telefono.trim();

        // Remover todo excepto dígitos
        String soloDigitos = telefono.replaceAll("[^0-9]", "");

        System.out.println("🔧 Normalizando teléfono: " + telefono + " -> dígitos: " + soloDigitos);

        // Caso 1: 8 dígitos (número local)
        if (soloDigitos.length() == 8) {
            String normalizado = "+598" + soloDigitos;
            System.out.println("   ✅ Formato local: " + normalizado);
            return normalizado;
        }

        // Caso 2: 9 dígitos que empiezan con 0 (0 + 8 dígitos)
        else if (soloDigitos.length() == 9 && soloDigitos.startsWith("0")) {
            String normalizado = "+598" + soloDigitos.substring(1);
            System.out.println("   ✅ Con 0 inicial: " + normalizado);
            return normalizado;
        }

        // Caso 3: 11 dígitos que empiezan con 598 (código sin +)
        else if (soloDigitos.length() == 11 && soloDigitos.startsWith("598")) {
            String normalizado = "+" + soloDigitos;
            System.out.println("   ✅ Con código 598: " + normalizado);
            return normalizado;
        }

        // Caso 4: 12 dígitos que empiezan con 598 (ya tiene +)
        else if (soloDigitos.length() == 12 && soloDigitos.startsWith("598")) {
            // Asegurar que tenga el +
            String normalizado = soloDigitos.startsWith("+") ? soloDigitos : "+" + soloDigitos;
            System.out.println("   ✅ Ya normalizado: " + normalizado);
            return normalizado;
        }

        // Caso 5: Cualquier otro número de 12 dígitos
        else if (soloDigitos.length() == 12) {
            String normalizado = "+" + soloDigitos;
            System.out.println("   ✅ Asumiendo formato internacional: " + normalizado);
            return normalizado;
        }

        // Caso 6: Si el usuario ingresó +598 y 8 dígitos (formato correcto)
        else if (telefono.matches("\\+598\\d{8}")) {
            System.out.println("   ✅ Formato correcto: " + telefono);
            return telefono;
        }

        // Si no coincide con ningún patrón, devolver vacío
        System.out.println("   ⚠️ No se pudo normalizar, devolviendo original");
        return telefono;
    }

    private Anchor crearBotonDescargaCSV() {
        StreamResource recurso = new StreamResource("funcionarios.csv", () -> {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);

                CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                        .withHeader("ci", "nombre", "correo", "telefono"));

                List<Funcionario> funcionarios = service.listarTodos();
                for (Funcionario f : funcionarios) {
                    printer.printRecord(f.getCi(), f.getNombre(), f.getCorreo(), f.getTelefono());
                }

                printer.flush();
                writer.flush();
                return new ByteArrayInputStream(outputStream.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
                return new ByteArrayInputStream(new byte[0]);
            }
        });

        Anchor boton = new Anchor(recurso, "");
        boton.getElement().setAttribute("download", true);
        Button downloadButton = new Button("Descargar CSV", new Icon(VaadinIcon.DOWNLOAD));
        boton.add(downloadButton);
        return boton;
    }

    private void abrirFormulario(Funcionario funcionarioExistente) {
        Dialog dialog = new Dialog();
        dialog.setModal(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.setHeaderTitle(funcionarioExistente == null ? "Nuevo Funcionario" : "Editar Funcionario");

        VerticalLayout formLayout = new VerticalLayout();
        formLayout.setSpacing(true);
        formLayout.setPadding(false);

        // Campo CI
        TextField ciField = new TextField("Cédula de Identidad");
        ciField.setRequired(true);
        ciField.setRequiredIndicatorVisible(true);
        // Nuevo patrón para formato 1234567-0
        ciField.setPattern("\\d{7}-\\d");
        ciField.setHelperText("Ingrese 7 dígitos, guión y 1 dígito (ej: 1234567-0)");
        ciField.setErrorMessage("Formato inválido. Use: 1234567-0");

        // Campo Nombre
        TextField nombreField = new TextField("Nombre Completo");
        nombreField.setRequired(true);
        nombreField.setRequiredIndicatorVisible(true);
        nombreField.setMinLength(3);
        nombreField.setMaxLength(100);
        nombreField.setErrorMessage("El nombre debe tener al menos 3 caracteres");

        // Campo Correo
        EmailField correoField = new EmailField("Correo Electrónico");
        correoField.setRequired(true);
        correoField.setRequiredIndicatorVisible(true);
        correoField.setErrorMessage("Ingrese un correo válido");
        correoField.setClearButtonVisible(true);

        // Campo Teléfono
        TextField telefonoField = new TextField("Teléfono");
        telefonoField.setRequired(false);
        telefonoField.setHelperText("Formato: +598XXXXXXXX (8 dígitos después del código)");
        telefonoField.setPattern("\\+598\\d{8}");
        telefonoField.setErrorMessage("Formato inválido. Use +598 seguido de 8 dígitos");
        telefonoField.setClearButtonVisible(true);

        // Pre-cargar valores si es edición
        if (funcionarioExistente != null) {
            ciField.setValue(funcionarioExistente.getCi());
            ciField.setEnabled(false); // No permitir modificar CI en edición
            nombreField.setValue(funcionarioExistente.getNombre());
            correoField.setValue(funcionarioExistente.getCorreo());
            telefonoField.setValue(funcionarioExistente.getTelefono());
        }

        // Botones del formulario
        HorizontalLayout botonesLayout = new HorizontalLayout();
        botonesLayout.setSpacing(true);

        Button guardarBtn = new Button("Guardar", new Icon(VaadinIcon.CHECK));
        guardarBtn.getElement().getThemeList().add("primary");
        guardarBtn.addClickListener(e -> {
            // Validaciones
            if (!ciField.isInvalid() && !nombreField.isInvalid() &&
                    !correoField.isInvalid() && !telefonoField.isInvalid()) {

                String telefono = telefonoField.getValue().trim();
                if (!telefono.isEmpty() && !telefonoPattern.matcher(telefono).matches()) {
                    telefonoField.setInvalid(true);
                    Notification.show("Formato de teléfono inválido. Use +598XXXXXXXX", 3000, Notification.Position.MIDDLE);
                    return;
                }

                // Validar CI
                String ci = ciField.getValue().trim();
                if (!ciPattern.matcher(ci).matches()) {
                    Notification.show("Formato de CI inválido. Use: 1234567-0", 3000, Notification.Position.MIDDLE);
                    return;
                }

                // Normalizar teléfono: quitar espacios y asegurar formato
                if (!telefono.isEmpty()) {
                    telefono = normalizarTelefonoParaGuardar(telefono);
                    telefonoField.setValue(telefono);
                }



                Funcionario funcionario = funcionarioExistente != null ? funcionarioExistente : new Funcionario();
                funcionario.setCi(ci);
                funcionario.setNombre(nombreField.getValue().trim());
                funcionario.setCorreo(correoField.getValue().trim());
                funcionario.setTelefono(telefono);

                try {
                    service.guardar(funcionario);
                    dialog.close();
                    actualizarGrid();
                    Notification.show("Funcionario guardado exitosamente", 3000, Notification.Position.MIDDLE);
                } catch (Exception ex) {
                    Notification.show("Error al guardar: " + ex.getMessage(), 3000, Notification.Position.MIDDLE);
                }
            }
        });

        Button cancelarBtn = new Button("Cancelar", new Icon(VaadinIcon.CLOSE));
        cancelarBtn.addClickListener(e -> dialog.close());

        botonesLayout.add(guardarBtn, cancelarBtn);

        formLayout.add(ciField, nombreField, correoField, telefonoField, botonesLayout);
        dialog.add(formLayout);
        dialog.open();
    }
}