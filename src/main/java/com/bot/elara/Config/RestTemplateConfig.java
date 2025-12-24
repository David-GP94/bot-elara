package com.bot. elara.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate con timeouts configurados para llamadas a APIs externas
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Configurar timeouts (en milisegundos)
        factory.setConnectTimeout(5000);  // 5 segundos para conectar
        factory.setReadTimeout(10000);    // 10 segundos para leer respuesta

        return new RestTemplate(factory);
    }
}