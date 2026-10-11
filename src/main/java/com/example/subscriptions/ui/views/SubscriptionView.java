package com.example.subscriptions.ui.views;

import com.example.subscriptions.domain.catalogs.Client;
import com.example.subscriptions.domain.catalogs.Tariff;
import com.example.subscriptions.domain.documents.Subscription;
import com.example.subscriptions.domain.documents.SubscriptionLine;
import com.example.subscriptions.domain.enums.SubscriptionStatus;
import com.example.subscriptions.service.SubscriptionCancellationService;
import com.example.subscriptions.service.SubscriptionCancellationService.CancellationRejected;
import com.example.subscriptions.service.SubscriptionStatusService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import su.onno.ui.ActionContext;
import su.onno.ui.ActionDialog;
import su.onno.ui.ActionPresentation;
import su.onno.ui.ActionRejectedException;
import su.onno.ui.ActionResult;
import su.onno.ui.ActionScope;
import su.onno.ui.ActionSeverity;
import su.onno.ui.ActionSpec;
import su.onno.ui.ActionToast;
import su.onno.ui.DialogSize;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.InputType;
import su.onno.ui.ListSpec;

/**
 * Подписки: список со статусами-«пилюлями», форма с табличной частью и действия
 * «Отменить» (модальное окно с причиной) и «Обновить статусы».
 */
@Component
public class SubscriptionView implements EntityView<Subscription> {

    private final SubscriptionCancellationService cancellation;
    private final SubscriptionStatusService statuses;

    public SubscriptionView(SubscriptionCancellationService cancellation, SubscriptionStatusService statuses) {
        this.cancellation = cancellation;
        this.statuses = statuses;
    }

    @Override
    public Class<Subscription> entity() {
        return Subscription.class;
    }

    @Override
    public void list(ListSpec<Subscription> list) {
        // Колонку «Дата» (дата документа) не показываем: с ней таблица не помещается по ширине и
        // кнопка «Отменить подписку» в конце строки уезжает за край экрана.
        list.columns(Subscription::getNumber, Subscription::getClient,
                        Subscription::getStatus, Subscription::getStartDate, Subscription::getEndDate,
                        Subscription::getTotal)
                .column("posted", "Проведена")
                .label(Subscription::getNumber, "Номер")
                .sortBy(Subscription::getDate, true)
                .groupable(Subscription::getStatus, Subscription::getClient, Subscription::getStartDate)
                .aggregate(Subscription::getTotal, ListSpec.Agg.SUM, "Итого");
        list.filter(Subscription::getStatus).label("Статус").multiOptions();
        list.filter(Subscription::getStartDate).label("Дата начала").dateRange();
        list.rowStyle(row -> {
            SubscriptionStatus status = row.enumValue("status", SubscriptionStatus.class);
            if (status == SubscriptionStatus.CANCELLED) {
                return ListSpec.RowStyle.MUTED;
            }
            if (status == SubscriptionStatus.EXPIRED) {
                return ListSpec.RowStyle.WARNING;
            }
            return null;
        });
    }

    @Override
    public void fields(EntityConfigBuilder<Subscription> f) {
        f.icon("calendar-check");

        f.field(Subscription::getNumber).label("Номер").order(0).width("half")
         .field(Subscription::getDate).label("Дата").order(1).width("half").format("dd.MM.yyyy HH:mm")
         .field("posted").label("Проведена")
         .field(Subscription::getClient).order(2).width("half")
                .hint("Со счёта этого клиента спишется стоимость при проведении.")
         .field(Subscription::getStatus).order(3).width("half")
                .hint("«Активна» и «Истекла» выставляются автоматически по датам; для отмены — кнопка «Отменить».")
         .field(Subscription::getStartDate).order(4).width("half").format("dd.MM.yyyy").group("Срок действия")
         .field(Subscription::getEndDate).order(5).width("half").format("dd.MM.yyyy").group("Срок действия")
                .hint("Считается автоматически: дата начала + максимальный срок среди строк.")
         .field(Subscription::getTotal).order(6).width("half").format("currency:RUB")
                .hint("Сумма строк; спишется с лицевого счёта при проведении.")
         .field(Subscription::getCancelReason).order(7).widget("textarea")
                .hint("Заполняется при отмене подписки.")
         .field(Subscription::getCancelled).hideInForm().hideInList().hideInDetail();

        f.refField(Subscription::getClient).refSecondary(Client::getEmail);

        // Табличная часть «lines».
        f.rowRefField(Subscription::getLines, SubscriptionLine::getTariff).label("Тариф")
                .refFilter("available = true")
                .refSecondary(Tariff::getCode);
        f.rowField(Subscription::getLines, SubscriptionLine::getPeriods).label("Периодов").format("integer");
        f.rowField(Subscription::getLines, SubscriptionLine::getPrice).label("Цена за период")
                .format("currency:RUB").hint("Подставляется из тарифа при сохранении.");
        f.rowField(Subscription::getLines, SubscriptionLine::getDays).label("Дней").format("integer")
                .hint("Длительность периода тарифа × число периодов.");
        f.rowField(Subscription::getLines, SubscriptionLine::getAmount).label("Сумма").format("currency:RUB");

        // «Отменить подписку» — основной путь; встроенное «Отменить проведение» у подписки убираем из
        // меню, чтобы два похожих пункта не путали пользователя (через REST оно по-прежнему доступно).
        f.action("cancelDetail").inMenu();
        f.action("unpost").hidden();
    }

    @Override
    public void actions(ActionSpec a) {
        a.action("cancel").scope(ActionScope.ROW).icon("ban").label("Отменить подписку")
                .visibleWhen(row -> SubscriptionCancellationService.canCancel(
                        row.enumValue("status", SubscriptionStatus.class)))
                .form(this::reasonForm)
                .handler(ctx -> cancel(ctx, false));

        a.action("cancelDetail").scope(ActionScope.DETAIL).icon("ban").label("Отменить подписку")
                .visibleWhen(row -> SubscriptionCancellationService.canCancel(
                        row.enumValue("status", SubscriptionStatus.class)))
                .form(this::reasonForm)
                .handler(ctx -> cancel(ctx, true));

        // Ручной запуск регламентного задания (безопасен: только пересчёт статусов по датам).
        a.action("refreshStatuses").scope(ActionScope.TOOLBAR).icon("refresh-cw").label("Обновить статусы")
                .handler(ctx -> {
                    int changed = statuses.refreshAll();
                    return ActionResult.refresh(ActionToast.success(
                            changed == 0 ? "Статусы актуальны" : "Обновлено подписок: " + changed));
                });
    }

    private void reasonForm(su.onno.ui.InputSpec form) {
        form.title("Отмена подписки")
                .description("Движения подписки будут сторнированы: деньги вернутся на лицевой счёт, "
                        + "выручка перестанет учитываться.")
                .submitLabel("Отменить подписку")
                .cancelLabel("Назад")
                .icon("ban")
                .tone(ActionSeverity.WARNING)
                .size(DialogSize.MD);
        form.input("reason").label("Причина отмены").type(InputType.TEXTAREA)
                .placeholder("Например: клиент передумал").required();
    }

    private ActionResult cancel(ActionContext ctx, boolean fromForm) {
        try {
            Subscription cancelled = cancellation.cancel(ctx.id(), ctx.input("reason"));
            // Проведённая подписка сторнируется — её итог возвращается на лицевой счёт.
            BigDecimal refunded = cancelled.isPosted() && cancelled.getTotal() != null
                    ? cancelled.getTotal() : BigDecimal.ZERO;
            ActionDialog dialog = ActionDialog.success("Подписка " + cancelled.getNumber() + " отменена")
                    .message(refunded.signum() > 0
                            ? "На лицевой счёт клиента возвращено " + money(refunded) + ", выручка сторнирована."
                            : "Подписка не была проведена — движений по счёту не было.")
                    .detail("Причина: " + cancelled.getCancelReason());
            if (fromForm) {
                // Открытая форма не перечитывает запись сама — подскажем обновить страницу.
                dialog = dialog.detail("Обновите страницу (⌘R), чтобы увидеть новый статус на форме.");
            }
            return ActionResult.refresh(dialog);
        } catch (CancellationRejected e) {
            throw ActionRejectedException.builder()
                    .title("Нельзя отменить подписку")
                    .message(e.getMessage())
                    .presentation(ActionPresentation.INLINE)
                    .formError(e.getMessage())
                    .build();
        }
    }

    private static String money(BigDecimal value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru-RU"));
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.00", symbols).format(value) + " ₽";
    }
}
