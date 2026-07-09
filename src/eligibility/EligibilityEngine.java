package eligibility;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule-driven eligibility engine.
 *
 * Each criterion is evaluated independently and produces a human-readable
 * reason when it fails. This is deliberately NOT a single tangled if/else -
 * new rules can be added as new checkX() methods without touching existing
 * ones, and every rejection is explainable to the student.
 */
public class EligibilityEngine {

    public EligibilityResult evaluate(Student s, Company c) {
        List<String> reasons = new ArrayList<>();

        checkCgpa(s, c, reasons);
        checkBranch(s, c, reasons);
        checkBacklogs(s, c, reasons);
        checkGapYears(s, c, reasons);
        checkTenth(s, c, reasons);
        checkTwelfth(s, c, reasons);

        boolean eligible = reasons.isEmpty();
        return new EligibilityResult(s.id, s.name, c.id, c.name, eligible, reasons);
    }

    private void checkCgpa(Student s, Company c, List<String> reasons) {
        if (s.cgpa < c.minCgpa) {
            reasons.add(String.format("CGPA %.2f is below required %.2f", s.cgpa, c.minCgpa));
        }
    }

    private void checkBranch(Student s, Company c, List<String> reasons) {
        boolean anyBranch = c.allowedBranches.size() == 1
                && c.allowedBranches.get(0).equalsIgnoreCase("ANY");
        if (!anyBranch) {
            boolean match = c.allowedBranches.stream()
                    .anyMatch(b -> b.equalsIgnoreCase(s.branch));
            if (!match) {
                reasons.add("Branch '" + s.branch + "' is not in allowed list: "
                        + String.join(", ", c.allowedBranches));
            }
        }
    }

    private void checkBacklogs(Student s, Company c, List<String> reasons) {
        if (s.activeBacklogs > c.maxBacklogs) {
            reasons.add("Has " + s.activeBacklogs + " active backlog(s), max allowed is "
                    + c.maxBacklogs);
        }
    }

    private void checkGapYears(Student s, Company c, List<String> reasons) {
        if (s.gapYears > c.maxGapYears) {
            reasons.add(String.format("Gap of %.1f year(s) exceeds max allowed %.1f",
                    s.gapYears, c.maxGapYears));
        }
    }

    private void checkTenth(Student s, Company c, List<String> reasons) {
        if (s.tenthPercent < c.minTenthPercent) {
            reasons.add(String.format("10th %% %.1f is below required %.1f",
                    s.tenthPercent, c.minTenthPercent));
        }
    }

    private void checkTwelfth(Student s, Company c, List<String> reasons) {
        if (s.twelfthPercent < c.minTwelfthPercent) {
            reasons.add(String.format("12th %% %.1f is below required %.1f",
                    s.twelfthPercent, c.minTwelfthPercent));
        }
    }
}
