# Library Management System

A robust, multi-role Web Application for managing a library's catalog, book inventory, borrowing lifecycle, and user accounts. Built with a modern Java tech stack without relying on Spring Boot, demonstrating a deep understanding of core Spring Framework capabilities.

## Technology Stack

*   **Language:** Java 17
*   **Framework:** Spring MVC 6, Spring Core, Spring Security 6
*   **Web Container:** Apache Tomcat 11.0.x
*   **Templating Engine:** Thymeleaf 3.1
*   **Database:** PostgreSQL 15+
*   **Build Tool:** Maven
*   **Frontend:** HTML5, Bootstrap 5, Vanilla JS

## Features

*   **Role-Based Access Control (RBAC):** Three distinct roles (`READER`, `LIBRARIAN`, `ADMIN`) managed securely via Spring Security.
*   **Catalog & Inventory:** Browse books, view detailed metadata (authors, genres), and manage physical copies (inventory numbers, status).
*   **Borrowing Lifecycle:** Readers can request to borrow books; Librarians can approve, reject, or mark books as returned.
*   **Admin Dashboard:** Administrators can manage users, create new librarian accounts, and lock/unlock accounts.
*   **Security Best Practices:** Password hashing (BCrypt), CSRF protection, and prevention of account enumeration vulnerabilities.
*   **Internationalization (i18n):** Multi-language support (English, Russian, Kazakh) using Spring Locale Resolvers.

## Getting Started

### Prerequisites

Ensure you have the following installed on your local machine:
*   [Java Development Kit (JDK) 21](https://jdk.java.net/21/)
*   [Apache Maven 3.8+](https://maven.apache.org/)
*   [PostgreSQL](https://www.postgresql.org/)
*   [Apache Tomcat 11.0.x](https://tomcat.apache.org/download-11.cgi)

### 1. Database Configuration

1.  Open your PostgreSQL client (e.g., pgAdmin or psql) and create a new database:
    ```sql
    CREATE DATABASE library_db;
    ```
2.  Open `src/main/resources/application.properties` and update the credentials if necessary:
    ```properties
    db.url=jdbc:postgresql://localhost:5432/library_db
    db.username=postgres
    db.password=postgres
    ```
3. *(Note: The application uses a custom lightweight JDBC implementation. Ensure your schema is initialized by running the SQL scripts located in your project's `sql` or `resources` directory before launching the app).*

### 2. Building the Project

Open a terminal in the root directory of the project and run:

```bash
mvn clean package
```
This will compile the project, run tests, and package the application into a WAR file located in the `target/` directory (e.g., `target/library-web-app-1.0-SNAPSHOT.war`).

### 3. Deployment & Execution

**Using IntelliJ IDEA (Recommended):**
1. Open **Run/Debug Configurations**.
2. Add a new **Tomcat Server -> Local** configuration.
3. In the **Deployment** tab, add the artifact `library-web-app:war exploded`.
4. Set the Application context to `/` (root).
5. Run the server. The application will be available at `http://localhost:8080`.

**Manual Tomcat Deployment:**
1. Copy the generated `.war` file from the `target/` folder into your Tomcat's `webapps/` directory.
2. Rename the file to `ROOT.war` if you want it accessible at the root URL.
3. Start Tomcat using `bin/startup.bat` (Windows) or `bin/startup.sh` (Linux/Mac).

### 4. Setting up an Administrator Account

By default, newly registered users are assigned the `READER` role. To gain access to the Admin Panel, register an account on the website, then run the following SQL query in your database to elevate your privileges:

```sql
UPDATE users SET role = 'ADMIN' WHERE id = 1;
```
*(After running the query, remember to log out and log back in for Spring Security to fetch your updated permissions).*

## 🔒 Security Notes
This application adheres to strict security standards. For example, login failure handlers are configured to prevent account enumeration by obfuscating locked account statuses unless valid credentials are provided.
