# Opportunity Nursery School Management System

A complete school management MVP for Opportunity Nursery School: academic years & terms,
teachers, subjects, classes, student enrollment, yearly promotion, attendance, exams &
marks, report cards, fees and dashboards — with a vanilla JavaScript single-page app
served by the same Spring Boot process. No frontend framework, no build step, one command.

## Tech Stack

| Layer      | Technology |
|------------|------------|
| Backend    | Java 25, Spring Boot 3.5 (Web, Validation), Spring Data JPA / Hibernate 6 |
| Security   | Spring Security + JWT (jjwt 0.12), BCrypt password hashing |
| Database   | MySQL 8 (utf8mb4), schema auto-created via `ddl-auto=update` |
| Frontend   | Single HTML page + vanilla CSS/JS SPA (hash router, fetch + JWT) |
| Build      | Maven (wrapper included — no Maven install needed) |

## Project Structure

```
src/main/java/com/opportunity/school/
├── model/           16 JPA entities (+ model/enums: ClassLevel, Role, ...)
├── repository/      Spring Data JPA repositories
├── dto/             Request/response DTOs
├── service/         Business logic (9 services)
├── controller/      REST controllers (12 controllers)
├── security/        JWT filter, entry point, user details service
├── config/          Security config, seed data loader
└── exception/       Global exception handler (JSON {timestamp, status, message})
src/main/resources/
├── application.properties        main config (MySQL)
├── application-dev.properties    dev profile (seed data)
├── application-test.properties   test profile (H2 in-memory)
└── static/           SPA: index.html, css/, js/ (api, auth, router, ui + 12 modules)
src/test/java/        JUnit tests (PromotionServiceTest)
```

## Requirements

- **Java 25** (JDK 25 LTS)
- **MySQL 8** server running locally (or reachable via env vars)
- No Maven install needed — use the included wrapper (`./mvnw` / `mvnw.cmd`)

## Database Setup

Create the database once (tables are created automatically by Hibernate on first start):

```sql
CREATE DATABASE opportunity_school CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Connection settings come from `application.properties` and can be overridden with
environment variables — no code change needed:

| Variable      | Default            | Purpose          |
|---------------|--------------------|------------------|
| `DB_HOST`     | `localhost`        | MySQL host       |
| `DB_PORT`     | `3306`             | MySQL port       |
| `DB_NAME`     | `opportunity_school` | Database name  |
| `DB_USER`     | `root`             | MySQL user       |
| `DB_PASSWORD` | *(empty)*          | MySQL password   |
| `JWT_SECRET`  | built-in dev key   | **Change in production** |
| `APP_PUBLIC_URL` | `http://localhost:8081` | Base URL used in password-reset links |
| `MAIL_FROM` | `no-reply@opportunity-school.local` | Password-reset sender address |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | unset | SMTP server for password-reset email |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | unset | SMTP credentials |

## Running the App

**With seed data (recommended for first run)** — the `dev` profile loads demo data:

```bash
# Linux / macOS / Git Bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Windows CMD
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

**Without seed data** (empty database, create users via the app):

```bash
./mvnw spring-boot:run
```

Then open **http://localhost:8081/login.html** — the SPA login screen is served from the same process.

Teacher self-registration is available from the login screen. Password-reset links are emailed
when SMTP is configured. In the `dev` profile without SMTP, the reset link is written to the
application log for local testing; configure SMTP and `APP_PUBLIC_URL` before deployment.

> Building/running on a very new JDK (e.g. 26) from Git Bash: first run
> `export JAVA_HOME="/c/Program Files/Java/jdk-26"` (adjust path to your JDK).

## Default Logins (dev seed)

| Role    | Email            | Password        |
|---------|------------------|-----------------|
| ADMIN   | `admin@oes.com`  | `Admin@2026`    |
| TEACHER | `teacher@oes.com`| `Teacher@2026`  |

> The seed loader only runs under the `dev` profile and skips everything it finds already
> present. Change these passwords immediately for any real deployment.

### What the dev seed creates

- Academic year **2026** with TERM_1 (Jan 5 – Apr 3) and TERM_2 (Apr 27 – Aug 1)
- Subjects: **Letters, Numbers, Writing**
- Classes: Baby Class, KG1, KG2, KG3 (all in 2026)
- 8 students with admission numbers `ON-2026-0001` … `ON-2026-0008`
- Attendance for the last 3 weekdays
- Fee structure: **12,000 per term** (general) + one recorded payment (receipt
  `RCP-2026-000001`)
- One KG1 exam *“Mid Term 1 Opener”* (max 100) with Letters + Numbers marks entered

## Roles & Access

| Role    | Access |
|---------|--------|
| ADMIN   | Everything under `/api/admin/**`, dashboards, report cards, promotion, fees |
| TEACHER | `/api/teacher/**` only — own led classes, attendance, marks, exam submission |

The SPA renders a different sidebar per role and hides blocks the role cannot access;
the backend enforces the same rules with role checks + JWT.

## API Summary

Base URL: `http://localhost:8081`. Public auth endpoints are listed below; other endpoints require
the `Authorization: Bearer <token>` header. Errors return `{timestamp, status, message}`.

### Auth — `/api/auth`
| Method | Path     | Description |
|--------|----------|-------------|
| POST   | `/login` | Email + password → `{token, user}` |
| POST   | `/register` | Create a teacher account (role is always TEACHER) |
| POST   | `/forgot-password` | Request a one-time, expiring reset link by email |
| POST   | `/reset-password` | Set a password using a valid reset token |
| GET    | `/me`    | Current authenticated user |

### Account — `/api/account` (authenticated)
| Method | Path | Description |
|--------|------|-------------|
| PUT | `/credentials` | Change email and/or password after confirming the current password |

### Admin — `/api/admin/**` (ADMIN only)
| Module | Endpoints |
|--------|-----------|
| Years & terms | `POST /years`, `GET /years`, `POST /years/set-current`, `PUT /years/terms/{termId}`, `GET /years/current-context` |
| Teachers | `POST|GET /teachers`, `PUT|DELETE /teachers/{id}` |
| Subjects | `POST|GET /subjects`, `PUT|DELETE /subjects/{id}` |
| Classes | `POST|GET /classes`, `GET /classes/{id}`, `POST /classes/{id}/class-teacher`, `POST /classes/{id}/subjects`, `DELETE /classes/{id}/subjects/{classSubjectId}` |
| Students | `POST|GET /students`, `GET|PUT|DELETE /students/{id}`, `POST /students/{id}/move` |
| Promotion | `GET /promotion/preview?toYear=`, `POST /promotion?toYear=` |
| Attendance | `GET /attendance?classId&date`, `GET /attendance/summary`, `GET /attendance/report` |
| Exams & results | `POST|GET /exams`, `POST /exams/{id}/status`, `GET /exams/{id}/results`, `POST /exams/{id}/approve`, `POST /exams/{id}/return`, `GET /exams/{id}/report-card/{studentId}`, `GET /exams/{id}/report-cards` |
| Fees | `POST|GET /fees/structures`, `POST /fees/payments`, `GET /fees/statuses`, `GET /fees/summary`, `GET /fees/statement/{studentId}`, `GET /fees/receipt/{receiptNumber}` |

### Teacher — `/api/teacher/**` (TEACHER only)
| Method | Path | Description |
|--------|------|-------------|
| GET  | `/my-classes` | Classes led by the teacher |
| GET  | `/my-classes/{classId}` | Class detail incl. students |
| GET  | `/attendance?classId&date` | Students + status for a date |
| POST | `/attendance` | Save attendance for a class/date |
| POST | `/marks` | Enter/update marks for an OPEN exam |
| GET  | `/exams` | Exams relevant to the teacher |
| GET  | `/exams/{examId}/progress` | Mark-entry completeness per subject |
| GET  | `/exams/{examId}/results` | Result preview |
| POST | `/exams/{examId}/send` | Submit results for admin approval |

### Dashboards
| Method | Path | Role |
|--------|------|------|
| GET | `/api/admin/dashboard` | ADMIN |
| GET | `/api/teacher/dashboard` | TEACHER |

## Core Business Rules

- **Class levels** (fixed order): `BABY_CLASS → KG1 → KG2 → KG3`.
- **Promotion** runs per academic year: each student moves to the next level's class in
  the target year (classes are auto-created if missing), the previous enrollment is
  completed, KG3 students are marked **COMPLETE** (no next level), students already
  placed in the target year or removed are **skipped**. `GET /promotion/preview` is a
  dry run that persists nothing.
- **Removals** (student: shift/died/complete; teacher: fired/shift/died/complete) are
  logged; removing a teacher disables their login and releases led classes.
- **Exams** follow `DRAFT → OPEN → SUBMITTED → APPROVED / RETURNED`. Marks can be entered
  only while OPEN. The class teacher sends results only when every subject is marked for
  every active student. Admin approves or returns (with a comment) — returned exams are
  editable again. **Report cards are available for APPROVED exams only.**
- **Grading:** A ≥ 80, B ≥ 65, C ≥ 50, D ≥ 35, else E. Positions handle ties (equal
  totals share a position; subsequent positions are skipped).
- **Report cards** include the attendance summary for the exam's term.
- **Fees:** structures are per term, per class or general (school-wide). Payments can't
  exceed the expected amount (class-specific fee, else general); receipts are
  auto-numbered `RCP-<year>-<6 digits>`. Statuses: PAID / PARTIAL / NOT_PAID.
- **Admission numbers** are auto-generated `ON-<year>-<4 digits>` (e.g. `ON-2026-0001`).

## Tests

```bash
./mvnw test
```

Runs on an in-memory H2 database (MySQL compatibility mode) — no MySQL needed for tests.
`PromotionServiceTest` covers level ordering, class creation, skip rules, removed
students, dry-run behaviour and error handling.

## Frontend (SPA) Notes

- Everything lives in `src/main/resources/static` — plain files, served by Spring Boot.
- Hash routing (`#/dashboard`, `#/students`, …), one JS module per feature.
- The JWT is stored in `localStorage` (`oes_token`); any 401 clears it and returns to the
  login view.
- Report cards open a print-optimized view (`css/print.css`, page-break per student).
#   O P P O R T U N I T Y - M A N A G E M E N T - S Y S T E M 
 
 