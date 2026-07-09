# Campus Placement Eligibility Checker

A small backend system that cross-checks student academic records against
company-specific placement eligibility criteria, and explains *why* a
student is or isn't eligible — not just a pass/fail flag.

## Why this exists

Most "eligibility checker" projects hardcode logic like:

```java
if (cgpa > 7 && backlogs == 0) { eligible = true; }
```

That breaks the moment a second company shows up with different criteria.
This project treats eligibility criteria as **data**, not code — each
company defines its own thresholds (CGPA, branch, backlogs, gap years,
10th/12th %), and a single rule engine evaluates any student against any
company. Adding a new company doesn't require touching the eligibility
logic at all.

The engine also returns **structured reasons for rejection** (e.g.
`"CGPA 6.20 is below required 6.50"`), which is the difference between a
toy CRUD app and something that models a real placement-cell workflow.

## Design decisions worth mentioning in an interview

- **Rule engine as independent checks, not one big conditional.** Each
  criterion (`checkCgpa`, `checkBranch`, `checkBacklogs`, ...) is its own
  method that appends a reason only on failure. This makes the rule set
  extensible and each rejection individually explainable/testable.
- **No external frameworks.** Built on the JDK's built-in
  `com.sun.net.httpserver.HttpServer` rather than Spring Boot. This was a
  practical choice (works with zero internet/dependency setup) but also
  demonstrates understanding of what a framework like Spring is actually
  doing underneath — routing, request/response handling, JSON
  serialization — all done explicitly here.
- **Bulk CSV import** for students, since in a real placement cell,
  student data arrives as a spreadsheet export, not one form submission
  at a time.
- **File-backed persistence.** Every add is written to `data/students_store.csv`
  and `data/companies_store.csv`, and reloaded on startup — restarting the
  server doesn't wipe your data. This is intentionally simple (no external
  DB driver, since the project has zero dependencies) but solves the real
  problem: data survives a restart or redeploy.

## Project structure

```
campus-eligibility/
├── src/eligibility/
│   ├── Student.java            student record
│   ├── Company.java            company + its eligibility criteria
│   ├── EligibilityEngine.java  the rule engine (core logic)
│   ├── EligibilityResult.java  eligible? + list of reasons
│   ├── JsonUtil.java           minimal JSON encode/decode, no dependencies
│   └── Server.java             REST API + static file serving
├── web/index.html              frontend (add students/companies, run checks)
├── data/sample_students.csv    sample file for the bulk import endpoint
└── run.sh                      compile + run
```

## Running it

Requires a JDK (11+) — no Maven, no internet access needed.

```bash
./run.sh
```

Then open **http://localhost:8080** in a browser.

The app starts pre-seeded with a few sample students and companies so
there's something to look at immediately.

## API reference

| Method | Endpoint                | Description                                  |
|--------|--------------------------|-----------------------------------------------|
| GET    | `/api/students`          | List all students                            |
| POST   | `/api/students`          | Add a student (JSON body)                    |
| GET    | `/api/companies`         | List all companies                           |
| POST   | `/api/companies`         | Add a company + its criteria (JSON body)     |
| POST   | `/api/import/students`   | Bulk-add students from CSV (raw text body)   |
| GET    | `/api/check?studentId=&companyId=` | Check one student against one company |
| GET    | `/api/check-all`         | Full eligibility matrix (every student × every company) |

### Example: add a company

```bash
curl -X POST http://localhost:8080/api/companies \
  -d '{"name":"Acme Corp","minCgpa":"7.0","allowedBranches":"CSE|IT","maxBacklogs":"0","maxGapYears":"1","minTenthPercent":"70","minTwelfthPercent":"70"}'
```

`allowedBranches` accepts `"ANY"` for no restriction, or a `|`-separated
list like `"CSE|IT|ECE"`.

### Example: bulk import students

```bash
curl -X POST http://localhost:8080/api/import/students \
  --data-binary @data/sample_students.csv
```

## Deploying it (so you have a public link, not just localhost)

The app already reads the `PORT` environment variable (falls back to 8080
locally), and a `Dockerfile` is included, so it deploys as-is to any
container-friendly host. **Render** has the simplest free path:

1. Push this project to a GitHub repo.
2. Go to [render.com](https://render.com) → New → Web Service → connect
   your repo.
3. Render will detect the `Dockerfile` automatically. Leave build/start
   commands blank (Docker handles it).
4. Deploy. You'll get a public URL like `https://your-app.onrender.com`
   — that's what goes on your CV.

**Railway** and **Fly.io** work the same way (connect repo → they read
the Dockerfile → deploy). Free tiers on all three may spin the app down
after inactivity, so the first load after idling can take a few seconds
— that's normal, not a bug.

## Possible extensions

- Swap the CSV-file persistence for a real embedded database (SQLite) if
  you want SQL querying — the storage functions (`saveStudentsToDisk`,
  `loadStudentsFromDisk`, etc.) are isolated in `Server.java`, so this is
  a contained change.
- Add authentication so only placement-cell staff can add companies.
- Migrate the HTTP layer to Spring Boot once network/Maven access is
  available, keeping `EligibilityEngine` untouched (it has no framework
  dependency, so this is a drop-in swap).
