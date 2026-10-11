package com.example.subscriptions.domain.documents;

import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.catalogs.Tariff;
import com.example.subscriptions.domain.enums.SubscriptionStatus;
import com.example.subscriptions.domain.registers.AccountBalance;
import com.example.subscriptions.domain.registers.TariffRevenue;
import com.example.subscriptions.support.FakeBilling;
import com.example.subscriptions.support.Req;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import su.onno.model.AccumulationRecord;
import su.onno.model.MovementType;
import su.onno.posting.PostingContext;
import su.onno.repository.RegisterRepositoryImpl;
import su.onno.rules.BusinessRule;
import su.onno.types.Ref;
import su.onno.validation.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-тесты доменной логики подписки: без Spring и БД, на фейковом {@link FakeBilling}.
 * Проверяют автоподстановку, пересчёт, бизнес-правила, проверку средств и состав движений.
 */
@DisplayName("Подписка: доменная логика")
public class SubscriptionDomainTest {

    private final FakeBilling billing = new FakeBilling();
    private final Ref<Tariff> monthly = billing.tariff("Месяц", "1000.00", 30, true);
    private final Ref<Tariff> annual = billing.tariff("Год", "9000.00", 365, true);
    private final Ref<Tariff> archived = billing.tariff("Архив", "100.00", 30, false);

    private static SubscriptionLine line(Ref<Tariff> tariff, Integer periods) {
        SubscriptionLine line = new SubscriptionLine();
        line.setTariff(tariff);
        line.setPeriods(periods);
        return line;
    }

    private Subscription subscription(Ref<Client> client, SubscriptionLine... lines) {
        Subscription s = new Subscription();
        s.setClient(client);
        s.setStartDate(LocalDate.of(2026, 1, 1));
        s.getLines().addAll(List.of(lines));
        return s;
    }

    private static List<String> failedRules(List<BusinessRule> rules) {
        return rules.stream().filter(r -> !r.holds()).map(BusinessRule::name).toList();
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Автоподстановка и пересчёт")
    class Recalculation {

        @Test
        @Req("R11")
        @DisplayName("[R11] onFilling подставляет дату документа и дату начала, не затирая заданные")
        void onFillingSetsDatesWithoutOverwriting() {
            Subscription fresh = new Subscription();
            fresh.setStartDate(null);
            fresh.onFilling();
            assertThat(fresh.getDate()).isNotNull();
            assertThat(fresh.getStartDate()).isEqualTo(fresh.getDate().toLocalDate());
            assertThat(fresh.getStatus()).isEqualTo(SubscriptionStatus.NEW);

            Subscription preset = new Subscription();
            LocalDateTime date = LocalDateTime.of(2025, 5, 5, 12, 0);
            preset.setDate(date);
            preset.setStartDate(LocalDate.of(2025, 6, 1));
            preset.onFilling();
            assertThat(preset.getDate()).isEqualTo(date);
            assertThat(preset.getStartDate()).isEqualTo(LocalDate.of(2025, 6, 1));
        }

        @Test
        @Req("R11")
        @DisplayName("[R11] Новая подписка уже на пустой форме имеет дату начала = сегодня и статус «Оформлена»")
        void blankFormDefaults() {
            Subscription blank = new Subscription();
            assertThat(blank.getStartDate()).isEqualTo(LocalDate.now());
            assertThat(blank.getStatus()).isEqualTo(SubscriptionStatus.NEW);
        }

        @Test
        @Req("R12")
        @DisplayName("[R12] Цена строки берётся из тарифа, сумма = цена × периоды, итог = сумма строк")
        void priceFromTariffAndTotals() {
            Subscription s = subscription(billing.client("0"), line(monthly, 3), line(annual, 1));
            s.recalculate(billing);

            assertThat(s.getLines().get(0).getPrice()).isEqualByComparingTo("1000.00");
            assertThat(s.getLines().get(0).getAmount()).isEqualByComparingTo("3000.00");
            assertThat(s.getLines().get(1).getPrice()).isEqualByComparingTo("9000.00");
            assertThat(s.getLines().get(1).getAmount()).isEqualByComparingTo("9000.00");
            assertThat(s.getTotal()).isEqualByComparingTo("12000.00");
        }

        @Test
        @Req("R12")
        @DisplayName("[R12] Пересчёт при каждом сохранении: изменение периодов меняет суммы и срок")
        void recalculatesOnEverySave() {
            Subscription s = subscription(billing.client("0"), line(monthly, 1));
            s.recalculate(billing);
            assertThat(s.getTotal()).isEqualByComparingTo("1000.00");
            assertThat(s.getEndDate()).isEqualTo(LocalDate.of(2026, 1, 31));

            s.getLines().get(0).setPeriods(2);
            s.recalculate(billing);
            assertThat(s.getTotal()).isEqualByComparingTo("2000.00");
            assertThat(s.getEndDate()).isEqualTo(LocalDate.of(2026, 3, 2));
        }

        @Test
        @Req("R12")
        @DisplayName("[R12] У проведённой подписки цена заморожена: смена цены тарифа не меняет суммы")
        void postedPriceIsFrozen() {
            Subscription s = subscription(billing.client("0"), line(monthly, 1));
            s.recalculate(billing);
            s.setPosted(true);

            billing.get(monthly).setPrice(new BigDecimal("5000.00"));
            s.recalculate(billing);

            assertThat(s.getLines().get(0).getPrice()).isEqualByComparingTo("1000.00");
            assertThat(s.getTotal()).isEqualByComparingTo("1000.00");
        }

        @Test
        @Req("R4")
        @DisplayName("[R4] Срок = дата начала + МАКСИМАЛЬНЫЙ срок строк (365 и 30 дней → 365)")
        void endDateUsesMaximumLineTerm() {
            Subscription s = subscription(billing.client("0"), line(annual, 1), line(monthly, 1));
            s.recalculate(billing);

            assertThat(s.getLines()).extracting(SubscriptionLine::getDays).containsExactly(365, 30);
            assertThat(s.getEndDate()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(365));
        }

        @Test
        @Req("R4")
        @DisplayName("[R4] Срок строки = длительность периода × число периодов (3 × 30 дней > 1 × 60)")
        void lineTermIsPeriodDaysTimesPeriods() {
            Ref<Tariff> twoMonths = billing.tariff("Два месяца", "1500.00", 60, true);
            Subscription s = subscription(billing.client("0"), line(monthly, 3), line(twoMonths, 1));
            s.recalculate(billing);

            assertThat(s.getEndDate()).isEqualTo(LocalDate.of(2026, 1, 1).plusDays(90));
        }

        @Test
        @Req("R5")
        @DisplayName("[R5] Флаг «Отменена» синхронизируется со статусом")
        void cancelledFlagFollowsStatus() {
            Subscription s = subscription(billing.client("0"), line(monthly, 1));
            s.recalculate(billing);
            assertThat(s.getCancelled()).isFalse();

            s.setStatus(SubscriptionStatus.CANCELLED);
            s.recalculate(billing);
            assertThat(s.getCancelled()).isTrue();
        }
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Бизнес-правила")
    class Rules {

        @Test
        @DisplayName("Корректная подписка проходит все правила")
        void validSubscriptionPasses() {
            Subscription s = subscription(billing.client("0"), line(monthly, 1));
            assertThat(failedRules(SubscriptionRules.rulesFor(s, billing))).isEmpty();
        }

        @Test
        @Req("R7")
        @DisplayName("[R7] Клиент обязателен (ошибка привязана к полю client)")
        void clientRequired() {
            Subscription s = subscription(null, line(monthly, 1));
            List<BusinessRule> rules = SubscriptionRules.rulesFor(s, billing);

            assertThat(failedRules(rules)).containsExactly(SubscriptionRules.CLIENT_REQUIRED);
            assertThat(rules.stream().filter(r -> !r.holds()).findFirst().orElseThrow().field())
                    .isEqualTo("client");
        }

        @Test
        @Req("R8")
        @DisplayName("[R8] Нужна хотя бы одна строка")
        void atLeastOneLine() {
            Subscription s = subscription(billing.client("0"));
            assertThat(failedRules(SubscriptionRules.rulesFor(s, billing)))
                    .containsExactly(SubscriptionRules.LINES_REQUIRED);
        }

        @Test
        @Req("R8")
        @DisplayName("[R8] В каждой строке должен быть выбран тариф")
        void tariffRequiredInEachLine() {
            Subscription s = subscription(billing.client("0"), line(monthly, 1), line(null, 1));
            List<BusinessRule> rules = SubscriptionRules.rulesFor(s, billing);

            assertThat(failedRules(rules)).containsExactly(SubscriptionRules.TARIFF_REQUIRED);
            assertThat(rules.stream().filter(r -> !r.holds()).findFirst().orElseThrow().message())
                    .contains("2");
        }

        @Test
        @Req("R9")
        @DisplayName("[R9] Число периодов должно быть больше нуля (0, отрицательное, пустое)")
        void periodsMustBePositive() {
            Subscription s = subscription(billing.client("0"),
                    line(monthly, 1), line(monthly, 0), line(monthly, -2), line(monthly, null));
            List<BusinessRule> rules = SubscriptionRules.rulesFor(s, billing);

            assertThat(failedRules(rules)).containsExactly(SubscriptionRules.PERIODS_POSITIVE);
            assertThat(rules.stream().filter(r -> !r.holds()).findFirst().orElseThrow().message())
                    .contains("2, 3, 4");
        }

        @Test
        @Req("R10")
        @DisplayName("[R10] Тариф должен быть доступен для подключения")
        void tariffMustBeAvailable() {
            Subscription s = subscription(billing.client("0"), line(monthly, 1), line(archived, 1));
            List<BusinessRule> rules = SubscriptionRules.rulesFor(s, billing);

            assertThat(failedRules(rules)).containsExactly(SubscriptionRules.TARIFF_AVAILABLE);
            assertThat(rules.stream().filter(r -> !r.holds()).findFirst().orElseThrow().message())
                    .contains("«Архив»");
        }

        @Test
        @Req("R10")
        @DisplayName("[R10] Удалённый/несуществующий тариф считается недоступным")
        void unknownTariffIsUnavailable() {
            Subscription s = subscription(billing.client("0"), line(Ref.of(Tariff.class, UUID.randomUUID()), 1));
            assertThat(failedRules(SubscriptionRules.rulesFor(s, billing)))
                    .containsExactly(SubscriptionRules.TARIFF_AVAILABLE);
        }

        @Test
        @Req("R10")
        @DisplayName("[R10] Снятие тарифа с продажи не ломает уже проведённую подписку")
        void availabilityIsCheckedOnlyBeforePosting() {
            Subscription s = subscription(billing.client("0"), line(archived, 1));
            s.setPosted(true);
            assertThat(failedRules(SubscriptionRules.rulesFor(s, billing))).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Проверка средств перед проведением")
    class Funds {

        @Test
        @Req("R3")
        @DisplayName("[R3] Нехватка денег → ValidationException с понятным сообщением")
        void insufficientFundsRejected() {
            Subscription s = subscription(billing.client("999.99"), line(monthly, 1));
            s.recalculate(billing);

            assertThatThrownBy(() -> s.ensureEnoughMoney(billing))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Недостаточно средств")
                    .hasMessageContaining("999,99")
                    .hasMessageContaining("1 000,00");
        }

        @Test
        @Req("R3")
        @DisplayName("[R3] Ровно хватает денег → можно проводить")
        void exactBalanceIsEnough() {
            Subscription s = subscription(billing.client("1000.00"), line(monthly, 1));
            s.recalculate(billing);
            assertThatCode(() -> s.ensureEnoughMoney(billing)).doesNotThrowAnyException();
        }

        @Test
        @Req("R3")
        @DisplayName("[R3] При перепроведении учитывается собственное прежнее списание")
        void repostAddsBackOwnCharge() {
            Subscription s = subscription(billing.client("0.00"), line(monthly, 1));
            s.setId(UUID.randomUUID());
            s.recalculate(billing);
            s.setPosted(true);
            billing.charged(s.getId(), "1000.00");

            assertThatCode(() -> s.ensureEnoughMoney(billing)).doesNotThrowAnyException();
        }

        @Test
        @Req("R3")
        @Req("R5")
        @DisplayName("[R3][R5] Отменённая подписка не требует денег")
        void cancelledNeedsNoMoney() {
            Subscription s = subscription(billing.client("0"), line(monthly, 1));
            s.setStatus(SubscriptionStatus.CANCELLED);
            s.recalculate(billing);
            assertThatCode(() -> s.ensureEnoughMoney(billing)).doesNotThrowAnyException();
        }
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("Движения проведения")
    class Movements {

        private PostingContext context() {
            // Репозитории без персистентности: в unit-тесте движения только «ставятся в очередь».
            Map<Class<?>, RegisterRepositoryImpl<?>> repositories = new HashMap<>();
            repositories.put(AccountBalance.class, new RegisterRepositoryImpl<AccountBalance>(null, AccountBalance.class));
            repositories.put(TariffRevenue.class, new RegisterRepositoryImpl<TariffRevenue>(null, TariffRevenue.class));
            return new PostingContext(repositories);
        }

        @SuppressWarnings("unchecked")
        private static <T extends AccumulationRecord> List<T> movements(PostingContext ctx, Class<T> register) {
            return ctx.touchedRepositories().stream()
                    .filter(r -> !r.getPendingMovements().isEmpty()
                            && register.isInstance(r.getPendingMovements().get(0)))
                    .flatMap(r -> r.getPendingMovements().stream())
                    .map(m -> (T) m)
                    .toList();
        }

        @Test
        @Req("R2")
        @Req("R6")
        @DisplayName("[R2][R6] Подписка: расход с лицевого счёта на итог + выручка по каждой строке")
        void subscriptionMovements() {
            Ref<Client> client = billing.client("100000");
            Subscription s = subscription(client, line(monthly, 2), line(annual, 1));
            s.recalculate(billing);

            PostingContext ctx = context();
            s.handlePosting(ctx);

            List<AccountBalance> balance = movements(ctx, AccountBalance.class);
            assertThat(balance).hasSize(1);
            assertThat(balance.get(0).getMovementType()).isEqualTo(MovementType.EXPENSE);
            assertThat(balance.get(0).getClient()).isEqualTo(client);
            assertThat(balance.get(0).getAmount()).isEqualByComparingTo("11000.00");

            List<TariffRevenue> revenue = movements(ctx, TariffRevenue.class);
            assertThat(revenue).hasSize(2);
            assertThat(revenue).allSatisfy(r -> {
                assertThat(r.getMovementType()).isEqualTo(MovementType.RECEIPT);
                assertThat(r.getClient()).isEqualTo(client);
            });
            assertThat(revenue).extracting(TariffRevenue::getTariff).containsExactly(monthly, annual);
            assertThat(revenue.get(0).getAmount()).isEqualByComparingTo("2000.00");
            assertThat(revenue.get(0).getPeriods()).isEqualByComparingTo("2");
            assertThat(revenue.get(1).getAmount()).isEqualByComparingTo("9000.00");
        }

        @Test
        @Req("R5")
        @DisplayName("[R5] Отменённая подписка не создаёт никаких движений")
        void cancelledSubscriptionHasNoMovements() {
            Subscription s = subscription(billing.client("100000"), line(monthly, 1));
            s.setStatus(SubscriptionStatus.CANCELLED);
            s.recalculate(billing);

            PostingContext ctx = context();
            s.handlePosting(ctx);

            assertThat(ctx.touchedRepositories()).allSatisfy(r -> assertThat(r.getPendingMovements()).isEmpty());
        }

        @Test
        @Req("R1")
        @DisplayName("[R1] Платёж: приход на лицевой счёт клиента")
        void paymentMovement() {
            Ref<Client> client = billing.client("0");
            Payment p = new Payment();
            p.setClient(client);
            p.setAmount(new BigDecimal("500.00"));

            PostingContext ctx = context();
            p.handlePosting(ctx);

            List<AccountBalance> balance = movements(ctx, AccountBalance.class);
            assertThat(balance).hasSize(1);
            assertThat(balance.get(0).getMovementType()).isEqualTo(MovementType.RECEIPT);
            assertThat(balance.get(0).getClient()).isEqualTo(client);
            assertThat(balance.get(0).getAmount()).isEqualByComparingTo("500.00");
        }

        @Test
        @Req("R1")
        @DisplayName("[R1] Платёж: клиент обязателен, сумма > 0")
        void paymentRules() {
            Payment p = new Payment();
            p.setAmount(BigDecimal.ZERO);
            assertThat(failedRules(p.rules())).hasSize(2);

            p.setClient(billing.client("0"));
            p.setAmount(new BigDecimal("1"));
            assertThat(failedRules(p.rules())).isEmpty();
        }
    }
}
