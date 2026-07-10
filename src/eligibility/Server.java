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
import java.util.stream.Collectors;

public class Server {

    static Map<String, Student> students = new ConcurrentHashMap<>();
    static Map<String, Company> companies = new ConcurrentHashMap<>();
    static EligibilityEngine engine = new EligibilityEngine();
    static int nextStudentId = 1;
    static int nextCompanyId = 1;

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

        server.createContext("/api/students", Server::handleStudents);
        server.createContext("/api/companies", Server::handleCompanies);
        server.createContext("/api/import/students", Server::handleImportStudents);
        server.createContext("/api/check-all", Server::handleCheckAll);
        server.createContext("/api/check", Server::handleCheckOne);
        server.createContext("/api/login", Server::handleLogin);
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

    // ---------- /api/students ----------
    static void handleStudents(HttpExchange ex) throws IOException {
        cors(ex);
        String method = ex.getRequestMethod();
        if (method.equals("GET")) {
            String json = "[" + students.values().stream()
                    .map(Student::toJson).collect(Collectors.joining(",")) + "]";
            sendJson(ex, 200, json);
        } else if (method.equals("POST")) {
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
                sendJson(ex, 201, s.toJson());
            } catch (Exception e) {
                sendJson(ex, 400, "{\"error\":\"" + JsonUtil.esc(e.getMessage()) + "\"}");
            }
        } else {
            sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
        }
    }

    // ---------- /api/companies ----------
    static void handleCompanies(HttpExchange ex) throws IOException {
        cors(ex);
        String method = ex.getRequestMethod();
        if (method.equals("GET")) {
            String json = "[" + companies.values().stream()
                    .map(Company::toJson).collect(Collectors.joining(",")) + "]";
            sendJson(ex, 200, json);
        } else if (method.equals("POST")) {
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
                sendJson(ex, 201, c.toJson());
            } catch (Exception e) {
                sendJson(ex, 400, "{\"error\":\"" + JsonUtil.esc(e.getMessage()) + "\"}");
            }
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
        if (!added.isEmpty()) saveStudentsToDisk();
        sendJson(ex, 201, json);
    }

    // ---------- /api/login ----------
    // NOTE: credentials are hardcoded here for simplicity. For anything beyond
    // a personal/demo project, replace this with a real user store + hashed passwords.
    static final String ADMIN_USER = "admin";
    static final String ADMIN_PASS = "admin123";

    static void handleLogin(HttpExchange ex) throws IOException {
        cors(ex);
        if (!ex.getRequestMethod().equals("POST")) {
            sendJson(ex, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        Map<String, String> body = JsonUtil.parseFlatObject(readBody(ex));
        String user = body.getOrDefault("username", "");
        String pass = body.getOrDefault("password", "");

        if (ADMIN_USER.equals(user) && ADMIN_PASS.equals(pass)) {
            sendJson(ex, 200, "{\"ok\":true}");
        } else {
            sendJson(ex, 200, "{\"ok\":false,\"error\":\"Invalid username or password\"}");
        }
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
        sendJson(ex, 200, "[" + String.join(",", results) + "]");
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
        Map<String, String> result = new
