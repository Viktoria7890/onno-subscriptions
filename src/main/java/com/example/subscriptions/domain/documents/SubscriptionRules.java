package com.example.subscriptions.domain.documents;

import com.example.subscriptions.domain.BillingLookup;
import com.example.subscriptions.domain.catalogs.Tariff;
import com.example.subscriptions.domain.enums.SubscriptionStatus;
import su.onno.rules.BusinessRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Бизнес-правила подписки, вынесенные в отдельную фабрику, чтобы их было удобно читать и
 * тестировать по одному. Имена правил стабильны — по ним тесты проверяют, какое правило сработало.
 */
final class SubscriptionRules {

    static final String CLIENT_REQUIRED = "client-required";
    static final String START_DATE_REQUIRED = "start-date-required";
    static final String LINES_REQUIRED = "lines-required";
    static final String TARIFF_REQUIRED = "tariff-required";
    static final String PERIODS_POSITIVE = "periods-positive";
    static final String TARIFF_AVAILABLE = "tariff-available";

    private SubscriptionRules() {
    }

    static List<BusinessRule> rulesFor(Subscription s, BillingLookup lookup) {
        List<SubscriptionLine> lines = s.safeLines();

        List<Integer> withoutTariff = new ArrayList<>();
        List<Integer> badPeriods = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            SubscriptionLine line = lines.get(i);
            int lineNo = i + 1;
            if (line.getTariff() == null) {
                withoutTariff.add(lineNo);
            } else if (checksAvailability(s)) {
                Optional<Tariff> tariff = lookup.tariff(line.getTariff());
                if (tariff.isEmpty() || !tariff.get().canBeConnected()) {
                    unavailable.add(tariff.map(Tariff::getDescription)
                            .filter(name -> name != null && !name.isBlank())
                            .map(name -> "«" + name + "»")
                            .orElse("в строке " + lineNo));
                }
            }
            if (line.getPeriods() == null || line.getPeriods() <= 0) {
                badPeriods.add(lineNo);
            }
        }

        return List.of(
                new BusinessRule(CLIENT_REQUIRED, "client", "Укажите клиента",
                        () -> s.getClient() != null),
                new BusinessRule(START_DATE_REQUIRED, "startDate", "Укажите дату начала",
                        () -> s.getStartDate() != null),
                new BusinessRule(LINES_REQUIRED, null, "Добавьте хотя бы одну строку с тарифом",
                        () -> !lines.isEmpty()),
                new BusinessRule(TARIFF_REQUIRED, null,
                        "Выберите тариф в строках: " + join(withoutTariff),
                        withoutTariff::isEmpty),
                new BusinessRule(PERIODS_POSITIVE, null,
                        "Число периодов должно быть больше нуля (строки: " + join(badPeriods) + ")",
                        badPeriods::isEmpty),
                new BusinessRule(TARIFF_AVAILABLE, null,
                        "Тариф недоступен для подключения: " + String.join(", ", unavailable),
                        unavailable::isEmpty));
    }

    /**
     * Доступность тарифа проверяется только в момент оформления. Уже проведённую подписку
     * (например, при смене статуса регламентным заданием) не должно «ломать» то, что тариф позже
     * сняли с продажи; отменённой подписке тариф тоже не нужен.
     */
    private static boolean checksAvailability(Subscription s) {
        return !s.isPosted() && s.getStatus() != SubscriptionStatus.CANCELLED;
    }

    private static String join(List<Integer> numbers) {
        return String.join(", ", numbers.stream().map(String::valueOf).toList());
    }
}
