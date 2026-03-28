package com.example.ApiGateWay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class
ApiGateWayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGateWayApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            if (factory instanceof TomcatServletWebServerFactory) {
                TomcatServletWebServerFactory tomcatFactory = (TomcatServletWebServerFactory) factory;
                tomcatFactory.addConnectorCustomizers(connector -> {
                    connector.setProperty("maxHttpHeaderSize", "8192");
                    // Отключаем chunked encoding
                    connector.setProperty("compressibleMimeType", "");
                });
            }
        };
    }
}