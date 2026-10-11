package com.example.subscriptions.seed;

import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.catalogs.Tariff;
import com.example.subscriptions.domain.documents.Payment;
import com.example.subscriptions.domain.documents.Subscription;
import com.example.subscriptions.domain.documents.SubscriptionLine;
import com.example.subscriptions.domain.enums.ClientStatus;
import com.example.subscriptions.domain.enums.PaymentMethod;
import com.example.subscriptions.repository.ClientRepository;
import com.example.subscriptions.repository.PaymentRepository;
import com.example.subscriptions.repository.SubscriptionRepository;
import com.example.subscriptions.repository.TariffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import su.onno.posting.PostingService;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Демо-данные при первом запуске (если справочник тарифов пуст), чтобы в интерфейсе сразу было на
 * что посмотреть. Отключается свойством {@code app.demo-data.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final TariffRepository tariffs;
    private final ClientRepository clients;
    private final PaymentRepository payments;
    private final SubscriptionRepository subscriptions;
    private final PostingService posting;

    public DemoDataSeeder(TariffRepository tariffs, ClientRepository clients, PaymentRepository payments,
                          SubscriptionRepository subscriptions, PostingService posting) {
        this.tariffs = tariffs;
        this.clients = clients;
        this.payments = payments;
        this.subscriptions = subscriptions;
        this.posting = posting;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tariffs.findAllActive().isEmpty()) {
            return;
        }
        try {
            seed();
            log.info("Демо-данные созданы");
        } catch (RuntimeException e) {
            log.warn("Не удалось создать демо-данные: {}", e.getMessage(), e);
        }
    }

    private void seed() {
        Tariff basic = tariff("Базовый", "990.00", 30, true);
        Tariff pro = tariff("Профи", "2490.00", 30, true);
        Tariff annual = tariff("Годовой", "9900.00", 365, true);
        tariff("Архивный (снят с продажи)", "500.00", 30, false);

        Client romashka = client("ООО «Ромашка»", "info@romashka.example", "+7 495 000-00-01");
        Client ivanov = client("Иванов Иван", "ivanov@example.com", "+7 900 000-00-02");
        Client petrova = client("Петрова Анна", "petrova@example.com", "+7 900 000-00-03");

        pay(romashka, "15000.00", PaymentMethod.BANK_TRANSFER, 20);
        pay(ivanov, "3000.00", PaymentMethod.CARD, 10);
        pay(petrova, "1000.00", PaymentMethod.SBP, 3);

        // Годовой + Профи в одном документе → срок 365 дней (максимум среди строк).
        subscribe(romashka, LocalDate.now().minusDays(15), line(annual, 1), line(pro, 1));
        // Месяц «Базового»: начался 40 дней назад → уже истёк.
        subscribe(ivanov, LocalDate.now().minusDays(40), line(basic, 1));
        // Черновик: проведения не было, демонстрирует ручное оформление.
        Subscription draft = new Subscription();
        draft.setClient(ref(Client.class, petrova));
        draft.getLines().add(line(pro, 1));
        subscriptions.save(draft);
    }

    private Tariff tariff(String name, String price, int days, boolean available) {
        Tariff t = new Tariff();
        t.setDescription(name);
        t.setPrice(new BigDecimal(price));
        t.setPeriodDays(days);
        t.setAvailable(available);
        return tariffs.save(t);
    }

    private Client client(String name, String email, String phone) {
        Client c = new Client();
        c.setDescription(name);
        c.setEmail(email);
        c.setPhone(phone);
        c.setStatus(ClientStatus.ACTIVE);
        c.setRegistrationDate(LocalDate.now().minusDays(30));
        return clients.save(c);
    }

    private void pay(Client client, String amount, PaymentMethod method, int daysAgo) {
        Payment p = new Payment();
        p.setDate(LocalDateTime.now().minusDays(daysAgo));
        p.setClient(ref(Client.class, client));
        p.setAmount(new BigDecimal(amount));
        p.setMethod(method);
        posting.post(payments.save(p));
    }

    private void subscribe(Client client, LocalDate start, SubscriptionLine... lines) {
        Subscription s = new Subscription();
        s.setDate(start.atTime(10, 0));
        s.setClient(ref(Client.class, client));
        s.setStartDate(start);
        for (SubscriptionLine line : lines) {
            s.getLines().add(line);
        }
        posting.post(subscriptions.save(s));
    }

    private static SubscriptionLine line(Tariff tariff, int periods) {
        SubscriptionLine line = new SubscriptionLine();
        line.setTariff(ref(Tariff.class, tariff));
        line.setPeriods(periods);
        return line;
    }

    private static <T extends su.onno.model.CatalogObject> Ref<T> ref(Class<T> type, T entity) {
        return Ref.of(type, entity.getId());
    }
}
