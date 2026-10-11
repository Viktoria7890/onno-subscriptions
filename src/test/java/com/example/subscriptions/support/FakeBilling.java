package com.example.subscriptions.support;

import com.example.subscriptions.domain.BillingLookup;
import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.catalogs.Tariff;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** BillingLookup в памяти для unit-тестов доменной логики (без Spring и БД). */
public class FakeBilling implements BillingLookup {

    private final Map<UUID, Tariff> tariffs = new HashMap<>();
    private final Map<UUID, BigDecimal> balances = new HashMap<>();
    private final Map<UUID, BigDecimal> charged = new HashMap<>();

    public Ref<Tariff> tariff(String name, String price, int periodDays, boolean available) {
        Tariff t = new Tariff();
        t.setId(UUID.randomUUID());
        t.setDescription(name);
        t.setPrice(new BigDecimal(price));
        t.setPeriodDays(periodDays);
        t.setAvailable(available);
        tariffs.put(t.getId(), t);
        return Ref.of(Tariff.class, t.getId());
    }

    public Tariff get(Ref<Tariff> ref) {
        return tariffs.get(ref.id());
    }

    public Ref<Client> client(String balance) {
        UUID id = UUID.randomUUID();
        balances.put(id, new BigDecimal(balance));
        return Ref.of(Client.class, id);
    }

    public void charged(UUID documentId, String amount) {
        charged.put(documentId, new BigDecimal(amount));
    }

    @Override
    public Optional<Tariff> tariff(Ref<Tariff> ref) {
        return ref == null ? Optional.empty() : Optional.ofNullable(tariffs.get(ref.id()));
    }

    @Override
    public BigDecimal balance(Ref<Client> client) {
        return client == null ? BigDecimal.ZERO : balances.getOrDefault(client.id(), BigDecimal.ZERO);
    }

    @Override
    public BigDecimal chargedBy(UUID documentId) {
        return charged.getOrDefault(documentId, BigDecimal.ZERO);
    }
}
