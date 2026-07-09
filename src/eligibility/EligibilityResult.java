package eligibility;

import java.util.List;
import java.util.stream.Collectors;

public class EligibilityResult {
    public String studentId;
    public String studentName;
    public String companyId;
    public String companyName;
    public boolean eligible;
    public List<String> reasons; // failure reasons; empty if eligible

    public EligibilityResult(String studentId, String studentName, String companyId,
                              String companyName, boolean eligible, List<String> reasons) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.companyId = companyId;
        this.companyName = companyName;
        this.eligible = eligible;
        this.reasons = reasons;
    }

    public String toJson() {
        String reasonsJson = reasons.stream()
                .map(r -> "\"" + JsonUtil.esc(r) + "\"")
                .collect(Collectors.joining(","));
        return "{"
            + "\"studentId\":\"" + JsonUtil.esc(studentId) + "\","
            + "\"studentName\":\"" + JsonUtil.esc(studentName) + "\","
            + "\"companyId\":\"" + JsonUtil.esc(companyId) + "\","
            + "\"companyName\":\"" + JsonUtil.esc(companyName) + "\","
            + "\"eligible\":" + eligible + ","
            + "\"reasons\":[" + reasonsJson + "]"
            + "}";
    }
}
