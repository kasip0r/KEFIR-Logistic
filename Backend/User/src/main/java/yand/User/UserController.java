package yand.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

@RestController
@RequestMapping("/api/clients")
public class UserController {

    @Autowired
    private UserRepository clientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // GET всех клиентов
    @GetMapping
    public ResponseEntity<List<User>> getAllClients() {
        try {
            List<User> clients = clientRepository.findAll();
            // Убираем пароли из ответа для безопасности
            clients.forEach(client -> client.setPassword(null));
            return ResponseEntity.ok(clients);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable int id) {
        return clientRepository.findById(id)
                .map(user -> {
                    // Здесь пароль можно убрать — для UI
                    user.setPassword(null);
                    return ResponseEntity.ok(convertUserToMap(user));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Проверка доступности email
    @PostMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");

            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success", false,
                                "available", false,
                                "message", "Email не указан"
                        ));
            }

            // Проверка формата email
            if (!email.contains("@")) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "available", false,
                        "message", "Некорректный формат email",
                        "valid", false
                ));
            }

            // Проверка существования в БД
            boolean exists = clientRepository.findByEmail(email).isPresent();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "available", !exists,
                    "exists", exists,
                    "message", exists ? "Email уже используется" : "Email свободен",
                    "valid", true
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "available", false,
                            "message", "Ошибка при проверке email"
                    ));
        }
    }

    // Проверка доступности username
    @PostMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");

            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "success", false,
                                "available", false,
                                "message", "Логин не указан"
                        ));
            }

            // Проверка минимальной длины
            if (username.length() < 3) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "available", false,
                        "message", "Логин должен быть не менее 3 символов",
                        "valid", false
                ));
            }

            // Проверка существования в БД
            boolean exists = clientRepository.findByUsername(username).isPresent();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "available", !exists,
                    "exists", exists,
                    "message", exists ? "Логин уже занят" : "Логин свободен",
                    "valid", true
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "available", false,
                            "message", "Ошибка при проверке логина"
                    ));
        }
    }

    // Единый endpoint для проверки всех полей
    @PostMapping("/validate")
    public ResponseEntity<?> validateFields(@RequestBody Map<String, String> request) {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);

            String email = request.get("email");
            String username = request.get("username");

            // Проверка email
            if (email != null) {
                boolean emailExists = false;
                boolean emailValid = email.contains("@");

                if (emailValid) {
                    emailExists = clientRepository.findByEmail(email).isPresent();
                }

                result.put("email", Map.of(
                        "available", !emailExists && emailValid,
                        "exists", emailExists,
                        "valid", emailValid,
                        "message", emailValid ?
                                (emailExists ? "Email уже используется" : "Email свободен") :
                                "Некорректный формат email"
                ));
            }

            // Проверка username
            if (username != null) {
                boolean usernameExists = false;
                boolean usernameValid = username.length() >= 3;

                if (usernameValid) {
                    usernameExists = clientRepository.findByUsername(username).isPresent();
                }

                result.put("username", Map.of(
                        "available", !usernameExists && usernameValid,
                        "exists", usernameExists,
                        "valid", usernameValid,
                        "message", usernameValid ?
                                (usernameExists ? "Логин уже занят" : "Логин свободен") :
                                "Логин должен быть не менее 3 символов"
                ));
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Ошибка валидации"
                    ));
        }
    }



    // PUT - обновление клиента (ИСПРАВЛЕННЫЙ)



    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, Object> userData) {
        try {
            System.out.println("=== USER SERVICE DEBUG ===");
            System.out.println("Получен JSON: " + new ObjectMapper().writeValueAsString(userData));

            // 1. Извлечение данных
            String username = (String) userData.get("username");
            String password = (String) userData.get("password");
            String email = (String) userData.get("email");
            String firstname = (String) userData.get("firstname");

            System.out.println("Извлечено:");
            System.out.println("  username: " + username);
            System.out.println("  password: " + (password != null ? "[PRESENT]" : "null"));
            System.out.println("  email: " + email);
            System.out.println("  firstname: " + firstname);

            // 2. Проверяем кодировку пароля
            if (password != null) {
                System.out.println("  Длина пароля: " + password.length());
                System.out.println("  Первые 10 chars пароля: " + password.substring(0, Math.min(10, password.length())));
                try {
                    System.out.println("  Пароль в bytes: " + Arrays.toString(password.getBytes("UTF-8")));
                } catch (Exception e) {
                    System.out.println("  Ошибка кодировки пароля: " + e.getMessage());
                }
            }

            // 3. Проверка уникальности
            System.out.println("Проверяем уникальность username...");
            boolean usernameExists = clientRepository.findByUsername(username).isPresent();
            System.out.println("  username существует: " + usernameExists);

            System.out.println("Проверяем уникальность email...");
            boolean emailExists = clientRepository.findByEmail(email).isPresent();
            System.out.println("  email существует: " + emailExists);

            // 4. Создаем пользователя
            User user = new User();
            user.setUsername(username);
            user.setFirstname(firstname);
            user.setEmail(email);

            System.out.println("Перед хешированием пароля...");
            String encodedPassword = passwordEncoder.encode(password);
            System.out.println("  Пароль захеширован, длина хеша: " + encodedPassword.length());
            user.setPassword(encodedPassword);

            user.setRole("client");
            user.setStatus("active");

            // 5. Сохраняем
            System.out.println("Сохраняем в БД...");
            User savedUser = clientRepository.save(user);
            System.out.println("✅ Сохранено! ID: " + savedUser.getId());
            System.out.println("  Автогенерация ID: " + savedUser.getId());

            // 6. Сразу проверим, что сохранено
            System.out.println("Проверяем сохранение...");
            Optional<User> verified = clientRepository.findById(savedUser.getId());
            if (verified.isPresent()) {
                User dbUser = verified.get();
                System.out.println("✅ Найдено в БД:");
                System.out.println("  ID: " + dbUser.getId());
                System.out.println("  Username: " + dbUser.getUsername());
                System.out.println("  Email: " + dbUser.getEmail());
                System.out.println("  Firstname: " + dbUser.getFirstname());
                System.out.println("  Role: " + dbUser.getRole());
                System.out.println("  Status: " + dbUser.getStatus());
                System.out.println("  CreatedAt: " + dbUser.getCreatedAt());
            } else {
                System.out.println("❌ Не найдено в БД после сохранения!");
            }

            // 7. Подготовка ответа
            System.out.println("Подготавливаем ответ...");
            savedUser.setPassword(null);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Регистрация успешна");
            response.put("user", Map.of(
                    "id", savedUser.getId(),
                    "username", savedUser.getUsername(),
                    "email", savedUser.getEmail(),
                    "firstname", savedUser.getFirstname(),
                    "role", savedUser.getRole(),
                    "status", savedUser.getStatus(),
                    "createdAt", savedUser.getCreatedAt()
            ));

            System.out.println("✅ Отправляем ответ: " + response);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            System.err.println("❌ ИСКЛЮЧЕНИЕ в UserService:");
            System.err.println("  Тип: " + e.getClass().getName());
            System.err.println("  Сообщение: " + e.getMessage());
            System.err.println("  Причина: " + (e.getCause() != null ? e.getCause().getMessage() : "null"));

            // Выводим полный stack trace
            e.printStackTrace();

            String errorMsg = e.getMessage();
            if (errorMsg == null) {
                errorMsg = "Unknown error";
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Ошибка сервера при регистрации: " + errorMsg
                    ));
        }
    }

    @GetMapping("/by-username/{username}")
    public ResponseEntity<Map<String, Object>> getUserByUsername(@PathVariable String username) {
        return clientRepository.findByUsername(username)
                .map(user -> {
                    // Не убираем пароль — он нужен Auth Service для проверки
                    return ResponseEntity.ok(convertUserToMap(user));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> convertUserToMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("firstname", user.getFirstname());
        map.put("email", user.getEmail());
        map.put("role", user.getRole());
        map.put("status", user.getStatus());
        map.put("password", user.getPassword()); // важно: оставляем для Auth
        return map;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            String dbStatus = checkDatabase();
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "service", "user-service",
                    "timestamp", System.currentTimeMillis(),
                    "database", dbStatus
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "DOWN",
                            "error", e.getMessage()
                    ));
        }
    }

    private String checkDatabase() {
        try {
            clientRepository.count();
            return "connected";
        } catch (Exception e) {
            return "disconnected: " + e.getMessage();
        }
    }
}