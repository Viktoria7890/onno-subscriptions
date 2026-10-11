package com.example.subscriptions.ui.layout;

import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.catalogs.Tariff;
import com.example.subscriptions.domain.documents.Payment;
import com.example.subscriptions.domain.documents.Subscription;
import com.example.subscriptions.domain.registers.AccountBalance;
import com.example.subscriptions.domain.registers.TariffRevenue;
import org.springframework.stereotype.Component;
import su.onno.ui.Layout;
import su.onno.ui.LayoutSpec;
import su.onno.ui.NavStyle;

/** Оболочка приложения и боковое меню. Навигация курируется: в меню только то, что перечислено. */
@Component
public class MainLayout implements Layout {

    @Override
    public void configure(LayoutSpec layout) {
        layout.shell()
                .nav(NavStyle.SIDEBAR)
                .brand("Подписки")
                .light(c -> c.primary("#2563EB").primarySoft("#EFF6FF"))
                .dark(c -> c.primary("#3B82F6").primarySoft("#172554"));

        // Дашборд живёт на маршруте «/» — оболочка сама показывает его как домашнюю ссылку,
        // отдельный пункт меню продублировал бы её.

        layout.section("Продажи")
                .order(1)
                .icon("calendar-check")
                .document(Subscription.class)
                .document(Payment.class);

        layout.section("Справочники")
                .order(2)
                .icon("book-open")
                .catalog(Client.class)
                .catalog(Tariff.class);

        layout.section("Отчёты")
                .order(3)
                .icon("chart-column")
                .register(AccountBalance.class)
                .register(TariffRevenue.class);
    }
}
