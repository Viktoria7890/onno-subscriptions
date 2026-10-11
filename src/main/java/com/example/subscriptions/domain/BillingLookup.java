package com.example.subscriptions.domain;

import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.catalogs.Tariff;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Чтения, которые нужны хукам документа подписки ({@code beforeWrite}, {@code rules},
 * {@code beforePost}): тариф по ссылке и текущий остаток лицевого счёта.
 *
 * <p>Хуки onno выполняются на доменном объекте, созданном рефлексией, — внедрения зависимостей
 * Spring там нет. Поэтому домен зависит только от этого маленького интерфейса, а реализация
 * регистрируется через {@link #install(BillingLookup)}: в приложении это делает Spring-бин
 * {@link com.example.subscriptions.service.BillingLookupInstaller}, в unit-тестах — фейк в памяти.</p>
 */
public interface BillingLookup {

    Optional<Tariff> tariff(Ref<Tariff> ref);

    /** Текущий остаток лицевого счёта клиента (0, если движений не было). */
    BigDecimal balance(Ref<Client> client);

    /**
     * Сколько документ уже списал с лицевых счетов (активные расходы в регистре). Нужно при
     * перепроведении: движок сначала сторнирует прежнее списание, поэтому его надо прибавить к
     * доступному остатку.
     */
    BigDecimal chargedBy(UUID documentId);

    /** Реализация «по умолчанию» до старта контекста: ничего не знает. */
    BillingLookup NONE = new BillingLookup() {
        @Override
        public Optional<Tariff> tariff(Ref<Tariff> ref) {
            return Optional.empty();
        }

        @Override
        public BigDecimal balance(Ref<Client> client) {
            return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal chargedBy(UUID documentId) {
            return BigDecimal.ZERO;
        }
    };

    static BillingLookup current() {
        return Holder.current;
    }

    static void install(BillingLookup lookup) {
        Holder.current = lookup == null ? NONE : lookup;
    }

    /** Статическое хранилище (интерфейсы не могут иметь изменяемых статических полей). */
    final class Holder {
        private static volatile BillingLookup current = NONE;

        private Holder() {
        }
    }
}
