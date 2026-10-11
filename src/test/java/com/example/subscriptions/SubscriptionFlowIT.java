package com.example.subscriptions;

import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.catalogs.Tariff;
import com.example.subscriptions.domain.documents.Payment;
import com.example.subscriptions.domain.documents.Subscription;
import com.example.subscriptions.domain.documents.SubscriptionLine;
import com.example.subscriptions.domain.enums.PaymentMethod;
import com.example.subscriptions.domain.enums.SubscriptionStatus;
import com.example.subscriptions.domain.registers.AccountBalance;
import com.example.subscriptions.domain.registers.TariffRevenue;
import com.example.subscriptions.jobs.SubscriptionStatusJob;
import com.example.subscriptions.repository.AccountBalanceRepository;
import com.example.subscriptions.repository.ClientRepository;
import com.example.subscriptions.repository.PaymentRepository;
import com.example.subscriptions.repository.SubscriptionRepository;
import com.example.subscriptions.repository.TariffRepository;
import com.example.subscriptions.repository.TariffRevenueRepository;
import com.example.subscriptions.service.SubscriptionCancellationService;
import com.example.subscriptions.service.SubscriptionCancellationService.CancellationRejected;
import com.example.subscriptions.service.SubscriptionStatusService;
import com.example.subscriptions.support.Req;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import su.onno.posting.PostingService;
import su.onno.types.Ref;
import su.onno.validation.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Сквозные тесты на поднятом приложении (onno + Spring + H2 в памяти): настоящая схема,
 * сохранение через репозитории, проведение через {@link PostingService}, чтение остатков и
 * оборотов из регистров.
 *
 * <p>Тесты намеренно НЕ {@code @Transactional}: onno требует, чтобы документ был сохранён и
 * закоммичен до проведения (проведение владеет своей транзакцией). Изоляция — новые клиенты и
 * тарифы в каждом тесте.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:subscriptions_it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.demo-data.enabled=false",
        "org.jobrunr.background-job-server.enabled=false",
        "spring.main.banner-mode=off"
})
@DisplayName("Сквозные сценарии: проведение, регистры, отмена, регламентное задание")
class SubscriptionFlowIT {

    private static final LocalDateTime FROM = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2100, 1, 1, 0, 0);

    @Autowired ClientRepository clients;
    @Autowired TariffRepository tariffs;
    @Autowired PaymentRepository payments;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired AccountBalanceRepository balances;
    @Autowired TariffRevenueRepository revenue;
    @Autowired PostingService posting;
    @Autowired SubscriptionCancellationService cancellation;
    @Autowired SubscriptionStatusService statuses;
    @Autowired SubscriptionStatusJob statusJob;

    // ---- helpers ---------------------------------------------------------------------------------

    private Ref<Client> newClient() {
        Client c = new Client();
        c.setDescription("Клиент " + UUID.randomUUID());
        c.setEmail("c" + System.nanoTime() + "@example.com");
        return Ref.of(Client.class, clients.save(c).getId());
    }

    private Ref<Tariff> newTariff(String price, int days, boolean available) {
        Tariff t = new Tariff();
        t.setDescription("Тариф " + UUID.randomUUID());
        t.setPrice(new BigDecimal(price));
        t.setPeriodDays(days);
        t.setAvailable(available);
        return Ref.of(Tariff.class, tariffs.save(t).getId());
    }

    private Payment pay(Ref<Client> client, String amount) {
        Payment p = new Payment();
        p.setClient(client);
        p.setAmount(new BigDecimal(amount));
        p.setMethod(PaymentMethod.CARD);
        Payment saved = payments.save(p);
        posting.post(saved);
        return saved;
    }

    private static SubscriptionLine line(Ref<Tariff> tariff, int periods) {
        SubscriptionLine line = new SubscriptionLine();
        line.setTariff(tariff);
        line.setPeriods(periods);
        return line;
    }

    private Subscription draft(Ref<Client> client, LocalDate start, SubscriptionLine... lines) {
        Subscription s = new Subscription();
        s.setClient(client);
        s.setStartDate(start);
        s.getLines().addAll(List.of(lines));
        return subscriptions.save(s);
    }

    private Subscription reload(Subscription s) {
        return subscriptions.findById(s.getId()).orElseThrow();
    }

    private BigDecimal balanceOf(Ref<Client> client) {
        return balances.getBalance(f -> f.where(AccountBalance::getClient, client)).stream()
                .map(AccountBalance::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<TariffRevenue> revenueOf(Ref<Client> client) {
        return revenue.getTurnover(FROM, TO, f -> f.where(TariffRevenue::getClient, client));
    }

    private static Throwable rootValidation(Throwable t) {
        Throwable cur = t;
        while (cur != null && !(cur instanceof ValidationException)) {
            cur = cur.getCause();
        }
        return cur == null ? t : cur;
    }

    // ---- сценарии --------------------------------------------------------------------------------

    @Test
    @Req("R1")
    @Req("R15")
    @DisplayName("[R1][R15] Проведённые платежи пополняют лицевой счёт; остаток читается из регистра")
    void paymentsTopUpAccount() {
        Ref<Client> client = newClient();
        assertThat(balanceOf(client)).isEqualByComparingTo("0");

        pay(client, "1000.00");
        pay(client, "250.50");

        assertThat(balanceOf(client)).isEqualByComparingTo("1250.50");
    }

    @Test
    @Req("R2")
    @Req("R6")
    @Req("R15")
    @DisplayName("[R2][R6][R15] Проведение подписки списывает итог и признаёт выручку по тарифам и клиенту")
    void postingSubscriptionChargesAndRecognisesRevenue() {
        Ref<Client> client = newClient();
        Ref<Tariff> month = newTariff("1000.00", 30, true);
        Ref<Tariff> year = newTariff("9000.00", 365, true);
        pay(client, "15000.00");

        Subscription s = draft(client, LocalDate.now(), line(month, 2), line(year, 1));
        assertThat(s.getTotal()).isEqualByComparingTo("11000.00");
        assertThat(balanceOf(client)).as("до проведения ничего не списано").isEqualByComparingTo("15000.00");

        posting.post(s);

        assertThat(reload(s).isPosted()).isTrue();
        assertThat(balanceOf(client)).isEqualByComparingTo("4000.00");
        List<TariffRevenue> rows = revenueOf(client);
        assertThat(rows).extracting(TariffRevenue::getTariff).containsExactlyInAnyOrder(month, year);
        assertThat(rows.stream().filter(r -> r.getTariff().equals(month)).findFirst().orElseThrow())
                .satisfies(r -> {
                    assertThat(r.getAmount()).isEqualByComparingTo("2000.00");
                    assertThat(r.getPeriods()).isEqualByComparingTo("2");
                });
        assertThat(rows.stream().map(TariffRevenue::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("11000.00");
    }

    @Test
    @Req("R3")
    @DisplayName("[R3] При нехватке денег подписка не проводится и движений нет")
    void cannotPostWithoutEnoughMoney() {
        Ref<Client> client = newClient();
        Ref<Tariff> month = newTariff("1000.00", 30, true);
        pay(client, "999.99");
        Subscription s = draft(client, LocalDate.now(), line(month, 1));

        assertThatThrownBy(() -> posting.post(s))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Недостаточно средств");

        assertThat(reload(s).isPosted()).isFalse();
        assertThat(balanceOf(client)).isEqualByComparingTo("999.99");
        assertThat(balances.getRecordsByDocument(s.getId())).isEmpty();
        assertThat(revenueOf(client)).isEmpty();
    }

    @Test
    @Req("R3")
    @DisplayName("[R3] Регистр остатков не уходит в минус: нельзя распровести уже потраченный платёж")
    void balanceRegisterNeverGoesNegative() {
        Ref<Client> client = newClient();
        Ref<Tariff> month = newTariff("1000.00", 30, true);
        Payment payment = pay(client, "1000.00");
        posting.post(draft(client, LocalDate.now(), line(month, 1)));
        assertThat(balanceOf(client)).isEqualByComparingTo("0");

        assertThatThrownBy(() -> posting.unpost(payments.findById(payment.getId()).orElseThrow()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("negative balance");
        assertThat(balanceOf(client)).isEqualByComparingTo("0");
    }

    @Test
    @Req("R4")
    @DisplayName("[R4] Срок действия = дата начала + максимальный срок строк (365 и 30 → 365)")
    void endDateIsStartPlusMaxTerm() {
        Ref<Client> client = newClient();
        LocalDate start = LocalDate.of(2026, 3, 1);
        Subscription s = draft(client, start,
                line(newTariff("100.00", 30, true), 1),
                line(newTariff("500.00", 365, true), 1));

        Subscription loaded = reload(s);
        assertThat(loaded.getEndDate()).isEqualTo(start.plusDays(365));
        assertThat(loaded.getLines()).extracting(SubscriptionLine::getDays).containsExactly(30, 365);
    }

    @Test
    @Req("R5")
    @DisplayName("[R5] Подписка, проведённая в статусе «Отменена», не создаёт движений")
    void cancelledSubscriptionPostsNothing() {
        Ref<Client> client = newClient();
        Subscription s = draft(client, LocalDate.now(), line(newTariff("1000.00", 30, true), 1));
        s = reload(s);
        s.setStatus(SubscriptionStatus.CANCELLED);
        s = subscriptions.save(s);

        posting.post(s); // денег на счёте нет — и не нужно

        assertThat(reload(s).isPosted()).isTrue();
        assertThat(balances.getRecordsByDocument(s.getId())).isEmpty();
        assertThat(revenue.getRecordsByDocument(s.getId())).isEmpty();
        assertThat(balanceOf(client)).isEqualByComparingTo("0");
    }

    @Test
    @Req("R5")
    @Req("R14")
    @DisplayName("[R5][R14] Отмена проведённой подписки с причиной: деньги возвращаются, выручки нет")
    void cancellingPostedSubscriptionReversesMovements() {
        Ref<Client> client = newClient();
        pay(client, "3000.00");
        Subscription s = draft(client, LocalDate.now(), line(newTariff("1000.00", 30, true), 2));
        posting.post(s);
        assertThat(balanceOf(client)).isEqualByComparingTo("1000.00");

        Subscription cancelled = cancellation.cancel(s.getId(), "Клиент передумал");

        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo("Клиент передумал");
        assertThat(cancelled.getCancelled()).isTrue();
        assertThat(balanceOf(client)).isEqualByComparingTo("3000.00");
        assertThat(revenueOf(client).stream().map(TariffRevenue::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0");
        assertThat(balances.getRecordsByDocument(s.getId())).noneMatch(AccountBalance::isActive);
    }

    @Test
    @Req("R14")
    @DisplayName("[R14] Отмена требует причину и невозможна повторно")
    void cancellationGuards() {
        Ref<Client> client = newClient();
        Subscription s = draft(client, LocalDate.now(), line(newTariff("10.00", 30, true), 1));

        assertThatThrownBy(() -> cancellation.cancel(s.getId(), "  "))
                .isInstanceOf(CancellationRejected.class).hasMessageContaining("причину");

        cancellation.cancel(s.getId(), "Ошибка оформления");
        assertThatThrownBy(() -> cancellation.cancel(s.getId(), "ещё раз"))
                .isInstanceOf(CancellationRejected.class).hasMessageContaining("уже отменена");
    }

    @Test
    @Req("R7")
    @Req("R8")
    @Req("R9")
    @DisplayName("[R7][R8][R9] Сохранение без клиента / без строк / с нулём периодов отклоняется")
    void invalidSubscriptionsAreRejectedOnSave() {
        Ref<Tariff> month = newTariff("10.00", 30, true);

        Subscription noClient = new Subscription();
        noClient.getLines().add(line(month, 1));
        assertThatThrownBy(() -> subscriptions.save(noClient))
                .satisfies(e -> assertThat(rootValidation(e)).isInstanceOf(ValidationException.class));

        Subscription noLines = new Subscription();
        noLines.setClient(newClient());
        assertThatThrownBy(() -> subscriptions.save(noLines))
                .satisfies(e -> assertThat(rootValidation(e))
                        .isInstanceOf(ValidationException.class)
                        .hasMessageContaining("хотя бы одну строку"));

        Subscription zeroPeriods = new Subscription();
        zeroPeriods.setClient(newClient());
        zeroPeriods.getLines().add(line(month, 0));
        assertThatThrownBy(() -> subscriptions.save(zeroPeriods))
                .satisfies(e -> assertThat(rootValidation(e))
                        .isInstanceOf(ValidationException.class)
                        .hasMessageContaining("больше нуля"));
    }

    @Test
    @Req("R10")
    @DisplayName("[R10] Тариф, снятый с продажи, нельзя подключить")
    void unavailableTariffIsRejected() {
        Subscription s = new Subscription();
        s.setClient(newClient());
        s.getLines().add(line(newTariff("10.00", 30, false), 1));

        assertThatThrownBy(() -> subscriptions.save(s))
                .satisfies(e -> assertThat(rootValidation(e))
                        .isInstanceOf(ValidationException.class)
                        .hasMessageContaining("недоступен для подключения"));
    }

    @Test
    @Req("R11")
    @Req("R12")
    @DisplayName("[R11][R12] При создании подставляются даты, цена строки — из тарифа")
    void autofillOnCreate() {
        Ref<Tariff> month = newTariff("777.00", 30, true);
        Subscription s = new Subscription();
        s.setStartDate(null);
        s.setClient(newClient());
        s.getLines().add(line(month, 3));

        Subscription saved = reload(subscriptions.save(s));

        assertThat(saved.getNumber()).isNotBlank();
        assertThat(saved.getDate()).isNotNull();
        assertThat(saved.getStartDate()).isEqualTo(saved.getDate().toLocalDate());
        assertThat(saved.getLines().get(0).getPrice()).isEqualByComparingTo("777.00");
        assertThat(saved.getLines().get(0).getAmount()).isEqualByComparingTo("2331.00");
        assertThat(saved.getTotal()).isEqualByComparingTo("2331.00");
        assertThat(saved.getEndDate()).isEqualTo(saved.getStartDate().plusDays(90));
    }

    @Test
    @Req("R13")
    @DisplayName("[R13] Статусы по датам: «Оформлена» → «Активна» → «Истекла»; черновики и отменённые не трогаются")
    void statusTransitionsByDates() {
        Ref<Client> client = newClient();
        pay(client, "100.00");
        LocalDate start = LocalDate.now().plusDays(10);
        Subscription future = draft(client, start, line(newTariff("10.00", 30, true), 1));
        posting.post(future);
        Subscription unposted = draft(client, start, line(newTariff("10.00", 30, true), 1));
        Subscription cancelled = draft(client, start, line(newTariff("10.00", 30, true), 1));
        cancellation.cancel(cancelled.getId(), "тест");

        assertThat(reload(future).getStatus()).as("ещё не началась").isEqualTo(SubscriptionStatus.NEW);

        statuses.refreshAll(start);
        assertThat(reload(future).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        statuses.refreshAll(start.plusDays(30));
        assertThat(reload(future).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(reload(unposted).getStatus()).as("черновик").isEqualTo(SubscriptionStatus.NEW);
        assertThat(reload(cancelled).getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    @Req("R13")
    @DisplayName("[R13] Регламентное задание (BackgroundTask) переводит устаревший статус по сегодняшней дате")
    void scheduledJobUpdatesStaleStatuses() {
        Ref<Client> client = newClient();
        pay(client, "100.00");
        Subscription old = draft(client, LocalDate.now().minusDays(40), line(newTariff("10.00", 30, true), 1));
        posting.post(old);

        // Имитируем «задание давно не запускалось»: статус устарел.
        Subscription stale = reload(old);
        stale.setStatus(SubscriptionStatus.ACTIVE);
        subscriptions.save(stale);

        statusJob.execute();

        assertThat(reload(old).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @Req("R13")
    @DisplayName("[R13] Сразу после проведения статус выставляется по датам (без ожидания задания)")
    void statusIsSetRightAfterPosting() {
        Ref<Client> client = newClient();
        pay(client, "100.00");
        Subscription s = draft(client, LocalDate.now(), line(newTariff("10.00", 30, true), 1));

        posting.post(s);

        assertThat(reload(s).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }
}
