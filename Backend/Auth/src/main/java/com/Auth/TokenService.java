package com.Auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final SessionService sessionService = new SessionService();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Извлекает userId из токена (поддерживает JWT и UUID токены)
     */
    public Integer extractUserIdFromToken(String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("⚠️ Отсутствует или некорректный Authorization header: {}", authHeader);
                throw new RuntimeException("Требуется авторизация");
            }

            String token = authHeader.substring(7);
            log.debug("Токен для парсинга: {}", token.substring(0, Math.min(token.length(), 50)) + "...");

            if (token.contains(".")) {
                return extractUserIdFromJwt(token);
            } else if (token.startsWith("auth-")) {
                return extractUserIdFromUuidToken(token);
            } else {
                throw new RuntimeException("Неизвестный формат токена");
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при извлечении userId: " + e.getMessage());
        }
    }

    /**
     * Извлекает userId из JWT токена
     */
    private Integer extractUserIdFromJwt(String jwtToken) throws Exception {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                throw new RuntimeException("Неверный формат JWT токена");
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            log.debug("JWT payload: {}", payloadJson);

            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);

            if (payload.containsKey("userId")) {
                Object userIdObj = payload.get("userId");
                return convertToInteger(userIdObj);
            }

            if (payload.containsKey("id")) {
                Object idObj = payload.get("id");
                return convertToInteger(idObj);
            }

            throw new RuntimeException("userId не найден в JWT токене");

        } catch (Exception e) {
            throw new RuntimeException("Ошибка парсинга JWT: " + e.getMessage());
        }
    }

    /**
     * Извлекает userId из UUID токена через Auth Service
     */
    // Вместо вызова самого себя, используем внутреннюю логику
    private Integer extractUserIdFromUuidToken(String uuidToken) {
        try {
            // Извлекаем sessionUUID из токена (auth-xxxxx)
            if (!uuidToken.startsWith("auth-")) {
                throw new RuntimeException("Invalid token format");
            }
            String sessionUUID = uuidToken.substring(5);

            // Прямой запрос в БД, а не HTTP вызов
            Optional<UserSession> session = sessionService.validateSession(uuidToken);
            if (session.isPresent()) {
                return session.get().getUserId();
            }
            throw new RuntimeException("Session not found");
        } catch (Exception e) {
            log.error("Failed to extract userId: {}", e.getMessage());
            throw new RuntimeException("Invalid session token");
        }
    }
    /**
     * Конвертирует Object в Integer
     */
    private Integer convertToInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof String) return Integer.parseInt((String) obj);
        if (obj instanceof Number) return ((Number) obj).intValue();
        throw new RuntimeException("Не могу преобразовать в Integer: " + obj.getClass());
    }
}