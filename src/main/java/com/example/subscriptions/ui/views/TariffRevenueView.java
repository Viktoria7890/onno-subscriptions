package com.example.subscriptions.ui.views;

import com.example.subscriptions.domain.registers.TariffRevenue;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;

/** Подсказки отображения для отчёта по регистру оборотов «Выручка по тарифам». */
@Component
public class TariffRevenueView implements EntityView<TariffRevenue> {

    @Override
    public Class<TariffRevenue> entity() {
        return TariffRevenue.class;
    }

    @Override
    public void fields(EntityConfigBuilder<TariffRevenue> f) {
        f.icon("chart-column");
        f.field(TariffRevenue::getAmount).format("currency:RUB")
         .field(TariffRevenue::getPeriods).format("integer")
         .field(TariffRevenue::getPeriod).label("Период").format("dd.MM.yyyy HH:mm");
    }
}
