package com.example.subscriptions.domain.documents;

import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.enums.PaymentMethod;
import com.example.subscriptions.domain.registers.AccountBalance;
import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Document;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.lifecycle.Postable;
import su.onno.model.DocumentObject;
import su.onno.posting.PostingContext;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Платёж — пополнение лицевого счёта клиента. При проведении делает приход в регистр
 * {@link AccountBalance}.
 */
@Document(name = "Payments", title = "Платежи", numberPrefix = "PAY-", context = "Billing")
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"MANAGER"})
@Getter
@Setter
public class Payment extends DocumentObject implements OnFillingHandler, Validated, Postable {

    @Attribute(displayName = "Клиент", required = true)
    private Ref<Client> client;

    @Attribute(displayName = "Сумма", precision = 15, scale = 2, required = true)
    private BigDecimal amount = BigDecimal.ZERO;

    @Attribute(displayName = "Способ оплаты", required = true)
    private PaymentMethod method = PaymentMethod.CARD;

    @Attribute(displayName = "Комментарий", length = 1000)
    private String comment;

    @Override
    public void onFilling() {
        if (getDate() == null) {
            setDate(LocalDateTime.now());
        }
        if (method == null) {
            method = PaymentMethod.CARD;
        }
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                BusinessRule.onField("client", "Укажите клиента", () -> client != null),
                BusinessRule.onField("amount", "Сумма платежа должна быть больше нуля",
                        () -> amount != null && amount.signum() > 0),
                BusinessRule.onField("method", "Укажите способ оплаты", () -> method != null));
    }

    @Override
    public void handlePosting(PostingContext context) {
        context.movements(AccountBalance.class).addReceipt(r -> {
            r.setClient(client);
            r.setAmount(amount);
        });
    }
}
