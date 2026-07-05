# 🏛️ PROJECT CONSTRAINTS & ARCHITECTURE GUIDELINES

## 1. TECH STACK & STRICT PROHIBITIONS
- **Java:** 17+
- **Core Framework:** Spring Core, Spring MVC (Configured via Java Config, NO XML).
- **Web:** Servlets, Thymeleaf.
- **Database:** PostgreSQL.
- **Data Access:** PURE JDBC.
- **STRICT PROHIBITION 1:** Do NOT use ORM frameworks (Hibernate, JPA, Spring Data JPA, EclipseLink).
- **STRICT PROHIBITION 2:** Do NOT use third-party Connection Pools (HikariCP, Tomcat JDBC, c3p0). A custom Thread-Safe Connection Pool must be implemented from scratch.

## 2. NAMING CONVENTIONS & DATA TRANSFER
- **Database:** All tables and columns MUST use `snake_case`.
- **Java Objects:** All Entities, Models, Request DTOs, and Response DTOs MUST use `CamelCase` (PascalCase for classes, camelCase for fields). 
- Mapping between `snake_case` DB fields and `CamelCase` Java fields must be handled manually in the RowMappers/DAO layer.

## 3. ARCHITECTURE & LAYERING
Strict MVC & Layered Architecture. Components must be highly cohesive and decoupled.
- `controller`: Spring MVC `@Controller` classes. Validation and HTTP request/response handling.
- `service`: Business logic, transaction management, DTO conversions.
- `dao`: Data Access Object layer. Pure JDBC interfaces and their implementations.
- `model`: Domain entities (`User`, `Book`, `BookCopy`, `BorrowRecord`).
- `dto`: Subpackages `request` and `response` for data transfer.
- `config`: Spring Configuration classes, Custom Connection Pool.
- `exception`: Custom domain exceptions and Global Exception Handler.
- `util`: Security (BCrypt wrapper), pagination, validation utilities.

## 4. DATABASE & TRANSACTION MANAGEMENT
- All queries must use `PreparedStatement` to prevent SQL Injection.
- **Transaction Management:** Handled MANUALLY via JDBC `Connection` object at the `Service` layer. 
  - Standard flow: `connection.setAutoCommit(false)` -> execute DAOs -> `connection.commit()` -> `catch: connection.rollback()`.
  - Use a `ThreadLocal` connection manager to ensure DAOs participate in the same transaction.

## 5. BUSINESS LOGIC (ERD v2 Rules)
- **Roles:** `READER`, `LIBRARIAN`, `ADMIN`.
- **Entities:**
  - `Book`: Abstract concept (Title, Author, ISBN).
  - `BookCopy`: Physical instance (inventory_number, status: `AVAILABLE`, `RESERVED`, `ISSUED`).
- **Borrowing Lifecycle:** `BorrowRecord` statuses: `PENDING_APPROVE` -> `BORROWED` -> `PENDING_RETURN` -> `RETURNED` (also `CANCELLED`, `REJECTED`).
- **Lending Types:** `HOME`, `READING_ROOM`. Due dates must be calculated based on the lending type.
- **Access Control:** `User.is_locked` boolean. Blocked users lose all access immediately.

## 6. SECURITY, AOP & PATTERNS
- Passwords MUST be hashed with `BCrypt`.
- PRG (Post-Redirect-Get) pattern must be strictly used for all form submissions to prevent duplicate submissions (F5).
- XSS prevention: rely on Thymeleaf text escaping (`th:text`).
- Apply **Spring AOP** for logging method execution times and auditing sensitive operations (e.g., locking users, issuing books).
- Use **Spring MVC Interceptors** to intercept requests, check user authentication, role authorization, and `is_locked` status.
- **Design Patterns:** Utilize at least two patterns in business logic (e.g., `Builder` for complex SQL search queries, `Strategy` for calculating lending periods).

## 7. QUALITY ASSURANCE
- i18n support (EN, RU, KZ) via resource bundles.
- Code coverage >50% on Service and DAO layers using JUnit 5 + Mockito.
- Comprehensive Javadoc for Service interfaces.