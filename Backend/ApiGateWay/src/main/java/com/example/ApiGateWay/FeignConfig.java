package com.example.ApiGateWay;

import feign.Logger;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new ErrorDecoder.Default() {
            @Override
            public Exception decode(String methodKey, feign.Response response) {

                // methodKey выглядит так: "AuthServiceClient#login(Map)"
                String serviceName = methodKey.split("#")[0];

                switch (serviceName) {
                    case "AuthServiceClient":
                        return handleAuthError(response);
                    case "PaymentServiceClient":
                        return handlePaymentError(response);
                    case "ProductServiceClient":
                        return handleProductError(response);
                    default:
                        return handleGenericError(response);
                }
            }

            private Exception handleAuthError(feign.Response response) {
                switch (response.status()) {
                    case 401: return new RuntimeException("Неверный логин/пароль");
                    case 403: return new RuntimeException("Аккаунт заблокирован");
                    default: return new RuntimeException("Ошибка авторизации");
                }
            }

            private Exception handlePaymentError(feign.Response response) {
                System.out.println("=== PAYMENT ERROR ===");
                System.out.println("Status: " + response.status());
                System.out.println("Reason: " + response.reason());

                switch (response.status()) {
                    case 401: return new RuntimeException("Ошибка авторизации");
                    case 402: return new RuntimeException("Недостаточно средств");
                    case 404: return new RuntimeException("Счет не найден");
                    default: return new RuntimeException("Ошибка платежной системы (HTTP " + response.status() + ")");
                }
            }

            private Exception handleProductError(feign.Response response) {
                switch (response.status()) {
                    case 404: return new RuntimeException("Товар не найден");
                    case 400: return new RuntimeException("Некорректные данные товара");
                    default: return new RuntimeException("Ошибка сервиса товаров");
                }
            }

            private Exception handleGenericError(feign.Response response) {
                return new RuntimeException("Ошибка сервиса: HTTP " + response.status());
            }
        };
    }

    @Bean
    public FeignRequestInterceptor feignRequestInterceptor() {
        return new FeignRequestInterceptor();
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL; // Логи для всех сервисов
    }
}