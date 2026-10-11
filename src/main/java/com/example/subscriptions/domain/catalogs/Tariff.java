package com.example.subscriptions.domain.catalogs;

import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Catalog;
import su.onno.model.CatalogObject;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;

import java.math.BigDecimal;
import java.util.List;

/**
 * Тарифный план — справочник. Наименование — {@code description}.
 * Тариф продаётся периодами: цена указана за один период длительностью {@link #periodDays} дней.
 */
@Catalog(name = "Tariffs", title = "Тарифы", codePrefix = "T-", context = "Billing")
// Цены и доступность тарифов меняет только администратор; менеджер видит тарифы и выбирает их
// в подписках, но редактировать не может.
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"ADMIN"})
@Getter
@Setter
public class Tariff extends CatalogObject implements Validated {

    @Attribute(displayName = "Цена за период", precision = 15, scale = 2, required = true, min = 0)
    private BigDecimal price = BigDecimal.ZERO;

    @Attribute(displayName = "Длительность периода, дней", required = true, min = 1)
    private Integer periodDays = 30;

    @Attribute(displayName = "Доступен для подключения")
    private Boolean available = Boolean.TRUE;

    /** Удобный null-safe геттер для бизнес-логики. */
    public boolean canBeConnected() {
        return Boolean.TRUE.equals(available);
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                BusinessRule.onField("description", "Укажите наименование тарифа",
                        () -> getDescription() != null && !getDescription().isBlank()),
                BusinessRule.onField("price", "Цена за период не может быть отрицательной",
                        () -> price != null && price.signum() >= 0),
                BusinessRule.onField("periodDays", "Длительность периода должна быть больше нуля",
                        () -> periodDays != null && periodDays > 0));
    }
}
