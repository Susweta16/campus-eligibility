package eligibility;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

public class Server {

    static Map<String, Student> students = new ConcurrentHashMap<>();
    static Map<String, Company> companies = new ConcurrentHashMap<>();
    static EligibilityEngine engine = new EligibilityEngine();
    static int nextStudentId = 1;
    static int nextCompanyId = 1;

    // ---------- auth: simple server-side session, credentials from env vars ----------
    // Set ADMIN_USERNAME / ADMIN_PASSWORD as environment variables on your host (e.g. Render).
    // Falls back to admin/changeme123 for local runs if not set - change this before deploying.
    static final String ADMIN_USERNAME = System.getenv().getOrDefault("ADMIN_USERNAME", "admin");
    static final String ADMIN_PASSWORD = System.getenv().getOrDefault("ADMIN_PASSWORD", "changeme123");
    static final Map<String, Long> sessions = new ConcurrentHashMap<>(); // token -> expiry (epoch millis)
    static final long SESSION_DURATION_MS = 2 * 60 * 60 * 1000; // 2 hours

    // ---------- recent activity feed ----------
    static final Deque<String> activityLog = new ConcurrentLinkedDeque<>(); // most recent first, JSON strings
    static final int MAX_ACTIVITY_ENTRIES = 30;

    static void logActivity(String icon, String message) {
        String entry = "{\"icon\":\"" + JsonUtil.esc(icon) + "\",\"message\":\"" + JsonUtil.esc(message)
                + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        activityLog.addFirst(entry);
        while (activityLog.size() > MAX_ACTIVITY_ENTRIES) activityLog.removeLast();
    }

    public static void main(String[] args) throws IOException {
        loadPersistedOrSeed();

        // Cloud hosts (Render, Railway, etc.) assign a port via $PORT.
        // Falls back to 8080 for local runs.
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            port = Integer.parseInt(envPort);
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/login", Server::handleLogin);
        server.createContext("/api/logout", Server::handleLogout);
        server.createContext("/api/session", Server::handleSessionCheck);
        server.createContext("/api/students", Server::handleStudents);
        server.createContext("/api/companies", Server::handleCompanies);
        server.createContext("/api/import/students", Server::handleImportStudents);
        server.createContext("/api/check-all", Server::handleCheckAll);
        server.createContext("/api/check", Server::handleCheckOne);
        server.createContext("/api/export/eligible", Server::handleExportEligibleCsv);
        server.createContext("/api/activity", Server::handleActivity);
        server.createContext("/api/dashboard-stats", Server::handleDashboardStats);
        server.createContext("/", Server::handleStatic);

        server.setExecutor(null);
        server.start();
        System.out.println("Campus Placement Eligibility Checker running on port " + port);
    }

    // ---------- Seed data so the app is usable immediately ----------
    static void loadSeedData() {
        addStudent(new Student(id(nextStudentId++, "S"), "Aditi Rao", 8.2, "CSE", 0, 0, 92, 88));
        addStudent(new Student(id(nextStudentId++, "S"), "Rohit Verma", 6.4, "ECE", 1, 0, 78, 74));
        addStudent(new Student(id(nextStudentId++, "S"), "Kavya Nair", 7.1, "MECH", 0, 1, 85, 80));
        addStudent(new Student(id(nextStudentId++, "S"), "Suresh Iyer", 5.8, "CSE", 2, 0, 65, 70));

        addCompany(new Company(id(nextCompanyId++, "C"), "TechNova Systems", 7.0,
                Company.parseBranches("CSE|IT"), 0, 1, 60, 60));
        addCompany(new Company(id(nextCompanyId++, "C"), "OpenGrid Analytics", 6.5,
                Company.parseBranches("ANY"), 1, 2, 60, 60));
        addCompany(new Company(id(nextCompanyId++, "C"), "Meridian Core", 8.0,
                Company.parseBranches("CSE"), 0, 0, 85, 85));
    }

    // ---------- persistence: data survives a server restart ----------
    static final String STUDENTS_FILE = "data/students_store.csv";
    static final String COMPANIES_FILE = "data/companies_store.csv";

    static void loadPersistedOrSeed() throws IOException {
        boolean loadedStudents = loadStudentsFromDisk();
        boolean loadedCompanies = loadCompaniesFromDisk();
        if (!loadedStudents || !loadedCompanies) {
            loadSeedData();
            saveStudentsToDisk();
            saveCompaniesToDisk();
        }
    }

    static boolean loadStudentsFromDisk() {
        Path file = Path.of(STUDENTS_FILE);
        if (!Files.exists(file)) return false;
        try {
            List<String> lines = Files.readAllLines(file);
            int loaded = 0;
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] c = line.split(",", -1);
                if (c.length < 8) continue;
                Student s = new Student(c[0], c[1], Double.parseDouble(c[2]), c[3],
                        Integer.parseInt(c[4]), Double.parseDouble(c[5]),
                        Double.parseDouble(c[6]), Double.parseDouble(c[7]));
                students.put(s.id, s);
                int num = Integer.parseInt(s.id.substring(1));
                if (num >= nextStudentId) nextStudentId = num + 1;
                loaded++;
            }
            return loaded > 0;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean loadCompaniesFromDisk() {
        Path file = Path.of(COMPANIES_FILE);
        if (!Files.exists(file)) return false;
        try {
            List<String> lines = Files.readAllLines(file);
            int loaded = 0;
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] c = line.split(",", -1);
                if (c.length < 8) continue;
                Company co = new Company(c[0], c[1], Double.parseDouble(c[2]),
                        Company.parseBranches(c[3].replace("~", "|")),
                        Integer.parseInt(c[4]), Double.parseDouble(c[5]),
                        Double.parseDouble(c[6]), Double.parseDouble(c[7]));
                companies.put(co.id, co);
                int num = Integer.parseInt(co.id.substring(1));
                if (num >= nextCompanyId) nextCompanyId = num + 1;
                loaded++;
            }
            return loaded > 0;
        } catch (Exception e) {
            return false;
        }
    }

    static void saveStudentsToDisk() {
        try {
            List<String> lines = new ArrayList<>();
            for (Student s : students.values()) {
                lines.add(String.join(",", s.id, s.name, String.valueOf(s.cgpa), s.branch,
                        String.valueOf(s.activeBacklogs), String.valueOf(s.gapYears),
                        String.valueOf(s.tenthPercent), String.valueOf(s.twelfthPercent)));
            }
            Files.write(Path.of(STUDENTS_FILE), lines);
        } catch (IOException ignored) { }
    }

    static void saveCompaniesToDisk() {
        try {
            List<String> lines = new ArrayList<>();
            for (Company c : companies.values()) {
                // "~" separates branches in the persisted file so commas stay the CSV delimiter
                String branches = String.join("~", c.allowedBranches);
                lines.add(String.join(",", c.id, c.name, String.valueOf(c.minCgpa), branches,
                        String.valueOf(c.maxBacklogs), String.valueOf(c.maxGapYears),
                        String.valueOf(c.minTenthPercent), String.valueOf(c.minTwelfthPercent)));
            }
            Files.write(Path.of(COMPANIES_FILE), lines);
        } catch (IOException ignored) { }
    }

    static void addStudent(Student s) { students.put(s.id, s); }
    static void addCompany(Company c) { companies.put(c.id, c); }
    static String id(int n, String prefix) { return prefix + n; }

    // ---------- auth handlers ----------
    static void handleLogin(HttpExchange ex) throws IOException {
        cors(ex);
        if (!ex.getRequestMethod().equals("POST")) {
            sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        String username = body.getOrDefault("username", "");
        String password = body.getOrDefault("password", "");
        if (username.equals(ADMIN_USERNAME) && password.equals(ADMIN_PASSWORD)) {
            String token = UUID.randomUUID().toString();
            sessions.put(token, System.currentTimeMillis() + SESSION_DURATION_MS);
            ex.getResponseHeaders().add("Set-Cookie",
                    "session=" + token + "; Path=/; HttpOnly; Max-Age=7200; SameSite=Lax");
            sendJson(ex, 200, "{\"ok\":true}");
        } else {
            sendJson(ex, 401, "{\"ok\":false,\"error\":\"Invalid username or password\"}");
        }
    }

    static void handleLogout(HttpExchange ex) throws IOException {
        cors(ex);
        String token = getSessionToken(ex);
        if (token != null) sessions.remove(token);
        ex.getResponseHeaders().add("Set-Cookie", "session=; Path=/; HttpOnly; Max-Age=0");
        sendJson(ex, 200, "{\"ok\":true}");
    }

    static void handleSessionCheck(HttpExchange ex) throws IOException {
        cors(ex);
        sendJson(ex, 200, "{\"authenticated\":" + isAuthenticated(ex) + "}");
    }

    static String getSessionToken(HttpExchange ex) {
        String cookieHeader = ex.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("session")) return kv[1];
        }
        return null;
    }

    static boolean isAuthenticated(HttpExchange ex) {
        String token = getSessionToken(ex);
        if (token == null) return false;
        Long expiry = sessions.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    /** Returns true and writes a 401 response if the request is not authenticated. */
    static boolean requireAuth(HttpExchange ex) throws IOException {
        if (!isAuthenticated(ex)) {
            sendJson(ex, 401, "{\"error\":\"Please log in to make changes\"}");
            return true;
        }
        return false;
    }

    // ---------- /api/students  (also /api/students?id=X for PUT/DELETE) ----------
    static void handleStudents(HttpExchange ex) throws IOException {
        cors(ex);
        String method = ex.getRequestMethod();
        if (method.equals("GET")) {
            String json = "[" + students.values().stream()
                    .map(Student::toJson).collect(Collectors.joining(",")) + "]";
            sendJson(ex, 200, json);
        } else if (method.equals("POST")) {
            if (requireAuth(ex)) return;
            Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
            try {
                Student s = new Student(
                        id(nextStudentId++, "S"),
                        body.getOrDefault("name", "Unnamed"),
                        Double.parseDouble(body.getOrDefault("cgpa", "0")),
                        body.getOrDefault("branch", "ANY"),
                        Integer.parseInt(body.getOrDefault("activeBacklogs", "0")),
                        Double.parseDouble(body.getOrDefault("gapYears", "0")),
                        Double.parseDouble(body.getOrDefault("tenthPercent", "0")),
                        Double.parseDouble(body.getOrDefault("twelfthPercent", "0"))
                );
                addStudent(s);
                saveStudentsToDisk();
                logActivity("➕", "Added student " + s.name + " (" + s.branch + ")");
                sendJson(ex, 201, s.toJson());
            } catch (Exception e) {
                sendJson(ex, 400, "{\"error\":\"" + JsonUtil.esc(e.getMessage()) + "\"}");
            }
        } else if (method.equals("PUT")) {
            if (requireAuth(ex)) return;
            String id = queryParams(ex).get("id");
            if (id == null || !students.containsKey(id)) {
                sendJson(ex, 404, "{\"error\":\"student not found\"}");
                return;
            }
            Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
            try {
                Student existing = students.get(id);
                Student updated = new Student(
                        id,
                        body.getOrDefault("name", existing.name),
                        Double.parseDouble(body.getOrDefault("cgpa", String.valueOf(existing.cgpa))),
                        body.getOrDefault("branch", existing.branch),
                        Integer.parseInt(body.getOrDefault("activeBacklogs", String.valueOf(existing.activeBacklogs))),
                        Double.parseDouble(body.getOrDefault("gapYears", String.valueOf(existing.gapYears))),
                        Double.parseDouble(body.getOrDefault("tenthPercent", String.valueOf(existing.tenthPercent))),
                        Double.parseDouble(body.getOrDefault("twelfthPercent", String.valueOf(existing.twelfthPercent)))
                );
                students.put(id, updated);
                saveStudentsToDisk();
                logActivity("✏️", "Updated student " + updated.name);
                sendJson(ex, 200, updated.toJson());
            } catch (Exception e) {
                sendJson(ex, 400, "{\"error\":\"" + JsonUtil.esc(e.getMessage()) + "\"}");
            }
        } else if (method.equals("DELETE")) {
            if (requireAuth(ex)) return;
            String id = queryParams(ex).get("id");
            if (id == null || !students.containsKey(id)) {
                sendJson(ex, 404, "{\"error\":\"student not found\"}");
                return;
            }
            String deletedName = students.get(id).name;
            students.remove(id);
            saveStudentsToDisk();
            logActivity("🗑️", "Deleted student " + deletedName);
            sendJson(ex, 200, "{\"ok\":true}");
        } else {
            sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
        }
    }

    // ---------- /api/companies  (also /api/companies?id=X for PUT/DELETE) ----------
    static void handleCompanies(HttpExchange ex) throws IOException {
        cors(ex);
        String method = ex.getRequestMethod();
        if (method.equals("GET")) {
            String json = "[" + companies.values().stream()
                    .map(Company::toJson).collect(Collectors.joining(",")) + "]";
            sendJson(ex, 200, json);
        } else if (method.equals("POST")) {
            if (requireAuth(ex)) return;
            Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
            try {
                Company c = new Company(
                        id(nextCompanyId++, "C"),
                        body.getOrDefault("name", "Unnamed Co"),
                        Double.parseDouble(body.getOrDefault("minCgpa", "0")),
                        Company.parseBranches(body.getOrDefault("allowedBranches", "ANY")),
                        Integer.parseInt(body.getOrDefault("maxBacklogs", "0")),
                        Double.parseDouble(body.getOrDefault("maxGapYears", "0")),
                        Double.parseDouble(body.getOrDefault("minTenthPercent", "0")),
                        Double.parseDouble(body.getOrDefault("minTwelfthPercent", "0"))
                );
                addCompany(c);
                saveCompaniesToDisk();
                logActivity("🏢", "Added company " + c.name);
                sendJson(ex, 201, c.toJson());
            } catch (Exception e) {
                sendJson(ex, 400, "{\"error\":\"" + JsonUtil.esc(e.getMessage()) + "\"}");
            }
        } else if (method.equals("PUT")) {
            if (requireAuth(ex)) return;
            String id = queryParams(ex).get("id");
            if (id == null || !companies.containsKey(id)) {
                sendJson(ex, 404, "{\"error\":\"company not found\"}");
                return;
            }
            Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
            try {
                Company existing = companies.get(id);
                Company updated = new Company(
                        id,
                        body.getOrDefault("name", existing.name),
                        Double.parseDouble(body.getOrDefault("minCgpa", String.valueOf(existing.minCgpa))),
                        body.containsKey("allowedBranches")
                                ? Company.parseBranches(body.get("allowedBranches"))
                                : existing.allowedBranches,
                        Integer.parseInt(body.getOrDefault("maxBacklogs", String.valueOf(existing.maxBacklogs))),
                        Double.parseDouble(body.getOrDefault("maxGapYears", String.valueOf(existing.maxGapYears))),
                        Double.parseDouble(body.getOrDefault("minTenthPercent", String.valueOf(existing.minTenthPercent))),
                        Double.parseDouble(body.getOrDefault("minTwelfthPercent", String.valueOf(existing.minTwelfthPercent)))
                );
                companies.put(id, updated);
                saveCompaniesToDisk();
                logActivity("✏️", "Updated company " + updated.name);
                sendJson(ex, 200, updated.toJson());
            } catch (Exception e) {
                sendJson(ex, 400, "{\"error\":\"" + JsonUtil.esc(e.getMessage()) + "\"}");
            }
        } else if (method.equals("DELETE")) {
            if (requireAuth(ex)) return;
            String id = queryParams(ex).get("id");
            if (id == null || !companies.containsKey(id)) {
                sendJson(ex, 404, "{\"error\":\"company not found\"}");
                return;
            }
            String deletedCompanyName = companies.get(id).name;
            companies.remove(id);
            saveCompaniesToDisk();
            logActivity("🗑️", "Deleted company " + deletedCompanyName);
            sendJson(ex, 200, "{\"ok\":true}");
        } else {
            sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
        }
    }

    // ---------- /api/import/students  (CSV body) ----------
    // Expected columns: name,cgpa,branch,activeBacklogs,gapYears,tenthPercent,twelfthPercent
    static void handleImportStudents(HttpExchange ex) throws IOException {
        cors(ex);
        if (!ex.getRequestMethod().equals("POST")) {
            sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        if (requireAuth(ex)) return;
        String csv = readBody(ex);
        List<Student> added = new ArrayList<>();
        String[] lines = csv.split("\\r?\\n");
        int start = 0;
        if (lines.length > 0 && lines[0].toLowerCase().contains("name")) start = 1; // skip header
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",");
            if (cols.length < 7) continue;
            try {
                Student s = new Student(
                        id(nextStudentId++, "S"),
                        cols[0].trim(),
                        Double.parseDouble(cols[1].trim()),
                        cols[2].trim(),
                        Integer.parseInt(cols[3].trim()),
                        Double.parseDouble(cols[4].trim()),
                        Double.parseDouble(cols[5].trim()),
                        Double.parseDouble(cols[6].trim())
                );
                addStudent(s);
                added.add(s);
            } catch (Exception ignored) { }
        }
        String json = "[" + added.stream().map(Student::toJson).collect(Collectors.joining(",")) + "]";
        if (!added.isEmpty()) {
            saveStudentsToDisk();
            logActivity("📥", "Bulk-imported " + added.size() + " student(s) from CSV");
        }
        sendJson(ex, 201, json);
    }

    // ---------- /api/check?studentId=&companyId= ----------
    static void handleCheckOne(HttpExchange ex) throws IOException {
        cors(ex);
        Map<String, String> qp = queryParams(ex);
        Student s = students.get(qp.get("studentId"));
        Company c = companies.get(qp.get("companyId"));
        if (s == null || c == null) {
            sendJson(ex, 404, "{\"error\":\"student or company not found\"}");
            return;
        }
        EligibilityResult r = engine.evaluate(s, c);
        sendJson(ex, 200, r.toJson());
    }

    // ---------- /api/check-all  -> full matrix ----------
    static void handleCheckAll(HttpExchange ex) throws IOException {
        cors(ex);
        List<String> results = new ArrayList<>();
        for (Student s : students.values()) {
            for (Company c : companies.values()) {
                results.add(engine.evaluate(s, c).toJson());
            }
        }
        logActivity("✓", "Ran eligibility check across " + students.size() + " student(s) and " + companies.size() + " compan(y/ies)");
        sendJson(ex, 200, "[" + String.join(",", results) + "]");
    }

    // ---------- /api/export/eligible  -> CSV download of all eligible matches ----------
    static void handleExportEligibleCsv(HttpExchange ex) throws IOException {
        cors(ex);
        StringBuilder csv = new StringBuilder("Student,Branch,CGPA,Company\n");
        for (Student s : students.values()) {
            for (Company c : companies.values()) {
                EligibilityResult r = engine.evaluate(s, c);
                if (r.eligible) {
                    csv.append(csvField(s.name)).append(",")
                       .append(csvField(s.branch)).append(",")
                       .append(s.cgpa).append(",")
                       .append(csvField(c.name)).append("\n");
                }
            }
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"eligible_students.csv\"");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ---------- /api/dashboard-stats  -> aggregates for charts, computed silently ----------
    static void handleDashboardStats(HttpExchange ex) throws IOException {
        cors(ex);
        Map<String, Integer> branchCounts = new TreeMap<>();
        for (Student s : students.values()) {
            branchCounts.merge(s.branch, 1, Integer::sum);
        }
        Map<String, Integer> companyEligibleCounts = new LinkedHashMap<>();
        int totalEligible = 0;
        for (Company c : companies.values()) {
            int count = 0;
            for (Student s : students.values()) {
                if (engine.evaluate(s, c).eligible) count++;
            }
            companyEligibleCounts.put(c.name, count);
            totalEligible += count;
        }
        double avgCgpa = students.isEmpty() ? 0 :
                students.values().stream().mapToDouble(s -> s.cgpa).average().orElse(0);
        int totalPairs = students.size() * companies.size();
        double eligiblePercent = totalPairs == 0 ? 0 : (100.0 * totalEligible / totalPairs);

        StringBuilder branchJson = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, Integer> e : branchCounts.entrySet()) {
            if (!first) branchJson.append(",");
            branchJson.append("{\"branch\":\"").append(JsonUtil.esc(e.getKey())).append("\",\"count\":").append(e.getValue()).append("}");
            first = false;
        }
        branchJson.append("]");

        StringBuilder companyJson = new StringBuilder("[");
        first = true;
        for (Map.Entry<String, Integer> e : companyEligibleCounts.entrySet()) {
            if (!first) companyJson.append(",");
            companyJson.append("{\"company\":\"").append(JsonUtil.esc(e.getKey())).append("\",\"count\":").append(e.getValue()).append("}");
            first = false;
        }
        companyJson.append("]");

        String json = "{"
                + "\"totalStudents\":" + students.size() + ","
                + "\"totalCompanies\":" + companies.size() + ","
                + "\"totalEligible\":" + totalEligible + ","
                + "\"avgCgpa\":" + String.format("%.2f", avgCgpa) + ","
                + "\"eligiblePercent\":" + String.format("%.1f", eligiblePercent) + ","
                + "\"branchDistribution\":" + branchJson + ","
                + "\"companyEligibleCounts\":" + companyJson
                + "}";
        sendJson(ex, 200, json);
    }

    // ---------- /api/activity  -> recent activity feed ----------
    static void handleActivity(HttpExchange ex) throws IOException {
        cors(ex);
        sendJson(ex, 200, "[" + String.join(",", activityLog) + "]");
    }

    static String csvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ---------- static file serving for the frontend ----------
    static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = Path.of("web" + path);
        if (!Files.exists(file)) {
            sendJson(ex, 404, "{\"error\":\"not found\"}");
            return;
        }
        byte[] bytes = Files.readAllBytes(file);
        String contentType = path.endsWith(".html") ? "text/html"
                : path.endsWith(".css") ? "text/css"
                : path.endsWith(".js") ? "application/javascript"
                : "application/octet-stream";
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ---------- helpers ----------
    static void cors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static Map<String, String> queryParams(HttpExchange ex) {
        Map<String, String> result = new HashMap<>();
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return result;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) continue;
            try {
                String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                result.put(key, value);
            } catch (Exception ignored) { }
        }
        return result;
    }
}
