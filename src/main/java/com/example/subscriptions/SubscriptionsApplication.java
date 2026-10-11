package com.example.subscriptions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * Сервис учёта подписок клиентов на тарифные планы, построенный на onno-framework.
 * Таблицы, REST API, формы и миграции схемы генерируются фреймворком из метамодели в
 * {@code domain}; руками написаны только бизнес-логика и описание интерфейса ({@code ui}).
 */
@SpringBootApplication
public class SubscriptionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubscriptionsApplication.class, args);
    }

    /** Часы приложения — отдельным бином, чтобы тесты могли «перевести время». */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
