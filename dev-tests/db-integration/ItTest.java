import com.restaurant.dao.*;
import com.restaurant.exception.*;
import com.restaurant.model.*;
import com.restaurant.service.*;
import java.util.*;

public class ItTest {
    static int pass, fail;
    static void check(String n, boolean ok) { if (ok) pass++; else fail++; System.out.println((ok ? "PASS  " : "FAIL  ") + n); }
    static MenuItem find(List<MenuItem> l, String name) { return l.stream().filter(m -> m.getName().equals(name)).findFirst().orElse(null); }
    static TableOverview tbl(TableService s, int number) throws Exception { return s.getTables().stream().filter(t -> t.getNumber() == number).findFirst().orElseThrow(); }

    public static void main(String[] a) throws Exception {
        MenuService menu = new MenuService(new MenuDaoImpl());
        TableService tables = new TableService(new TableDaoImpl());
        DashboardDao dash = new DashboardDaoImpl();
        if (a[0].equals("A")) phaseA(menu, tables, dash); else if (a[0].equals("B")) phaseB(menu, tables, dash); else phaseC(menu, tables, dash);
        System.out.println("RESULT " + a[0] + ": " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void phaseA(MenuService menu, TableService tables, DashboardDao dash) throws Exception {
        System.out.println("== MENU (real DAOs -> JDBC -> database) ==");
        List<MenuItem> items = menu.getAllItems();
        check("menu loads the 9 starter dishes", items.size() == 9);
        check("categories come from the database", menu.categoriesFor(items).containsAll(List.of("Starters", "Main Course", "Beverages")));
        MenuItem chicken = find(items, "Butter Chicken");
        check("Butter Chicken has category Main Course and price 380.00", chicken != null && chicken.getCategory().equals("Main Course") && chicken.getPrice().toPlainString().equals("380.00"));

        MenuItem added = menu.addItem("Test Pizza", "Italian", "199.5", true);
        check("add: new id returned", added.getId() > 0);
        MenuItem back = find(menu.getAllItems(), "Test Pizza");
        check("add: saved and read back (name, NEW category, price, available)", back != null && back.getCategory().equals("Italian") && back.getPrice().toPlainString().equals("199.50") && back.isAvailable());
        check("add: list now has 10", menu.getAllItems().size() == 10);
        check("categories now include the new one", menu.categoriesFor(menu.getAllItems()).contains("Italian"));
        try { menu.addItem("test pizza", "Italian", "10", true); check("add duplicate name refused", false); }
        catch (InvalidInputException e) { check("add duplicate name refused with friendly message: " + e.getMessage(), e.getMessage().contains("already exists")); }
        menu.updateItem(added.getId(), "Test Pizza XL", "Pizzas", "249", false);
        MenuItem upd = find(menu.getAllItems(), "Test Pizza XL");
        check("edit: name, category, price, availability saved", upd != null && upd.getCategory().equals("Pizzas") && upd.getPrice().toPlainString().equals("249.00") && !upd.isAvailable());
        check("edit: old name gone", find(menu.getAllItems(), "Test Pizza") == null);
        menu.deleteItem(added.getId());
        check("delete: item removed, back to 9", menu.getAllItems().size() == 9 && find(menu.getAllItems(), "Test Pizza XL") == null);
        try { menu.deleteItem(added.getId()); check("delete twice refused", false); }
        catch (InvalidInputException e) { check("delete again: friendly message: " + e.getMessage(), e.getMessage().contains("no longer exists")); }

        System.out.println("== TABLES ==");
        List<TableOverview> t = tables.getTables();
        check("9 tables loaded, all AVAILABLE", t.size() == 9 && t.stream().allMatch(x -> x.getStatus() == TableStatus.AVAILABLE));
        int orderId = tables.assignTable(tbl(tables, 2).getId(), "Rahul Sharma", "98765-43210");
        TableOverview t2 = tbl(tables, 2);
        check("assign: table 2 OCCUPIED with customer and the new PENDING order id", t2.getStatus() == TableStatus.OCCUPIED && "Rahul Sharma".equals(t2.getCustomerName()) && "9876543210".equals(t2.getCustomerPhone()) && t2.getOpenOrderId() == orderId);
        List<RecentOrder> orders = dash.findRecentOrders(10);
        check("assign: exactly ONE order exists and it is PENDING", orders.size() == 1 && orders.get(0).getStatus().equals("PENDING") && orders.get(0).getTableNumber() == 2);
        try { tables.assignTable(t2.getId(), "Someone Else", null); check("duplicate assignment refused", false); }
        catch (TableUnavailableException e) { check("duplicate assignment refused: " + e.getMessage(), true); }
        check("duplicate assignment created NO extra order", dash.findRecentOrders(10).size() == 1);
        tables.releaseTable(t2.getId());
        check("release: table 2 AVAILABLE again, no customer", tbl(tables, 2).getStatus() == TableStatus.AVAILABLE && tbl(tables, 2).getCustomerName() == null);
        check("release: its order is now CANCELLED", dash.findRecentOrders(10).get(0).getStatus().equals("CANCELLED"));
        try { tables.releaseTable(t2.getId()); check("release a free table refused", false); }
        catch (TableUnavailableException e) { check("release a free table refused: " + e.getMessage(), true); }

        System.out.println("== DASHBOARD (real, not demo) ==");
        DashboardData d = new DashboardService(dash).loadDashboard();
        check("dashboard is NOT in demo mode", !d.isDemoMode());
        check("dashboard numbers come from the database (1 order, 0 revenue, 9 tables free)", d.getTotalOrders() == 1 && d.getTotalRevenue().signum() == 0 && d.getAvailableTables() == 9 && d.getOccupiedTables() == 0);

        System.out.println("== leave data behind for the restart test ==");
        menu.addItem("Persist Test Dish", "Persistence", "77.77", true);
        tables.assignTable(tbl(tables, 5).getId(), "Persist Customer", null);
    }

    static void phaseB(MenuService menu, TableService tables, DashboardDao dash) throws Exception {
        System.out.println("== AFTER RESTART (new JVM) ==");
        MenuItem m = find(menu.getAllItems(), "Persist Test Dish");
        check("dish saved before restart is still there with category and price", m != null && m.getCategory().equals("Persistence") && m.getPrice().toPlainString().equals("77.77"));
        TableOverview t5 = tbl(tables, 5);
        check("table 5 still OCCUPIED by Persist Customer", t5.getStatus() == TableStatus.OCCUPIED && "Persist Customer".equals(t5.getCustomerName()));
        check("dashboard counts it (2 orders, 1 occupied)", new DashboardService(dash).loadDashboard().getTotalOrders() == 2 && new DashboardService(dash).loadDashboard().getOccupiedTables() == 1);
        menu.deleteItem(m.getId()); tables.releaseTable(t5.getId());
        check("cleanup: dish deleted and table released", find(menu.getAllItems(), "Persist Test Dish") == null && tbl(tables, 5).getStatus() == TableStatus.AVAILABLE);
    }

    static void phaseC(MenuService menu, TableService tables, DashboardDao dash) throws Exception {
        System.out.println("== WITH sample_data.sql loaded ==");
        DashboardData d = new DashboardService(dash).loadDashboard();
        check("dashboard: 5 sample orders + the 2 earlier test orders = 7", d.getTotalOrders() == 7);
        check("dashboard: revenue = 1239.00 + 840.00 = 2079.00", d.getTotalRevenue().compareTo(new java.math.BigDecimal("2079.00")) == 0);
        check("tables 1, 3, 6 OCCUPIED by the sample customers", tbl(tables, 1).getStatus() == TableStatus.OCCUPIED && "Rohan Verma".equals(tbl(tables, 1).getCustomerName()) && tbl(tables, 3).getStatus() == TableStatus.OCCUPIED && tbl(tables, 6).getStatus() == TableStatus.OCCUPIED);
        check("recent orders show amounts from order_items (Aarav 2*380+4*60+2*50 = 1100.00)", dash.findRecentOrders(20).stream().anyMatch(o -> o.getCustomerName().equals("Aarav Mehta") && o.getTotal().compareTo(new java.math.BigDecimal("1100.00")) == 0));
        MenuItem bc = find(menu.getAllItems(), "Butter Chicken");
        try { menu.deleteItem(bc.getId()); check("deleting a dish used in orders is refused", false); }
        catch (InvalidInputException e) { check("delete used dish refused with friendly message: " + e.getMessage().replace('\n', ' '), e.getMessage().contains("part of existing orders")); }
        check("the used dish is still on the menu", find(menu.getAllItems(), "Butter Chicken") != null);
        tables.releaseTable(tbl(tables, 3).getId());
        check("release table 3 (had food): table free, its order CANCELLED", tbl(tables, 3).getStatus() == TableStatus.AVAILABLE && dash.findRecentOrders(20).stream().anyMatch(o -> o.getCustomerName().equals("Aarav Mehta") && o.getStatus().equals("CANCELLED")));
    }
}
