package com.restaurant.ui;

import com.restaurant.model.RevenueReset;
import com.restaurant.service.BillingService;
import com.restaurant.service.ReportsService;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.time.format.DateTimeFormatter;

/**
 * The ONE "Reset Revenue" flow, used by both the Reports screen and the Dashboard so they behave identically:
 * check what would be reset -> explain it in a confirmation dialog (Cancel is the default) -> reset -> success message.
 * Nothing is deleted: bills, payments, GST, discounts and order history stay in the database; only the revenue figures start again from zero.
 */
public final class RevenueResetAction {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private RevenueResetAction() {
    }

    /** Text for the "counted since ..." note under the revenue figures; empty when revenue was never reset. */
    public static String sinceText(RevenueReset last) {
        return last == null ? ""
                : "Revenue is counted from the reset on " + WHEN.format(last.resetAt())
                + ". Older bills are kept safely in the database.";
    }

    /** Short version for the Dashboard. */
    public static String sinceShort(RevenueReset last) {
        return last == null ? "" : "Revenue counted since " + DateTimeFormatter.ofPattern("dd MMM, hh:mm a").format(last.resetAt());
    }

    /**
     * @param onDone runs on the UI thread after a successful reset (the screen reloads its numbers)
     */
    public static void run(Component parent, ReportsService service, Runnable onDone) {
        Background.run(service::previewReset, preview -> {
            if (preview.billsCleared() == 0) {
                Messages.info(parent, "Nothing to reset", "Revenue is already at zero: there are no paid bills counted right now.\n"
                        + "New payments will be counted automatically.");
                return;
            }
            Object[] choices = {"Reset Revenue", "Cancel"};
            int answer = JOptionPane.showOptionDialog(parent,
                    "Reset the revenue and amount figures to zero?\n\n"
                            + "WILL BE RESET (shown as zero from now on):\n"
                            + "  - Total revenue, sales, average order value, GST collected, discounts given\n"
                            + "  - Daily and monthly sales, payment method split, best-selling items\n"
                            + "  - On the Dashboard and in Reports, at the same time\n"
                            + "  Right now this covers " + preview.billsCleared() + " paid bill" + (preview.billsCleared() == 1 ? "" : "s")
                            + " worth " + BillingService.money(preview.amountCleared()) + ".\n\n"
                            + "STAYS EXACTLY AS IT IS:\n"
                            + "  - Every bill, payment, GST and discount record (nothing is deleted)\n"
                            + "  - Receipts, the Billing 'Paid bills' list and Order History\n"
                            + "  - New payments after the reset are counted normally.",
                    "Reset Revenue?", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[1]);
            if (answer != 0) {
                return; // Cancel or closed: nothing changes
            }
            Background.run(service::resetRevenue, done -> {
                Messages.info(parent, "Revenue reset", "Revenue and amounts were reset to zero at " + WHEN.format(done.resetAt()) + ".\n"
                        + preview.billsCleared() + " bill" + (preview.billsCleared() == 1 ? "" : "s")
                        + " (" + BillingService.money(preview.amountCleared()) + ") are no longer counted, but are still saved.\n"
                        + "New payments start counting from now.");
                onDone.run();
            }, error -> Messages.error(parent, error));
        }, error -> Messages.error(parent, error));
    }
}
