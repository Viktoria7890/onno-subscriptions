package com.example.subscriptions.ui.pages;

import com.example.subscriptions.domain.documents.Payment;
import com.example.subscriptions.domain.documents.Subscription;
import com.example.subscriptions.domain.registers.AccountBalance;
import com.example.subscriptions.domain.registers.TariffRevenue;
import org.springframework.stereotype.Component;
import su.onno.ui.Page;
import su.onno.ui.PageBuilder;

/**
 * Главная страница: показатели за выбранный период, диаграммы и последние документы.
 * Все агрегаты считает сервер по документам и регистрам.
 */
@Component
public class DashboardPage implements Page {

    @Override
    public String route() {
        return "/";
    }

    @Override
    public void compose(PageBuilder b) {
        b.title("Дашборд");
        b.subtitle("Подписки, платежи и остатки лицевых счетов");

        b.widget("Период").type("timeRange").width("full").order(-10)
                .config("presets", "7d,30d,90d,1y,all")
                .config("default", "30d");

        // ---- KPI за период --------------------------------------------------------------------
        b.widget("Поступило платежей").type("stat").width("1/3").order(0).document(Payment.class)
                .dateField(Payment::getDate)
                .config("metric", "sum").metricField(Payment::getAmount)
                .config("currency", "RUB").config("locale", "ru-RU")
                .config("trend", "false").config("comparison", "true")
                .config("comparisonLabel", "к прошлому периоду")
                .config("filter", "_posted = true")
                .hint("Сумма проведённых платежей за период.");

        b.widget("Продано подписок").type("stat").width("1/3").order(1).document(Subscription.class)
                .dateField(Subscription::getDate)
                .config("metric", "sum").metricField(Subscription::getTotal)
                .config("currency", "RUB").config("locale", "ru-RU")
                .config("trend", "false").config("comparison", "true")
                .config("comparisonLabel", "к прошлому периоду")
                .config("filter", "_posted = true AND cancelled = false")
                .hint("Сумма проведённых и не отменённых подписок за период.");

        b.widget("Оформлено подписок").type("stat").width("1/3").order(2).document(Subscription.class)
                .dateField(Subscription::getDate)
                .config("metric", "count")
                .config("trend", "false").config("comparison", "true")
                .config("comparisonLabel", "к прошлому периоду")
                .config("filter", "_posted = true AND cancelled = false");

        // ---- Итоги по регистрам (за всё время) ----------------------------------------------
        b.widget("Остаток на лицевых счетах").type("count").width("1/2").order(5)
                .register(AccountBalance.class)
                .config("metric", "sum").metricField(AccountBalance::getAmount)
                .config("currency", "RUB").config("locale", "ru-RU")
                .hint("Сумма остатков всех клиентов (регистр «Лицевые счета»).");

        b.widget("Признанная выручка").type("count").width("1/2").order(6)
                .register(TariffRevenue.class)
                .config("metric", "sum").metricField(TariffRevenue::getAmount)
                .config("currency", "RUB").config("locale", "ru-RU")
                .hint("Оборот регистра «Выручка по тарифам».");

        // ---- Диаграммы ------------------------------------------------------------------------
        b.widget("Подписки по статусам").type("chart").width("1/2").order(10).document(Subscription.class)
                .config("kind", "pie").config("groupBy", "statusDisplay")
                .config("metric", "count");

        b.widget("Платежи по дням").type("chart").width("1/2").order(11).document(Payment.class)
                .config("kind", "area")
                .groupBy(Payment::getDate).config("groupByDate", "day")
                .config("metric", "sum").metricField(Payment::getAmount)
                .config("filter", "_posted = true");

        // ---- Последние документы ----------------------------------------------------------------
        b.widget("Последние подписки").type("list").width("1/2").order(20).document(Subscription.class)
                .maxItems(8)
                .config("titleTemplate", "{number} · {clientDisplay}")
                .config("secondaryField", "statusDisplay");

        b.widget("Последние платежи").type("list").width("1/2").order(21).document(Payment.class)
                .maxItems(8)
                .config("titleTemplate", "{number} · {clientDisplay}")
                .config("secondaryField", "methodDisplay");
    }
}
