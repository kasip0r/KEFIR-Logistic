package yand.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/clients")  // ← Правильный базовый путь
public class AdminUserController {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository clientRepository;

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClient(
            @PathVariable int id,
            @RequestHeader(value = "X-User-Role", required = false) String role) {

        System.out.println("=== DELETE CLIENT ===");
        System.out.println("ID: " + id);
        System.out.println("Role header: " + role);

        if (!"ADMIN".equalsIgnoreCase(role)) {
            System.out.println("❌ Access denied for role: " + role);
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "error", "Access denied. Admin role required."
            ));
        }

        return clientRepository.findById(id)
                .map(client -> {
                    clientRepository.delete(client);
                    System.out.println("✅ Client " + id + " deleted successfully");
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "message", "Client deleted successfully"
                    ));
                })
                .orElseGet(() -> {
                    System.out.println("❌ Client " + id + " not found");
                    return ResponseEntity.status(404).body(Map.of(
                            "success", false,
                            "error", "Client not found"
                    ));
                });
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateClient(@PathVariable int id, @RequestBody User clientDetails,
                                          @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"admin".equalsIgnoreCase(role)) {  // ← принимаем "admin" в любом регистре
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "error", "Access denied. Admin role required."
            ));
        }
        return clientRepository.findById(id)
                .map(client -> {
                    // Обновляем поля
                    if (clientDetails.getUsername() != null) {
                        client.setUsername(clientDetails.getUsername());
                    }

                    if (clientDetails.getEmail() != null) {
                        client.setEmail(clientDetails.getEmail());
                    }

                    if (clientDetails.getFirstname() != null) {
                        client.setFirstname(clientDetails.getFirstname());
                    }

                    if (clientDetails.getCity() != null) {
                        client.setCity(clientDetails.getCity());
                    }

                    if (clientDetails.getRole() != null) {
                        client.setRole(clientDetails.getRole());
                    }

                    if (clientDetails.getStatus() != null) {
                        client.setStatus(clientDetails.getStatus());
                    }

                    // ВАЖНО: Обновляем пароль, если он предоставлен
                    if (clientDetails.getPassword() != null &&
                            !clientDetails.getPassword().isEmpty()) {
                        // Хешируем новый пароль
                        String encodedPassword = passwordEncoder.encode(clientDetails.getPassword());
                        client.setPassword(encodedPassword);
                    }

                    User updated = clientRepository.save(client);
                    updated.setPassword(null); // Убираем пароль из ответа
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createClient(@RequestBody User client,
                                          @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"admin".equalsIgnoreCase(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access denied"));
        }
        try {
            // 1. Проверка обязательных полей
            if (client.getUsername() == null || client.getUsername().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Username is required"));
            }

            if (client.getPassword() == null || client.getPassword().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Password is required"));
            }

            // 2. Проверка уникальности username
            if (clientRepository.findByUsername(client.getUsername()).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Username already exists"));
            }

            // 3. Хеширование пароля
            String encodedPassword = passwordEncoder.encode(client.getPassword());
            client.setPassword(encodedPassword);

            // 4. Установка дефолтных значений
            if (client.getStatus() == null) {
                client.setStatus("active");
            }

            if (client.getRole() == null) {
                client.setRole("client");
            }

            // 5. Сохранение
            User savedClient = clientRepository.save(client);

            // 6. Убираем пароль из ответа
            savedClient.setPassword(null);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "message", "User created successfully",
                            "user", savedClient
                    ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create user: " + e.getMessage()));
        }
    }
}