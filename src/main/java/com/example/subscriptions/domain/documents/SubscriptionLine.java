package com.example.subscriptions.domain.documents;

import com.example.subscriptions.domain.catalogs.Tariff;
import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.Attribute;
import su.onno.model.TabularSectionRow;
import su.onno.types.Ref;

import java.math.BigDecimal;

/**
 * Строка подписки (табличная часть «lines»): тариф, купленный на N периодов.
 * {@code price}, {@code days} и {@code amount} вычисляет {@link Subscription#beforeWrite()} —
 * руками их не вводят.
 */
@Getter
@Setter
public class SubscriptionLine extends TabularSectionRow {

    @Attribute(displayName = "Тариф")
    private Ref<Tariff> tariff;

    @Attribute(displayName = "Периодов")
    private Integer periods = 1;

    /** Цена за период — снимок цены тарифа на момент оформления. */
    @Attribute(displayName = "Цена", precision = 15, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /** Срок строки в днях = длительность периода тарифа × число периодов. */
    @Attribute(displayName = "Дней")
    private Integer days = 0;

    @Attribute(displayName = "Сумма", precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;
}
