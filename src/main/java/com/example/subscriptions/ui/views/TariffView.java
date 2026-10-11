package com.example.subscriptions.ui.views;

import com.example.subscriptions.domain.catalogs.Tariff;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TariffView implements EntityView<Tariff> {

    @Override
    public Class<Tariff> entity() {
        return Tariff.class;
    }

    @Override
    public void list(ListSpec<Tariff> list) {
        list.columns(Tariff::getCode, Tariff::getDescription, Tariff::getPrice,
                        Tariff::getPeriodDays, Tariff::getAvailable)
                .label(Tariff::getCode, "Код")
                .label(Tariff::getDescription, "Тариф")
                .sortBy(Tariff::getPrice);
        Map<String, String> availability = new LinkedHashMap<>();
        availability.put("true", "Доступен");
        availability.put("false", "Недоступен");
        list.filter(Tariff::getAvailable).label("Подключение").options(availability);
        list.rowStyle(row -> row.bool("available") ? null : ListSpec.RowStyle.MUTED);
    }

    @Override
    public void fields(EntityConfigBuilder<Tariff> f) {
        f.icon("badge-percent");
        f.field(Tariff::getDescription).label("Наименование").order(0).width("full")
                .placeholder("Например, «Стандарт»")
         .field(Tariff::getCode).label("Код").order(1).width("half").hint("Присваивается автоматически.")
         .field(Tariff::getAvailable).order(2).width("half").widget("switch")
                .hint("Снятый с продажи тариф нельзя выбрать в новой подписке.")
         .field(Tariff::getPrice).order(3).width("half").format("currency:RUB")
                .hint("Стоимость одного периода.")
         .field(Tariff::getPeriodDays).order(4).width("half").format("integer")
                .hint("Сколько дней длится один период (30 — месяц, 365 — год).");
    }
}
