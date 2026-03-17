package org.vaadin.example.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vaadin.example.data.Funcionario;
import org.vaadin.example.data.FuncionarioRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class FuncionarioService {

    private final FuncionarioRepository repo;

    public FuncionarioService(FuncionarioRepository repo) {
        this.repo = repo;
    }

    public List<Funcionario> listarTodos() {
        return repo.findAll();
    }

    @Transactional
    public void guardar(Funcionario funcionario) {
        repo.save(funcionario);
    }

    @Transactional
    public void eliminar(String ci) {
        repo.deleteByCi(ci);
    }

    @Transactional
    public void importarDesdeCSV(List<Funcionario> funcionarios) {
        repo.saveAll(funcionarios);
    }

    @Transactional(readOnly = true)
    public Optional<Funcionario> buscarPorTelefono(String telefono) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔍 BUSQUEDA INICIADA para: '" + telefono + "'");

        if (telefono == null || telefono.isEmpty()) {
            System.out.println("❌ Teléfono vacío");
            return Optional.empty();
        }

        // PASO 1: Limpiar el teléfono de búsqueda
        String telefonoLimpio = limpiarYNormalizarTelefonoBusqueda(telefono);
        System.out.println("📱 Teléfono limpio para búsqueda: '" + telefonoLimpio + "'");

        // PASO 2: Extraer los 8 dígitos locales del teléfono buscado
        String digitosLocalesBuscados = extraer8DigitosLocales(telefonoLimpio);
        System.out.println("🔢 8 dígitos locales extraídos: '" + digitosLocalesBuscados + "'");

        if (digitosLocalesBuscados.length() != 8) {
            System.out.println("❌ No se pudieron extraer 8 dígitos válidos");
            System.out.println("=".repeat(60));
            return Optional.empty();
        }

        // PASO 3: Obtener TODOS los funcionarios
        List<Funcionario> todosFuncionarios = repo.findAll();
        System.out.println("👥 Total funcionarios en BD: " + todosFuncionarios.size());

        // PASO 4: Buscar coincidencia
        for (Funcionario funcionario : todosFuncionarios) {
            String telefonoBD = funcionario.getTelefono();

            if (telefonoBD == null || telefonoBD.isEmpty()) {
                continue;
            }

            System.out.println("\n   🔍 Analizando funcionario: " + funcionario.getNombre());
            System.out.println("      📞 Teléfono en BD: '" + telefonoBD + "'");

            // Extraer 8 dígitos locales del teléfono en BD
            String digitosLocalesBD = extraer8DigitosLocales(telefonoBD);
            System.out.println("      🔢 8 dígitos BD: '" + digitosLocalesBD + "'");

            // Comparar
            if (digitosLocalesBD.equals(digitosLocalesBuscados)) {
                System.out.println("      ✅ ¡COINCIDENCIA ENCONTRADA!");
                System.out.println("=".repeat(60));
                return Optional.of(funcionario);
            } else {
                System.out.println("      ❌ No coincide");
            }
        }

        System.out.println("\n❌ NINGUNA COINCIDENCIA ENCONTRADA");
        System.out.println("=".repeat(60));
        return Optional.empty();
    }

    /**
     * Limpia y normaliza un teléfono para búsqueda
     */
    private String limpiarYNormalizarTelefonoBusqueda(String telefono) {
        if (telefono == null) return "";

        System.out.println("   🧹 Limpiando teléfono: '" + telefono + "'");

        // 1. Remover caracteres no numéricos (pero conservar + para diagnóstico)
        String soloDigitos = telefono.replaceAll("[^0-9]", "");
        System.out.println("     Solo dígitos: '" + soloDigitos + "'");

        // 2. Si está vacío, devolver vacío
        if (soloDigitos.isEmpty()) {
            return "";
        }

        // 3. Manejar diferentes formatos
        String resultado = soloDigitos;

        // Caso 1: Tiene exactamente 8 dígitos (número local)
        if (soloDigitos.length() == 8) {
            System.out.println("     ✅ Formato: 8 dígitos locales");
            resultado = soloDigitos;
        }
        // Caso 2: Tiene 9 dígitos y empieza con 0 (0 + 8 dígitos)
        else if (soloDigitos.length() == 9 && soloDigitos.startsWith("0")) {
            System.out.println("     ✅ Formato: 0 + 8 dígitos");
            resultado = soloDigitos.substring(1);
        }
        // Caso 3: Tiene 11 dígitos (formato uruguayo sin +)
        else if (soloDigitos.length() == 11) {
            System.out.println("     📞 Formato 11 dígitos");
            if (soloDigitos.startsWith("598")) {
                // Es uruguayo con código de país
                resultado = soloDigitos.substring(3);
                System.out.println("     ✅ Uruguayo con 598: '" + resultado + "'");
            } else {
                // Otro país, tomamos últimos 8 como fallback
                resultado = soloDigitos.substring(soloDigitos.length() - 8);
                System.out.println("     ⚠️ Formato internacional, últimos 8: '" + resultado + "'");
            }
        }
        // Caso 4: Tiene 12 dígitos (formato internacional con +)
        else if (soloDigitos.length() == 12) {
            System.out.println("     📞 Formato 12 dígitos");
            if (soloDigitos.startsWith("598")) {
                // Es uruguayo con código de país y sin +
                resultado = soloDigitos.substring(3);
                System.out.println("     ✅ Uruguayo con 598: '" + resultado + "'");
            } else {
                // Otro país, tomamos últimos 8
                resultado = soloDigitos.substring(soloDigitos.length() - 8);
                System.out.println("     ⚠️ Formato internacional, últimos 8: '" + resultado + "'");
            }
        }
        // Caso 5: Más de 12 dígitos
        else if (soloDigitos.length() > 12) {
            System.out.println("     ⚠️ Más de 12 dígitos, extrayendo últimos 11");
            String ultimos11 = soloDigitos.substring(soloDigitos.length() - 11);
            if (ultimos11.startsWith("598")) {
                resultado = ultimos11.substring(3);
            } else {
                resultado = ultimos11.substring(ultimos11.length() - 8);
            }
        }
        // Caso 6: Otros formatos (menos de 8 dígitos)
        else {
            System.out.println("     ⚠️ Formato no reconocido, longitud: " + soloDigitos.length());
            // Si tiene menos de 8, dejamos como está (fallará después)
            resultado = soloDigitos;
        }

        System.out.println("     📍 Resultado normalizado: '" + resultado + "'");
        return resultado;
    }

    /**
     * Extrae los 8 dígitos locales de un teléfono
     */
    private String extraer8DigitosLocales(String telefono) {
        if (telefono == null || telefono.isEmpty()) {
            System.out.println("      ⚠️ Teléfono vacío en extraer8DigitosLocales");
            return "";
        }

        System.out.println("      📞 Extrayendo 8 dígitos de: '" + telefono + "'");

        // Obtener solo dígitos
        String soloDigitos = telefono.replaceAll("[^0-9]", "");
        System.out.println("      🔢 Solo dígitos: '" + soloDigitos + "'");

        // Si no hay dígitos, retornar vacío
        if (soloDigitos.isEmpty()) {
            return "";
        }

        // Para Uruguay: queremos los últimos 8 dígitos (número local)
        if (soloDigitos.length() >= 8) {
            String resultado = soloDigitos.substring(soloDigitos.length() - 8);
            System.out.println("      ✅ 8 dígitos extraídos: '" + resultado + "'");
            return resultado;
        } else {
            // Si tiene menos de 8 dígitos, rellenar con ceros a la izquierda
            String resultado = String.format("%8s", soloDigitos).replace(' ', '0');
            System.out.println("      ⚠️ Menos de 8 dígitos, rellenado: '" + resultado + "'");
            return resultado;
        }
    }

    @Transactional(readOnly = true)
    public Optional<Funcionario> buscarPorCorreo(String correo) {
        return repo.findByCorreoIgnoreCase(correo);
    }

    @Transactional(readOnly = true)
    public Optional<Funcionario> buscarPorTelegramChatId(String chatId) {
        return repo.findByTelegramChatId(chatId);
    }





    @Transactional(readOnly = true)
    public Optional<Funcionario> buscarPorCi(String ci) {
        if (ci == null || ci.isEmpty()) {
            return Optional.empty();
        }
        return repo.findByCi(ci.trim());
    }

    @Transactional
    public void actualizarTelegramChatId(String ci, String chatId) {
        buscarPorCi(ci).ifPresent(funcionario -> {
            funcionario.setTelegramChatId(chatId);
            repo.save(funcionario);
        });
    }

    @Transactional
    public void desactivarTelegram(String ci) {
        buscarPorCi(ci).ifPresent(funcionario -> {
            funcionario.setTelegramChatId(null);
            funcionario.setTelegramActivado(false);
            funcionario.setTelegramCodigoActivacion(null);
            funcionario.setTelegramCodigoExpiracion(null);
            repo.save(funcionario);
        });
    }


    // Método adicional para debugging
    public List<String> listarTodosTelefonos() {
        return repo.findAll().stream()
                .map(Funcionario::getTelefono)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Funcionario> buscarPorWhatsappLid(String lid) {
        if (lid == null || lid.isEmpty()) {
            return Optional.empty();
        }
        return repo.findByWhatsappLid(lid);
    }



    @Transactional
    public void guardarCodigoActivacionWhatsapp(String lid, String correo, String codigo) {
        Optional<Funcionario> funcionarioOpt = buscarPorCorreo(correo);

        if (funcionarioOpt.isPresent()) {
            Funcionario f = funcionarioOpt.get();
            f.setWhatsappLid(lid);
            f.setWhatsappCodigoActivacion(codigo);
            f.setWhatsappCodigoExpiracion(LocalDateTime.now().plusMinutes(15));
            f.setWhatsappActivado(false);
            repo.save(f);
        }
    }

    @Transactional
    public boolean activarWhatsappConCodigo(String lid, String codigo) {
        Optional<Funcionario> funcionarioOpt = repo.findByWhatsappLid(lid);

        if (funcionarioOpt.isPresent()) {
            Funcionario f = funcionarioOpt.get();

            if (codigo.equals(f.getWhatsappCodigoActivacion()) &&
                    LocalDateTime.now().isBefore(f.getWhatsappCodigoExpiracion())) {

                f.setWhatsappActivado(true);
                f.setWhatsappRegistradoEn(LocalDateTime.now());
                f.setWhatsappCodigoActivacion(null);
                f.setWhatsappCodigoExpiracion(null);
                repo.save(f);

                return true;
            }
        }
        return false;
    }

    @Transactional
    public void limpiarCodigoActivacionWhatsapp(String lid) {
        buscarPorWhatsappLid(lid).ifPresent(f -> {
            f.setWhatsappCodigoActivacion(null);
            f.setWhatsappCodigoExpiracion(null);
            repo.save(f);
        });
    }

    private String generarCodigo6Digitos() {
        return String.format("%06d", new Random().nextInt(999999));
    }


}