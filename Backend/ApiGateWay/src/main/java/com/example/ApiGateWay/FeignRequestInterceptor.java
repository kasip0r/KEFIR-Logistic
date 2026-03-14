package com.example.ApiGateWay;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class FeignRequestInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignRequestInterceptor.class);

    @Override
    public void apply(RequestTemplate template) {
        try {
            log.info("🔍 Feign interceptor - applying to: {}", template.url());

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                // 1. Пытаемся получить роль из разных мест
                String role = null;

                // Из request attribute
                if (request != null) {
                    role = (String) request.getAttribute("X-User-Role");
                    log.info("🔍 Role from request attribute: {}", role);
                }

                // Из RequestContextHolder
                if (role == null && attributes != null) {
                    role = (String) attributes.getAttribute("X-User-Role", RequestAttributes.SCOPE_REQUEST);
                    log.info("🔍 Role from RequestContextHolder: {}", role);
                }

                // 2. Получаем Authorization header
                String authHeader = request != null ? request.getHeader("Authorization") : null;

                log.info("🔍 Feign interceptor - role: {}, auth header present: {}",
                        role, authHeader != null ? "yes" : "no");

                // 3. Добавляем роль в заголовок, если нашли
                if (role != null && !role.isEmpty()) {
                    template.header("X-User-Role", role);
                    log.info("✅ Added X-User-Role header: {}", role);
                } else {
                    log.warn("⚠️ Role not found in any context");
                }

                // 4. Проксируем Authorization заголовок
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    template.header("Authorization", authHeader);
                    log.info("🔑 Token found, added to request");
                } else {
                    log.info("ℹ️ No Authorization header to proxy");
                }

                // 5. Проксируем другие важные заголовки (опционально)
                proxyHeader(request, template, "X-Request-ID");
                proxyHeader(request, template, "X-Forwarded-For");

            } else {
                log.warn("⚠️ No request attributes found in Feign interceptor");
            }
        } catch (Exception e) {
            log.error("❌ Error in Feign interceptor: {}", e.getMessage(), e);
        }
    }

    private void proxyHeader(HttpServletRequest request, RequestTemplate template, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (headerValue != null && !headerValue.isEmpty()) {
            template.header(headerName, headerValue);
            log.debug("📋 Proxied header: {} = {}", headerName, headerValue);
        }
    }
}