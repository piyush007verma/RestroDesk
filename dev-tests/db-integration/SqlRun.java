import java.nio.file.*; import java.sql.*; import java.util.*;
public class SqlRun {
    public static void main(String[] a) throws Exception {
        String url = a[0]; StringBuilder sb = new StringBuilder();
        for (int i = 1; i < a.length; i++) for (String line : Files.readAllLines(Path.of(a[i]))) { if (!line.trim().startsWith("--")) sb.append(line).append('\n'); }
        int ok = 0, bad = 0;
        try (Connection c = DriverManager.getConnection(url, "root", ""); Statement st = c.createStatement()) {
            for (String stmt : sb.toString().split(";\\s*\\n")) { String q = stmt.trim(); if (q.isEmpty()) continue;
                try { st.execute(q); ok++; } catch (SQLException e) { bad++; System.out.println("SQL ERROR: " + e.getMessage() + "\n   in: " + q.substring(0, Math.min(90, q.length())).replace('\n',' ')); } }
        }
        System.out.println("statements ok=" + ok + " failed=" + bad); System.exit(bad == 0 ? 0 : 1);
    }
}
