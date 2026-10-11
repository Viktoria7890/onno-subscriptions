package com.example.subscriptions.domain.registers;

import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.catalogs.Tariff;
import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.AccessControl;
import su.onno.annotations.AccumulationRegister;
import su.onno.annotations.Dimension;
import su.onno.annotations.Resource;
import su.onno.model.AccumulationRecord;
import su.onno.model.AccumulationType;
import su.onno.types.Ref;

import java.math.BigDecimal;

/**
 * Выручка по тарифам — регистр <b>оборотов</b>. Проведённая (не отменённая) подписка пишет по одной
 * записи на каждую строку: тариф × клиент → сумма и число купленных периодов.
 */
@AccumulationRegister(name = "TariffRevenue", title = "Выручка по тарифам",
        type = AccumulationType.TURNOVER, context = "Billing")
@AccessControl(readRoles = {"MANAGER"})
@Getter
@Setter
public class TariffRevenue extends AccumulationRecord {

    @Dimension(displayName = "Тариф")
    private Ref<Tariff> tariff;

    @Dimension(displayName = "Клиент")
    private Ref<Client> client;

    @Resource(displayName = "Сумма", precision = 15, scale = 2)
    private BigDecimal amount;

    @Resource(displayName = "Периодов", precision = 10, scale = 0)
    private BigDecimal periods;
}
