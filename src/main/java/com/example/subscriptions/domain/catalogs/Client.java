package com.example.subscriptions.domain.catalogs;

import com.example.subscriptions.domain.enums.ClientStatus;
import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Catalog;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.model.CatalogObject;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;

import java.time.LocalDate;
import java.util.List;

/**
 * Клиент — справочник. Наименование клиента хранится в унаследованном {@code description}.
 * Лицевой счёт клиента — это не отдельная сущность, а срез регистра остатков
 * {@link com.example.subscriptions.domain.registers.AccountBalance} по измерению «Клиент».
 */
@Catalog(name = "Clients", title = "Клиенты", codePrefix = "CL-", context = "Billing")
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"MANAGER"})
@Getter
@Setter
public class Client extends CatalogObject implements OnFillingHandler, Validated {

    @Attribute(displayName = "Статус")
    private ClientStatus status = ClientStatus.ACTIVE;

    @Attribute(displayName = "E-mail", length = 200, email = true)
    private String email;

    @Attribute(displayName = "Телефон", length = 50)
    private String phone;

    /** Инициализатор нужен, чтобы дата была видна уже на пустой форме «Создать». */
    @Attribute(displayName = "Дата регистрации")
    private LocalDate registrationDate = LocalDate.now();

    @Override
    public void onFilling() {
        if (status == null) {
            status = ClientStatus.ACTIVE;
        }
        if (registrationDate == null) {
            registrationDate = LocalDate.now();
        }
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(BusinessRule.onField("description", "Укажите наименование клиента",
                () -> getDescription() != null && !getDescription().isBlank()));
    }
}
