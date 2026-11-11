import com.restaurant.dao.*;
import com.restaurant.exception.*;
import com.restaurant.model.*;
import com.restaurant.service.*;
import java.math.BigDecimal;
import java.util.*;

/** Phase 3 integration test: real services + DAOs + Connector/J + database. Phase A writes, phase B (new JVM) checks persistence. */
public class OrderItTest {
    static int pass, fail;
    static void check(String n, boolean ok) { if (ok) pass++; else fail++; System.out.println((ok ? "PASS  " : "FAIL  ") + n); }
    static com.restaurant.model.MenuItem dish(MenuService m, String name) throws Exception {
        return m.getAllItems().stream().filter(x -> x.getName().equals(name)).findFirst().orElseThrow();
    }
    static TableOverview tbl(TableService s, int n) throws Exception { return s.getTables().stream().filter(t -> t.getNumber() == n).findFirst().orElseThrow(); }
    static OrderSummary order(OrderService o, int id) throws Exception { return o.getActiveOrders().stream().filter(x -> x.getOrderId() == id).findFirst().orElse(null); }

    public static void main(String[] a) throws Exception {
        MenuService menu = new MenuService(new MenuDaoImpl());
        TableService tables = new TableService(new TableDaoImpl());
        OrderService orders = new OrderService(new OrderDaoImpl());
        if (a[0].equals("A")) phaseA(menu, tables, orders); else phaseB(menu, tables, orders);
        System.out.println("RESULT " + a[0] + ": " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void phaseA(MenuService menu, TableService tables, OrderService orders) throws Exception {
        check("no active orders at start", orders.getActiveOrders().isEmpty());
        int id = tables.assignTable(tbl(tables, 3).getId(), "Anita Rao", "9876543210");
        List<OrderSummary> act = orders.getActiveOrders();
        check("assign creates exactly ONE active order, PENDING, for table 3, customer shown",
                act.size() == 1 && act.get(0).getOrderId() == id && act.get(0).getStatus() == OrderStatus.PENDING
                        && act.get(0).getTableNumber() == 3 && "Anita Rao".equals(act.get(0).getCustomerName()));
        check("new order: 0 items, total 0", act.get(0).getItemCount() == 0 && act.get(0).getTotal().signum() == 0);

        // empty order cannot go to the kitchen
        try { orders.changeStatus(order(orders, id), OrderStatus.PREPARING); check("empty order refused", false); }
        catch (InvalidInputException e) { check("empty order cannot be sent to kitchen: " + e.getMessage(), true); }
        check("empty order still PENDING", order(orders, id).getStatus() == OrderStatus.PENDING);

        var chicken = dish(menu, "Butter Chicken"); // 380.00
        var other = menu.getAllItems().stream().filter(x -> !x.getName().equals("Butter Chicken") && x.isAvailable()).findFirst().orElseThrow();
        orders.addItem(id, chicken, 2);
        orders.addItem(id, other, 1);
        orders.addItem(id, chicken, 1); // same dish again -> merged into one line
        List<OrderLine> lines = orders.getLines(id);
        check("two different dishes -> 2 lines (same dish merged)", lines.size() == 2);
        OrderLine cl = lines.stream().filter(l -> l.getName().equals("Butter Chicken")).findFirst().orElseThrow();
        check("Butter Chicken quantity 3, price 380.00, subtotal 1140.00", cl.getQuantity() == 3 && cl.getUnitPrice().compareTo(new BigDecimal("380")) == 0 && cl.getSubtotal().compareTo(new BigDecimal("1140")) == 0);
        BigDecimal expected = new BigDecimal("1140").add(other.getPrice());
        check("order total = sum of subtotals", orders.totalOf(lines).compareTo(expected) == 0);
        OrderSummary s = order(orders, id);
        check("active list shows 4 items and DB total equals calculated total", s.getItemCount() == 4 && s.getTotal().compareTo(expected) == 0);
        check("still exactly one order for the table (no duplicate)", orders.getActiveOrders().size() == 1);

        orders.increaseQuantity(id, cl.getItemId());
        check("+ : quantity 4", orders.getLines(id).stream().filter(l -> l.getItemId() == cl.getItemId()).findFirst().orElseThrow().getQuantity() == 4);
        orders.decreaseQuantity(id, cl.getItemId());
        orders.decreaseQuantity(id, cl.getItemId());
        check("- twice: quantity 2", orders.getLines(id).stream().filter(l -> l.getItemId() == cl.getItemId()).findFirst().orElseThrow().getQuantity() == 2);
        orders.decreaseQuantity(id, cl.getItemId());
        try { orders.decreaseQuantity(id, cl.getItemId()); check("quantity below 1 refused", false); }
        catch (InvalidInputException e) { check("quantity cannot drop below 1: " + e.getMessage(), orders.getLines(id).stream().anyMatch(l -> l.getItemId() == cl.getItemId() && l.getQuantity() == 1)); }
        try { orders.addItem(id, chicken, 0); check("zero quantity refused", false); } catch (InvalidInputException e) { check("zero quantity refused: " + e.getMessage(), true); }
        try { orders.addItem(id, chicken, -3); check("negative quantity refused", false); } catch (InvalidInputException e) { check("negative quantity refused", true); }
        try { orders.addItem(id, chicken, 100); check("100 refused", false); } catch (InvalidInputException e) { check("quantity over 99 refused", true); }

        // unavailable dish
        menu.updateItem(other.getId(), other.getName(), other.getCategory(), other.getPrice().toPlainString(), false);
        var stale = other; // screen still holds the old 'available' object: the DAO must re-check in the transaction
        try { orders.addItem(id, stale, 1); check("dish made unavailable meanwhile refused", false); }
        catch (InvalidInputException e) { check("dish made unavailable meanwhile is refused by the database check: " + e.getMessage(), true); }
        var unav = dish(menu, other.getName());
        try { orders.addItem(id, unav, 1); check("unavailable refused (service)", false); } catch (InvalidInputException e) { check("unavailable dish refused by service", true); }
        menu.updateItem(other.getId(), other.getName(), other.getCategory(), other.getPrice().toPlainString(), true);

        // price change does not alter existing line
        menu.updateItem(chicken.getId(), chicken.getName(), chicken.getCategory(), "999", true);
        check("menu price change does not change the order line", orders.getLines(id).stream().filter(l -> l.getName().equals("Butter Chicken")).findFirst().orElseThrow().getUnitPrice().compareTo(new BigDecimal("380")) == 0);
        menu.updateItem(chicken.getId(), chicken.getName(), chicken.getCategory(), "380", true);

        // remove
        orders.removeItem(id, lines.stream().filter(l -> l.getName().equals(other.getName())).findFirst().orElseThrow().getItemId());
        check("remove: only Butter Chicken left", orders.getLines(id).size() == 1);
        try { orders.removeItem(id, 999999); check("remove missing refused", false); } catch (InvalidInputException e) { check("removing an item that is not there is refused", true); }
        orders.addItem(id, other, 2);

        // status flow
        orders.changeStatus(order(orders, id), OrderStatus.PREPARING);
        check("PENDING -> PREPARING", order(orders, id).getStatus() == OrderStatus.PREPARING);
        try { orders.changeStatus(order(orders, id), OrderStatus.SERVED); check("skip refused", false); } catch (InvalidInputException e) { check("skipping READY refused", true); }
        orders.changeStatus(order(orders, id), OrderStatus.READY);
        check("PREPARING -> READY (stored in MySQL ENUM)", order(orders, id).getStatus() == OrderStatus.READY);
        check("READY order is still an open order on the Tables screen", tbl(tables, 3).getOpenOrderId() == id);
        orders.changeStatus(order(orders, id), OrderStatus.PREPARING);
        check("one step back READY -> PREPARING", order(orders, id).getStatus() == OrderStatus.PREPARING);
        orders.changeStatus(order(orders, id), OrderStatus.READY);
        orders.changeStatus(order(orders, id), OrderStatus.SERVED);
        check("READY -> SERVED", order(orders, id).getStatus() == OrderStatus.SERVED);
        try { orders.changeStatus(order(orders, id), null); check("past SERVED refused", false); } catch (InvalidInputException e) { check("nothing after SERVED on this screen", true); }

        // second table: stale status
        int id2 = tables.assignTable(tbl(tables, 4).getId(), "Vikram Shah", null);
        orders.addItem(id2, chicken, 1);
        OrderSummary stale2 = order(orders, id2);
        orders.changeStatus(stale2, OrderStatus.PREPARING);
        try { orders.changeStatus(stale2, OrderStatus.PREPARING); check("stale status change refused", false); }
        catch (InvalidInputException e) { check("someone else already changed it -> refused: " + e.getMessage(), true); }
        check("2 active orders, one per table", orders.getActiveOrders().size() == 2);

        // reuse, never duplicate
        int reuse = orders.openOrderForTable(tbl(tables, 4).getId(), "Vikram Shah");
        check("openOrderForTable on a table with an open order REUSES it", reuse == id2 && orders.getActiveOrders().size() == 2);
        check("no seated table without order", orders.getSeatedTablesWithoutOrder().isEmpty());

        // table without any open order (repair path)
        int t5 = tbl(tables, 5).getId();
        try { orders.openOrderForTable(t5, "Nobody"); check("free table refused", false); } catch (Exception e) { check("repair path refuses an AVAILABLE table: " + e.getMessage(), true); }
        check("still 2 orders after refusals", orders.getActiveOrders().size() == 2);

        // release cancels order
        tables.releaseTable(tbl(tables, 4).getId());
        check("release table 4: its order leaves the active list", order(orders, id2) == null && orders.getActiveOrders().size() == 1);
        try { orders.addItem(id2, chicken, 1); check("closed order refused", false); } catch (InvalidInputException e) { check("cannot add to a cancelled order: " + e.getMessage(), true); }
        try { orders.addItem(0, chicken, 1); check("no order refused", false); } catch (InvalidInputException e) { check("no order selected refused", true); }
        try { orders.addItem(id, null, 1); check("no dish refused", false); } catch (InvalidInputException e) { check("no dish selected refused", true); }

        // category save/load
        var item = menu.addItem("Phase3 Test Dish", "Desserts3", "55", true);
        check("new category saved and loaded from DB", dish(menu, "Phase3 Test Dish").getCategory().equals("Desserts3") && menu.usedCategories(menu.getAllItems()).contains("Desserts3"));
        menu.updateItem(item.getId(), "Phase3 Test Dish", "Sweets3", "55", true);
        check("category edit saved", dish(menu, "Phase3 Test Dish").getCategory().equals("Sweets3"));
    }

    static void phaseB(MenuService menu, TableService tables, OrderService orders) throws Exception {
        List<OrderSummary> act = orders.getActiveOrders();
        check("after restart: exactly 1 active order (table 3)", act.size() == 1 && act.get(0).getTableNumber() == 3);
        OrderSummary o = act.get(0);
        check("after restart: status SERVED persisted", o.getStatus() == OrderStatus.SERVED);
        List<OrderLine> lines = orders.getLines(o.getOrderId());
        check("after restart: 2 lines kept, total matches", lines.size() == 2 && o.getTotal().compareTo(orders.totalOf(lines)) == 0 && o.getItemCount() == 3);
        check("after restart: table 3 still occupied by this order", tbl(tables, 3).getOpenOrderId() == o.getOrderId());
        check("after restart: new category 'Sweets3' still there", dish(menu, "Phase3 Test Dish").getCategory().equals("Sweets3"));
        check("after restart: Butter Chicken price still 380.00", dish(menu, "Butter Chicken").getPrice().compareTo(new BigDecimal("380")) == 0);
    }
}
