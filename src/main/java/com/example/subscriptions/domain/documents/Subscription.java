package com.example.subscriptions.domain.documents;

import com.example.subscriptions.domain.BillingLookup;
import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.catalogs.Tariff;
import com.example.subscriptions.domain.enums.SubscriptionStatus;
import com.example.subscriptions.domain.registers.AccountBalance;
import com.example.subscriptions.domain.registers.TariffRevenue;
import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Document;
import su.onno.annotations.TabularSection;
import su.onno.lifecycle.BeforePostHandler;
import su.onno.lifecycle.BeforeWriteHandler;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.lifecycle.Postable;
import su.onno.model.DocumentObject;
import su.onno.posting.PostingContext;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;
import su.onno.types.Ref;
import su.onno.validation.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Подписка клиента на один или несколько тарифов.
 *
 * <p>Жизненный цикл (порядок вызовов задаёт onno):</p>
 * <ul>
 *   <li>{@link #onFilling()} — при создании: дата документа и дата начала = сегодня;</li>
 *   <li>{@link #beforeWrite()} — при <b>каждом</b> сохранении и перед проведением: цена строки из
 *       тарифа, сумма строки, срок строки в днях, итог документа и дата окончания
 *       (дата начала + <b>максимальный</b> срок среди строк);</li>
 *   <li>{@link #rules()} — бизнес-правила (проверяются перед записью и перед проведением);</li>
 *   <li>{@link #beforePost()} — перед проведением: хватает ли денег на лицевом счёте;</li>
 *   <li>{@link #handlePosting(PostingContext)} — движения: расход с лицевого счёта и выручка по
 *       тарифам. Отменённая подписка движений не создаёт.</li>
 * </ul>
 */
@Document(name = "Subscriptions", title = "Подписки", numberPrefix = "SUB-", context = "Billing")
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"MANAGER"})
@Getter
@Setter
public class Subscription extends DocumentObject
        implements OnFillingHandler, BeforeWriteHandler, Validated, BeforePostHandler, Postable {

    @Attribute(displayName = "Клиент", required = true)
    private Ref<Client> client;

    @Attribute(displayName = "Статус")
    private SubscriptionStatus status = SubscriptionStatus.NEW;

    /** Инициализатор — чтобы дата была видна уже на пустой форме «Создать». */
    @Attribute(displayName = "Дата начала", required = true)
    private LocalDate startDate = LocalDate.now();

    /** Вычисляется в {@link #beforeWrite()}: дата начала + максимальный срок среди строк. */
    @Attribute(displayName = "Дата окончания")
    private LocalDate endDate;

    @Attribute(displayName = "Итого", precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Attribute(displayName = "Причина отмены", length = 1000)
    private String cancelReason;

    /**
     * Производный флаг «отменена» — обычная boolean-колонка, чтобы KPI на дашборде могли фильтровать
     * выражением {@code cancelled = false} (фильтр виджетов не умеет предикаты по enum).
     */
    @Attribute(displayName = "Отменена")
    private Boolean cancelled = Boolean.FALSE;

    @TabularSection(name = "lines")
    private List<SubscriptionLine> lines = new ArrayList<>();

    // ---------------------------------------------------------------------------------------------
    // Автоподстановка
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onFilling() {
        // onFilling вызывается при каждом сохранении нового объекта — только null-guard'ы,
        // чтобы не затирать значения, заданные формой, импортом или тестом.
        if (getDate() == null) {
            setDate(LocalDateTime.now());
        }
        if (startDate == null) {
            startDate = getDate().toLocalDate();
        }
        if (status == null) {
            status = SubscriptionStatus.NEW;
        }
    }

    @Override
    public void beforeWrite() {
        recalculate(BillingLookup.current());
    }

    /**
     * Пересчёт производных полей. Цена и длительность берутся из тарифа только пока документ не
     * проведён: у проведённой подписки цена «заморожена» (это снимок на момент оформления), и
     * повторное сохранение (например, сменой статуса регламентным заданием) не должно менять суммы,
     * под которые уже записаны движения.
     */
    void recalculate(BillingLookup lookup) {
        BigDecimal sum = BigDecimal.ZERO;
        int maxDays = 0;
        for (SubscriptionLine line : safeLines()) {
            int periods = line.getPeriods() == null ? 0 : line.getPeriods();
            if (!isPosted()) {
                Optional<Tariff> tariff = line.getTariff() == null
                        ? Optional.empty()
                        : lookup.tariff(line.getTariff());
                if (tariff.isPresent()) {
                    Tariff t = tariff.get();
                    line.setPrice(t.getPrice() == null ? BigDecimal.ZERO : t.getPrice());
                    int periodDays = t.getPeriodDays() == null ? 0 : t.getPeriodDays();
                    line.setDays(periodDays * Math.max(periods, 0));
                }
            }
            BigDecimal price = line.getPrice() == null ? BigDecimal.ZERO : line.getPrice();
            BigDecimal amount = price.multiply(BigDecimal.valueOf(periods)).setScale(2, RoundingMode.HALF_UP);
            line.setAmount(amount);
            sum = sum.add(amount);
            if (line.getDays() != null) {
                maxDays = Math.max(maxDays, line.getDays());
            }
        }
        total = sum.setScale(2, RoundingMode.HALF_UP);
        endDate = startDate == null ? null : startDate.plusDays(maxDays);
        cancelled = status == SubscriptionStatus.CANCELLED;
    }

    // ---------------------------------------------------------------------------------------------
    // Бизнес-правила
    // ---------------------------------------------------------------------------------------------

    @Override
    public List<BusinessRule> rules() {
        return SubscriptionRules.rulesFor(this, BillingLookup.current());
    }

    // ---------------------------------------------------------------------------------------------
    // Проведение
    // ---------------------------------------------------------------------------------------------

    /**
     * Проверка средств перед проведением — с понятным сообщением. Окончательную гарантию даёт
     * регистр остатков {@link AccountBalance} (BALANCE не уходит в минус атомарно).
     */
    @Override
    public void beforePost() {
        ensureEnoughMoney(BillingLookup.current());
    }

    void ensureEnoughMoney(BillingLookup lookup) {
        if (status == SubscriptionStatus.CANCELLED || client == null) {
            return; // отменённая подписка ничего не списывает
        }
        BigDecimal required = total == null ? BigDecimal.ZERO : total;
        BigDecimal available = lookup.balance(client);
        if (isPosted() && getId() != null) {
            // Перепроведение: собственное прежнее списание будет сторнировано движком.
            available = available.add(lookup.chargedBy(getId()));
        }
        if (available.compareTo(required) < 0) {
            throw new ValidationException(
                    "Недостаточно средств на лицевом счёте: доступно " + money(available)
                            + ", требуется " + money(required) + ". Пополните счёт платежом.");
        }
    }

    @Override
    public void handlePosting(PostingContext context) {
        if (status == SubscriptionStatus.CANCELLED) {
            return; // отменённая подписка не создаёт движений
        }
        context.movements(AccountBalance.class).addExpense(r -> {
            r.setClient(client);
            r.setAmount(total);
        });
        var revenue = context.movements(TariffRevenue.class);
        for (SubscriptionLine line : safeLines()) {
            revenue.addReceipt(r -> {
                r.setTariff(line.getTariff());
                r.setClient(client);
                r.setAmount(line.getAmount());
                r.setPeriods(BigDecimal.valueOf(line.getPeriods() == null ? 0 : line.getPeriods()));
            });
        }
    }

    List<SubscriptionLine> safeLines() {
        return lines == null ? List.of() : lines;
    }

    static String money(BigDecimal value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru-RU"));
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.00", symbols).format(value) + " ₽";
    }
}
