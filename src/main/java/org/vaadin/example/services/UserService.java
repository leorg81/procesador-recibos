package org.vaadin.example.services;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.vaadin.example.data.User;
import org.vaadin.example.data.UserRepository;

@Service
public class UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> get(Long id) {
        return repository.findById(id);
    }

    public User save(User entity) {
        return repository.save(entity);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public Page<User> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Page<User> list(Pageable pageable, Specification<User> filter) {
        return repository.findAll(filter, pageable);
    }

    public List<User> findAll () {
        return repository.findAll();
    }

    public  Optional<User> findByCorreo(String correo){
        return repository.findByCorreo(correo);
    }


    public void update(User user) {
        try {
            System.out.println("Actualizando usuario: " + user.getUsername());
            System.out.println("Roles: " + user.getRoles());
            repository.save(user);
            System.out.println("Usuario actualizado correctamente");
        } catch (Exception e) {
            System.err.println("Error al actualizar usuario: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    public Optional<User> findByUsername(String username) {
        return repository.findByUsername(username);
    }
    public int count() {
        return (int) repository.count();
    }

    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public void updatePassword(User user, String newPassword) {
        user.setHashedPassword(passwordEncoder.encode(newPassword));
        repository.save(user);
    }

}
