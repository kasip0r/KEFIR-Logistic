package com.Auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private PasswordEncoderConfig.PasswordEncoder passwordEncoder;

    public UserSession authenticate(String username, String password) {
        // 1. Запрашиваем пользователя из User Service
        ResponseEntity<Map<String, Object>> response = userServiceClient.getUserByUsername(username);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("User not found");
        }

        Map<String, Object> userData = response.getBody();

        // 2. Проверяем статус пользователя
        String status = (String) userData.get("status");
        if ("banned".equalsIgnoreCase(status)) {
            throw new RuntimeException("Account is banned");
        }

        // 3. Проверяем пароль
        String encodedPassword = (String) userData.get("password");
        if (!passwordEncoder.matches(password, encodedPassword)) {
            throw new RuntimeException("Invalid password");
        }

        // 4. Создаём сессию
        Integer userId = (Integer) userData.get("id");
        String sessionUUID = UUID.randomUUID().toString();
        String clientToken = "auth-" + sessionUUID;

        sessionService.createUserSession(sessionUUID, userId, null);

        // 5. Возвращаем сессию (можно сразу или через Optional)
        return sessionService.validateSession(clientToken)
                .orElseThrow(() -> new RuntimeException("Session creation failed"));
    }

    public UserSession getSessionByToken(String clientToken) {
        return sessionService.validateSession(clientToken)
                .orElseThrow(() -> new RuntimeException("Invalid or expired session"));
    }
}