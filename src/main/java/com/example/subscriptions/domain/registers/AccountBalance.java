package com.example.subscriptions.domain.registers;

import com.example.subscriptions.domain.catalogs.Client;
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
 * Лицевые счета — регистр <b>остатков</b>.
 * <ul>
 *   <li>Платёж — приход (receipt) на счёт клиента;</li>
 *   <li>Подписка — расход (expense) со счёта клиента.</li>
 * </ul>
 * Так как регистр BALANCE и {@code allowNegative = false} (по умолчанию), движок проведения
 * атомарно откажет в проведении, если остаток клиента уйдёт в минус. Это «последняя линия обороны»
 * правила «нельзя оформить подписку при нехватке денег» — она работает даже при гонке двух
 * одновременных проведений. Дружелюбная проверка с понятным сообщением — в
 * {@link com.example.subscriptions.domain.documents.Subscription#beforePost()}.
 */
@AccumulationRegister(name = "AccountBalance", title = "Лицевые счета",
        type = AccumulationType.BALANCE, context = "Billing")
@AccessControl(readRoles = {"MANAGER"})
@Getter
@Setter
public class AccountBalance extends AccumulationRecord {

    @Dimension(displayName = "Клиент")
    private Ref<Client> client;

    @Resource(displayName = "Сумма", precision = 15, scale = 2)
    private BigDecimal amount;
}
