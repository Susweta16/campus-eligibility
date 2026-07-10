# Placement Hub — Campus Placement Eligibility Checker

A backend system that cross-checks student academic records against
company-specific placement eligibility criteria, explains *why* a student
is or isn't eligible, and gives placement-cell staff a login-protected
dashboard to manage everything.

## Overview

Most "eligibility checker" projects hardcode logic like:

```java
if (cgpa > 7 && backlogs == 0) { eligible = true; }
```

That breaks the moment a second company shows up with different criteria.
This project treats eligibility criteria as **data**, not code — each
company defines its own thresholds (CGPA, branch, backlogs, gap years,
10th/12th %), and a single rule engine evaluates any student against any
company. The engine returns **structured reasons for rejection** (e.g.
`"CGPA 6.20 is below required 6.50"`), not just a pass/fail flag.

## Features

- **Dashboard** — live counts of students, companies, eligible matches,
  and average CGPA, plus a branch-wise student distribution chart,
  a company-wise eligible-students chart, and a recent activity feed
  (every add/edit/delete/import/eligibility-check is logged with a
  timestamp). Fully responsive — the sidebar collapses to a horizontal
  bar on narrow/mobile screens.
- **Student management** — add, edit, delete, search by name/branch,
  filter by branch and minimum CGPA.
- **Company management** — add, edit, delete, search by name, criteria
  defined per company (min CGPA, allowed branches, max backlogs, max gap
  years, min 10th/12th %).
- **Eligibility engine** — cross-checks every student against every
  company and shows exactly which criteria passed or failed.
- **Admin authentication** — server-side session login (not a frontend
  password check). Only logged-in admins can add, edit, delete, or
  bulk-import; anyone can view the dashboard and run eligibility checks.
- **CSV export** — download all eligible student-company matches as a CSV.
- **Data persistence** — every change is written to disk (`data/*.csv`)
  and reloaded on restart, so nothing is lost when the server restarts.
- **Dark mode** — toggle in the sidebar, preference remembered per browser.
- **Toast notifications** — clear success/error feedback on every action.

## Technologies used

- **Backend:** Plain Java (JDK 11+), using the built-in
  `com.sun.net.httpserver.HttpServer` — no Spring Boot, no Maven, no
  external dependencies.
- **Frontend:** Vanilla HTML/CSS/JavaScript — no React, no build step.
- **Persistence:** Flat CSV files on disk (no external database driver).
- **Deployment:** Docker (multi-stage build) → deployed on Render.

This stack was chosen deliberately: it builds and runs anywhere a JDK is
available, with no dependency downloads, no build tool, and no database
server to configure — while still demonstrating the same HTTP,
authentication, and data-modeling concepts a framework like Spring Boot
would provide.

## Architecture

```
Browser (index.html)
      │  fetch() calls to /api/*
      ▼
Server.java (HttpServer)
 ├── /api/login, /api/logout, /api/session   → session auth (cookie-based)
 ├── /api/students, /api/companies           → CRUD (POST/PUT/DELETE require login)
 ├── /api/import/students                    → bulk CSV import (requires login)
 ├── /api/check, /api/check-all              → EligibilityEngine.evaluate(...)
 └── /api/export/eligible                    → CSV download
      │
      ▼
EligibilityEngine.java  →  independent per-criterion checks, each
                            producing a human-readable reason on failure
      │
      ▼
data/students_store.csv, data/companies_store.csv   (persisted on every write)
```

## Screenshots

_Add screenshots of your deployed dashboard, students page, and
eligibility matrix here before sharing this repo — e.g._

```markdown
![Dashboard](docs/screenshot-dashboard.png)
![Eligibility Matrix](docs/screenshot-eligibility.png)
```

## Project structure

```
campus-eligibility/
├── src/eligibility/
│   ├── Student.java            student record
│   ├── Company.java            company + its eligibility criteria
│   ├── EligibilityEngine.java  the rule engine (core logic)
│   ├── EligibilityResult.java  eligible? + list of reasons
│   ├── JsonUtil.java           minimal JSON encode/decode, no dependencies
│   └── Server.java             REST API, auth, persistence, static file serving
├── web/index.html               frontend dashboard (login, CRUD, search, export)
├── data/sample_students.csv     sample file for the bulk import endpoint
├── Dockerfile                   container build for Render/Railway/Fly.io
└── run.sh                       local compile + run script
```

## Installation guide (running locally)

Requires a JDK (11+). No Maven, no internet access needed to build.

```bash
git clone <your-repo-url>
cd campus-eligibility
./run.sh
```

Then open **http://localhost:8080** in a browser. The app starts
pre-seeded with sample students and companies.

**Default admin login (local only):** username `admin`, password
`changeme123`. Change this before deploying anywhere public — see below.

## Deployment guide

The app reads the `PORT` environment variable (falls back to 8080
locally) and includes a `Dockerfile`, so it deploys as-is to any
container-friendly host.

1. Push this project to a GitHub repo.
2. Go to [render.com](https://render.com) → **New** → **Web Service** →
   connect your repo. Render auto-detects the `Dockerfile`.
3. **Before deploying, set environment variables** (Render → your service
   → Environment):
   - `ADMIN_USERNAME` — your chosen admin username
   - `ADMIN_PASSWORD` — a strong password (do **not** leave the default)
4. Deploy. You'll get a public URL like `https://your-app.onrender.com`.

Railway and Fly.io work the same way (connect repo → Dockerfile detected
→ set the same environment variables → deploy).

## API reference

| Method | Endpoint | Auth required | Description |
|--------|----------|:---:|-------------|
| POST   | `/api/login` | — | Log in `{username, password}`, sets session cookie |
| POST   | `/api/logout` | — | Clears the session |
| GET    | `/api/session` | — | `{authenticated: true/false}` |
| GET    | `/api/students` | — | List all students |
| POST   | `/api/students` | ✅ | Add a student |
| PUT    | `/api/students?id=` | ✅ | Update a student |
| DELETE | `/api/students?id=` | ✅ | Delete a student |
| GET    | `/api/companies` | — | List all companies |
| POST   | `/api/companies` | ✅ | Add a company |
| PUT    | `/api/companies?id=` | ✅ | Update a company |
| DELETE | `/api/companies?id=` | ✅ | Delete a company |
| POST   | `/api/import/students` | ✅ | Bulk-add students from CSV (raw body) |
| GET    | `/api/check?studentId=&companyId=` | — | Check one student against one company |
| GET    | `/api/check-all` | — | Full eligibility matrix |
| GET    | `/api/export/eligible` | — | Download CSV of all eligible matches |
| GET    | `/api/dashboard-stats` | — | Branch distribution, per-company eligible counts, averages |
| GET    | `/api/activity` | — | Recent activity feed (last 30 events) |

`allowedBranches` accepts `"ANY"` or a `|`-separated list like `"CSE|IT"`.

## Testing every feature

1. **Login:** click "Admin Login" in the sidebar, enter your credentials
   — the sidebar should switch to "Logged in as admin."
2. **Add a student/company:** fill either form and submit — it should
   appear in the list below immediately, and persist after a restart.
3. **Search/filter:** type in the student/company search boxes — the
   list should filter instantly, no page reload.
4. **Edit:** click "Edit" on any row, change a value, submit — the row
   should update.
5. **Delete:** click "Delete" — you should get a confirmation prompt
   before anything is removed.
6. **Eligibility check:** go to "Eligibility Check" → "Run Eligibility
   Check" — a table should appear with per-criterion failure reasons.
7. **Export:** click "Export Eligible (CSV)" — a CSV file should download.
8. **Dark mode:** toggle it in the sidebar — the whole UI should restyle,
   and the choice should persist after a page refresh.
9. **Logged-out protection:** log out, then try adding a student — you
   should see a "Please log in first" toast and the login modal.

## Design decisions worth mentioning in an interview

- **Rule engine as independent checks, not one big conditional** — each
  criterion is its own method that appends a reason only on failure,
  making the rule set extensible and individually testable.
- **No external frameworks** — built on the JDK's built-in HTTP server
  rather than Spring Boot, so it has zero dependencies to install, while
  still implementing the same core concepts (routing, JSON handling,
  cookie-based sessions) a framework would provide.
- **Server-side sessions, not frontend-only login** — the admin password
  is checked on the server against an environment variable, and a random
  session token is issued and validated on every write request; the
  frontend never holds or checks the password itself.
- **File-backed persistence** — every change is written to CSV files and
  reloaded on startup, avoiding the need for an external database driver
  while still surviving restarts.

## Future improvements

- Swap the CSV-file persistence for a real embedded database (SQLite) —
  the storage functions in `Server.java` are isolated, so this is a
  contained change.
- Add password hashing (bcrypt) instead of a plain-text env-var
  comparison, and support multiple admin accounts.
- Add a student profile page with skills/projects and eligibility
  history per student.
- Persist the activity log to disk (currently in-memory, so it resets
  when the server restarts, unlike student/company data).
- Migrate the HTTP layer to Spring Boot once Maven/network access is
  available, keeping `EligibilityEngine` untouched.
