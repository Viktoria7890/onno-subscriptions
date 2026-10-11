package com.example.subscriptions.service;

import com.example.subscriptions.domain.BillingLookup;
import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.catalogs.Tariff;
import com.example.subscriptions.domain.registers.AccountBalance;
import com.example.subscriptions.repository.AccountBalanceRepository;
import com.example.subscriptions.repository.TariffRepository;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import su.onno.model.MovementType;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Связывает доменные хуки подписки с репозиториями Spring (см. {@link BillingLookup}).
 * Читает через «доверенные» типизированные репозитории; удалённые (deletionMark) тарифы не видны.
 */
@Component
public class BillingLookupInstaller implements BillingLookup, InitializingBean, DisposableBean {

    private final TariffRepository tariffs;
    private final AccountBalanceRepository balances;

    public BillingLookupInstaller(TariffRepository tariffs, AccountBalanceRepository balances) {
        this.tariffs = tariffs;
        this.balances = balances;
    }

    @Override
    public void afterPropertiesSet() {
        BillingLookup.install(this);
    }

    @Override
    public void destroy() {
        BillingLookup.install(null);
    }

    @Override
    public Optional<Tariff> tariff(Ref<Tariff> ref) {
        if (ref == null || ref.id() == null) {
            return Optional.empty();
        }
        return tariffs.findActiveById(ref.id());
    }

    @Override
    public BigDecimal balance(Ref<Client> client) {
        if (client == null) {
            return BigDecimal.ZERO;
        }
        return balances.getBalance(f -> f.where(AccountBalance::getClient, client)).stream()
                .map(AccountBalance::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public BigDecimal chargedBy(UUID documentId) {
        if (documentId == null) {
            return BigDecimal.ZERO;
        }
        return balances.getRecordsByDocument(documentId).stream()
                .filter(AccountBalance::isActive)
                .filter(r -> r.getMovementType() == MovementType.EXPENSE)
                .map(AccountBalance::getAmount)
                .filter(a -> a != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
