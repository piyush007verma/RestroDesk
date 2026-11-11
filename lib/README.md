# lib/ - external libraries

The only external library is the **MySQL JDBC driver** (MySQL Connector/J).

1. Download "Platform Independent" ZIP from https://dev.mysql.com/downloads/connector/j/
2. Copy `mysql-connector-j-<version>.jar` into this `lib/` folder.
3. IntelliJ: File > Project Structure > Libraries > + > Java > choose the jar.
   VS Code: the folder `lib/` is picked up automatically by the Java extension.

The jar is ignored by Git, so each team member downloads it once.
The app still opens without it (the dashboard shows demo data).
