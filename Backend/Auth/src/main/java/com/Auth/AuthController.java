package com.Auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private PasswordEncoderConfig.PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private SessionService sessionService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            System.out.println("=== AUTH SERVICE LOGIN (VIA USER SERVICE) ===");

            String username = request.get("username");
            String password = request.get("password");

            System.out.println("Username: " + username);

            // 1. Запрашиваем пользователя из User Service
            ResponseEntity<Map<String, Object>> userResponse = userServiceClient.getUserByUsername(username);

            if (!userResponse.getStatusCode().is2xxSuccessful() || userResponse.getBody() == null) {
                System.out.println("User not found in User Service: " + username);
                return ResponseEntity.status(400)
                        .body(Map.of(
                                "success", false,
                                "error", "Пользователь не найден"
                        ));
            }

            Map<String, Object> userData = userResponse.getBody();
            System.out.println("User data received: " + userData);

            // 2. Проверяем статус
            String status = (String) userData.get("status");
            if ("banned".equalsIgnoreCase(status)) {
                return ResponseEntity.status(403)
                        .body(Map.of(
                                "success", false,
                                "error", "Ваш аккаунт заблокирован",
                                "status", "banned"
                        ));
            }

            // 3. Проверяем пароль
            String encodedPassword = (String) userData.get("password");
            boolean passwordMatches = passwordEncoder.matches(password, encodedPassword);
            System.out.println("Password matches: " + passwordMatches);

            if (!passwordMatches) {
                System.out.println("Invalid password for user: " + username);
                return ResponseEntity.status(400)
                        .body(Map.of(
                                "success", false,
                                "error", "Неверный пароль"
                        ));
            }

            // 4. Извлекаем userId
            Integer userId = (Integer) userData.get("id");

            // 5. Генерируем UUID для сессии
            String sessionUUID = UUID.randomUUID().toString();
            System.out.println("Generated session UUID: " + sessionUUID);

            String clientToken = "auth-" + sessionUUID;

            // 6. Создаём JWT (опционально)
            String jwtToken = null;
            try {
                jwtToken = createJwtToken(userData, sessionUUID);
                System.out.println("JWT created successfully");
            } catch (Exception jwtError) {
                System.err.println("⚠️ JWT creation failed: " + jwtError.getMessage());
                jwtToken = "jwt-error-" + sessionUUID;
            }

            // 7. Сохраняем сессию
            try {
                sessionService.createUserSession(sessionUUID, userId, jwtToken);
                System.out.println("Session saved successfully");
            } catch (Exception sessionError) {
                System.err.println("⚠️ Session creation failed: " + sessionError.getMessage());
            }

            // 8. Формируем ответ
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", clientToken);
            response.put("sessionId", sessionUUID);
            response.put("user", createUserResponse(userData));
            response.put("message", "Вход выполнен успешно");

            if (jwtToken != null && !jwtToken.startsWith("jwt-error-")) {
                response.put("jwtToken", jwtToken);
                response.put("tokenType", "hybrid-uuid-jwt");
            } else {
                response.put("tokenType", "uuid-only");
                response.put("warning", "JWT не сгенерирован, используется только UUID");
            }

            System.out.println("✅ Login successful for user: " + username);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("ERROR in login: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "success", false,
                            "error", "Внутренняя ошибка сервера: " + e.getMessage()
                    ));
        }
    }

    // Вспомогательный метод для создания JWT из Map
    private String createJwtToken(Map<String, Object> userData, String sessionUUID) {
        try {
            String username = (String) userData.get("username");
            Integer userId = (Integer) userData.get("id");
            String role = (String) userData.get("role");
            String status = (String) userData.get("status");
            String firstname = (String) userData.get("firstname");
            String email = (String) userData.get("email");

            System.out.println("Creating JWT token for user: " + username);

            Map<String, Object> claims = new HashMap<>();
            claims.put("sessionId", sessionUUID);
            claims.put("userId", userId);
            claims.put("role", role);
            claims.put("status", status);
            claims.put("firstname", firstname);
            claims.put("email", email);

            String jwtToken = jwtUtil.generateTokenWithClaims(username, claims);
            System.out.println("✅ JWT token created successfully");

            return jwtToken;
        } catch (Exception e) {
            System.err.println("❌ Failed to create JWT token: " + e.getMessage());
            return "temp-jwt-" + sessionUUID + "-" + System.currentTimeMillis();
        }
    }

    @PostMapping("/extract-user-id")
    public ResponseEntity<?> extractUserId(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "clientToken", required = false) String clientToken) {

        try {
            Integer userId = null;
            String role = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                if (token.startsWith("auth-")) {
                    Optional<UserSession> session = sessionService.validateSession(token);
                    if (session.isPresent()) {
                        userId = session.get().getUserId();

                        // ✅ ПОЛУЧАЕМ РОЛЬ ИЗ USER SERVICE
                        ResponseEntity<Map<String, Object>> userResponse =
                                userServiceClient.getUserById(userId);

                        if (userResponse.getStatusCode().is2xxSuccessful() && userResponse.getBody() != null) {
                            role = (String) userResponse.getBody().get("role");
                            System.out.println("✅ Role from user service: " + role);
                        }
                    }
                }
            }

            if (userId != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("userId", userId);
                response.put("role", role);
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Вспомогательный метод для создания ответа пользователя из Map
    private Map<String, Object> createUserResponse(Map<String, Object> userData) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", userData.get("id"));
        userResponse.put("username", userData.get("username"));
        userResponse.put("firstname", userData.get("firstname") != null ? userData.get("firstname") : "");
        userResponse.put("age", userData.get("age") != null ? userData.get("age") : 0);
        userResponse.put("city", userData.get("city") != null ? userData.get("city") : "");
        userResponse.put("magaz", userData.get("magaz") != null ? userData.get("magaz") : "");
        userResponse.put("email", userData.get("email") != null ? userData.get("email") : "");
        userResponse.put("status", userData.get("status") != null ? userData.get("status") : "active");
        userResponse.put("role", userData.get("role") != null ? userData.get("role") : "client");
        userResponse.put("createdAt", userData.get("createdAt") != null ? userData.get("createdAt").toString() : "");
        userResponse.put("updatedAt", userData.get("updatedAt") != null ? userData.get("updatedAt").toString() : "");
        return userResponse;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "service", "auth-service",
                    "timestamp", System.currentTimeMillis(),
                    "database", checkDatabase()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of(
                            "status", "DOWN",
                            "error", e.getMessage()
                    ));
        }
    }

    private String checkDatabase() {
        try {
            // Проверяем, что таблица сессий доступна
            sessionService.getActiveSessionsCount();
            return "connected";
        } catch (Exception e) {
            return "disconnected: " + e.getMessage();
        }
    }
}