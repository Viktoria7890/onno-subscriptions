package com.example.subscriptions.domain.enums;

import su.onno.annotations.EnumLabel;
import su.onno.annotations.Enumeration;

/** Способ оплаты (пополнения лицевого счёта). */
@Enumeration(name = "PaymentMethods", title = "Способ оплаты")
public enum PaymentMethod {

    @EnumLabel("Банковская карта") CARD,

    @EnumLabel("СБП") SBP,

    @EnumLabel("Банковский перевод") BANK_TRANSFER,

    @EnumLabel("Наличные") CASH
}
