package eligibility;

public class Student {
    public String id;
    public String name;
    public double cgpa;
    public String branch;
    public int activeBacklogs;
    public double gapYears;
    public double tenthPercent;
    public double twelfthPercent;

    public Student(String id, String name, double cgpa, String branch,
                   int activeBacklogs, double gapYears,
                   double tenthPercent, double twelfthPercent) {
        this.id = id;
        this.name = name;
        this.cgpa = cgpa;
        this.branch = branch;
        this.activeBacklogs = activeBacklogs;
        this.gapYears = gapYears;
        this.tenthPercent = tenthPercent;
        this.twelfthPercent = twelfthPercent;
    }

    public String toJson() {
        return "{"
            + "\"id\":\"" + JsonUtil.esc(id) + "\","
            + "\"name\":\"" + JsonUtil.esc(name) + "\","
            + "\"cgpa\":" + cgpa + ","
            + "\"branch\":\"" + JsonUtil.esc(branch) + "\","
            + "\"activeBacklogs\":" + activeBacklogs + ","
            + "\"gapYears\":" + gapYears + ","
            + "\"tenthPercent\":" + tenthPercent + ","
            + "\"twelfthPercent\":" + twelfthPercent
            + "}";
    }
}
