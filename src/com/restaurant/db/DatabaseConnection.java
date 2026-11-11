package com.restaurant.db;

import com.restaurant.exception.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The ONLY place in the project that opens a JDBC connection.
 * Connection details are read from config/db.properties, which is NOT
 * committed to Git (so passwords never reach GitHub). Each team member keeps
 * their own copy, created from config/db.properties.example.
 * <p>
 * Every way the connection can fail gets its own clear message (what is wrong AND how to fix it),
 * followed by the real technical error, so nothing is hidden from the user.
 * Run {@link DbCheck} (check-db.bat / check-db.sh) to test the connection step by step.
 */
public final class DatabaseConnection {

    private static final String CONFIG_NAME = "db.properties";
    private static final String PLACEHOLDER_PASSWORD = "your_password_here"; // text from db.properties.example
    private static final String MYSQL_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private DatabaseConnection() {
        // utility class: no objects needed
    }

    /** Opens a connection or throws a {@link DatabaseException} that explains exactly what to fix. */
    public static Connection getConnection() throws DatabaseException {
        Properties props = loadConfig();
        String url = props.getProperty("db.url");
        requireDriver(url);
        String password = checkPassword(props);
        try {
            return DriverManager.getConnection(url, props.getProperty("db.user"), password);
        } catch (SQLException e) {
            throw new DatabaseException(explain(e), e);
        }
    }

    // ---------- step 1: the settings file ----------

    /** Folders we look in: the current folder and up to two folders above it (IDEs start programs in different folders). */
    public static List<Path> configCandidates() {
        List<Path> list = new ArrayList<>();
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 3 && dir != null; i++) {
            list.add(dir.resolve("config").resolve(CONFIG_NAME));
            dir = dir.getParent();
        }
        return list;
    }

    /** Finds config/db.properties or explains where it looked and what to do. */
    public static Path findConfigFile() throws DatabaseException {
        List<Path> candidates = configCandidates();
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        StringBuilder msg = new StringBuilder("The settings file config/db.properties was not found. Looked in: ");
        for (int i = 0; i < candidates.size(); i++) {
            msg.append(i == 0 ? "" : " | ").append(candidates.get(i));
        }
        msg.append(". FIX: copy config/db.properties.example to config/db.properties and enter your MySQL user and password.");
        for (Path p : candidates) {
            if (Files.exists(p.resolveSibling(CONFIG_NAME + ".txt"))) {
                msg.append(" NOTE: a file named db.properties.txt exists - Windows is hiding the .txt ending. "
                        + "Rename it to exactly db.properties (turn on View > File name extensions in Explorer).");
                break;
            }
        }
        throw new DatabaseException(msg.toString());
    }

    /** Reads the settings file and checks that db.url and db.user are present. */
    public static Properties loadConfig() throws DatabaseException {
        Path file = findConfigFile();
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new DatabaseException("The settings file " + file + " exists but cannot be read: " + e.getMessage(), e);
        }
        String url = props.getProperty("db.url");
        String user = props.getProperty("db.user");
        if (url == null || url.isBlank() || user == null || user.isBlank()) {
            throw new DatabaseException("The settings file " + file + " must contain db.url and db.user "
                    + "(and db.password). Compare it with config/db.properties.example.");
        }
        props.setProperty("db.url", url.trim());
        props.setProperty("db.user", user.trim());
        return props;
    }

    /** Returns the password, but refuses the example text so beginners notice they forgot to change it. */
    public static String checkPassword(Properties props) throws DatabaseException {
        String password = props.getProperty("db.password", "");
        if (PLACEHOLDER_PASSWORD.equals(password.trim())) {
            throw new DatabaseException("config/db.properties still contains the example password \""
                    + PLACEHOLDER_PASSWORD + "\". FIX: open the file and replace it with your real MySQL password "
                    + "(the one you chose when installing MySQL).");
        }
        return password;
    }

    // ---------- step 2: the MySQL driver jar ----------

    /** Makes sure the MySQL Connector/J jar is on the classpath (only checked for jdbc:mysql URLs). */
    public static void requireDriver(String url) throws DatabaseException {
        if (url == null || !url.startsWith("jdbc:mysql:")) {
            return;
        }
        try {
            Class.forName(MYSQL_DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new DatabaseException("The MySQL driver is missing: no mysql-connector-j .jar file was found. "
                    + "FIX: download MySQL Connector/J (Platform Independent ZIP) from dev.mysql.com/downloads/connector/j, "
                    + "copy the mysql-connector-j-x.y.z.jar file into the lib folder of the project, then start the app "
                    + "with run.bat / run.sh (in an IDE: add the jar as a library).", e);
        }
    }

    // ---------- step 3: turning a JDBC error into advice ----------

    /** Translates the most common MySQL connection errors into plain language, and always adds the technical detail. */
    public static String explain(SQLException e) {
        int code = e.getErrorCode();
        String state = e.getSQLState();
        String msg = String.valueOf(e.getMessage());
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String reason;
        if (code == 1045 || "28000".equals(state)) {
            reason = "MySQL rejected the user name or password. FIX: check db.user and db.password in config/db.properties "
                    + "(test them by logging in with MySQL Workbench).";
        } else if (code == 1049) {
            reason = "MySQL is running but the database does not exist yet. FIX: run database/schema.sql and then "
                    + "database/seed.sql in MySQL Workbench (or the mysql command line).";
        } else if (code == 1044) {
            reason = "This MySQL user is not allowed to use the database. FIX: use the root user, or grant the user access.";
        } else if (root instanceof UnknownHostException) {
            reason = "The computer name in db.url cannot be found. FIX: for MySQL on this computer use localhost.";
        } else if (root instanceof ConnectException || "08S01".equals(state)
                || msg.contains("Communications link failure")) {
            reason = "Cannot reach the MySQL server at the host and port in db.url. FIX: make sure MySQL is running "
                    + "(Windows: open Services, start MySQL80 / MySQL) and that the port is correct (normally 3306).";
        } else if (msg.contains("Public Key Retrieval")) {
            reason = "MySQL needs allowPublicKeyRetrieval=true in db.url. FIX: add &allowPublicKeyRetrieval=true "
                    + "to the end of db.url.";
        } else if (msg.toLowerCase().contains("time zone")) {
            reason = "MySQL does not understand the time zone in db.url. FIX: use serverTimezone=Asia/Kolkata "
                    + "(or UTC) in db.url.";
        } else if (msg.contains("No suitable driver")) {
            reason = "db.url is not a valid MySQL address. FIX: it must start with jdbc:mysql:// "
                    + "(compare with config/db.properties.example).";
        } else {
            reason = "Could not connect to MySQL.";
        }
        return reason + " [Technical detail: " + (code != 0 ? "error " + code + ", " : "") + msg + "]";
    }
}
