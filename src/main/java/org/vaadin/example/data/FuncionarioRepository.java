package org.vaadin.example.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FuncionarioRepository extends JpaRepository<Funcionario, String> {
    Optional<Funcionario> findByCorreoIgnoreCase(String remitente);

    void deleteByCi(String ci);

    Optional<Funcionario> findByTelefono(String telefono);


    // Nuevos métodos para Telegram
    Optional<Funcionario> findByTelegramChatId(String chatId);
    Optional<Funcionario> findByCi(String ci);

    // Método para buscar por correo o teléfono
    Optional<Funcionario> findByCorreoOrTelefono(String correo, String telefono);

    Optional<Funcionario> findByWhatsappLid(String whatsappLid);

    Optional<Funcionario> findByWhatsappCodigoActivacion(String codigo);

    @Query("SELECT f FROM Funcionario f WHERE f.whatsappLid = :lid AND f.whatsappActivado = true")
    Optional<Funcionario> findActivoByWhatsappLid(@Param("lid") String lid);


}
