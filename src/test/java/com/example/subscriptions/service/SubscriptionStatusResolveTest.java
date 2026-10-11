package com.example.subscriptions.service;

import com.example.subscriptions.domain.enums.SubscriptionStatus;
import com.example.subscriptions.support.Req;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Регламентное задание: правило перевода статусов по датам")
public class SubscriptionStatusResolveTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 31); // 30 дней

    @ParameterizedTest(name = "[R13] {0} на {1} → {2}")
    @Req("R13")
    @CsvSource({
            "NEW,       2025-12-31, NEW",       // ещё не началась
            "NEW,       2026-01-01, ACTIVE",    // первый день — активна
            "NEW,       2026-01-30, ACTIVE",    // последний день — активна
            "ACTIVE,    2026-01-31, EXPIRED",   // дата окончания — уже истекла
            "NEW,       2026-03-01, EXPIRED",   // задание не запускалось весь срок → сразу «Истекла»
            "EXPIRED,   2026-03-01, EXPIRED",
            "CANCELLED, 2026-01-15, CANCELLED", // отменённую не трогаем
            "CANCELLED, 2026-03-01, CANCELLED",
    })
    @DisplayName("[R13] Статус по датам: [start, end) — активна, с end — истекла")
    void resolve(SubscriptionStatus current, LocalDate today, SubscriptionStatus expected) {
        assertThat(SubscriptionStatusService.resolve(current, START, END, today)).isEqualTo(expected);
    }
}
