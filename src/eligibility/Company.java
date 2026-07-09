package eligibility;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Company {
    public String id;
    public String name;
    public double minCgpa;
    public List<String> allowedBranches; // ["ANY"] means no restriction
    public int maxBacklogs;
    public double maxGapYears;
    public double minTenthPercent;
    public double minTwelfthPercent;

    public Company(String id, String name, double minCgpa, List<String> allowedBranches,
                   int maxBacklogs, double maxGapYears,
                   double minTenthPercent, double minTwelfthPercent) {
        this.id = id;
        this.name = name;
        this.minCgpa = minCgpa;
        this.allowedBranches = allowedBranches;
        this.maxBacklogs = maxBacklogs;
        this.maxGapYears = maxGapYears;
        this.minTenthPercent = minTenthPercent;
        this.minTwelfthPercent = minTwelfthPercent;
    }

    public static List<String> parseBranches(String raw) {
        if (raw == null || raw.trim().isEmpty() || raw.trim().equalsIgnoreCase("ANY")) {
            return Arrays.asList("ANY");
        }
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public String toJson() {
        String branchesJson = allowedBranches.stream()
                .map(b -> "\"" + JsonUtil.esc(b) + "\"")
                .collect(Collectors.joining(","));
        return "{"
            + "\"id\":\"" + JsonUtil.esc(id) + "\","
            + "\"name\":\"" + JsonUtil.esc(name) + "\","
            + "\"minCgpa\":" + minCgpa + ","
            + "\"allowedBranches\":[" + branchesJson + "],"
            + "\"maxBacklogs\":" + maxBacklogs + ","
            + "\"maxGapYears\":" + maxGapYears + ","
            + "\"minTenthPercent\":" + minTenthPercent + ","
            + "\"minTwelfthPercent\":" + minTwelfthPercent
            + "}";
    }
}
