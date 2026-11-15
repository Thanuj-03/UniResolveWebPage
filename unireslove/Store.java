package unireslove;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Data layer. Replaces localStorage: everything lives in memory and is saved to the
 * "data" folder as simple tab-separated text files after every change.
 */
final class Store {
    static final Object LOCK = new Object();
    /** Project folder = the folder that contains "public". Works even if VS Code runs from src/unireslove. */
    static final Path ROOT = findRoot();
    static final Path DIR = ROOT.resolve("data");
    static final Path UPLOADS = DIR.resolve("uploads");

    static final String[][] CATEGORIES = {
        {"Plumber", "Plumber"}, {"Electrician", "Electrician"}, {"Wi-Fi", "Wi-Fi / Internet"},
        {"Carpenter", "Carpenter"}, {"AC", "Air Conditioner (AC)"}
    };

    static final List<Student> students = new ArrayList<>();
    static final List<Worker> workers = new ArrayList<>();
    static final List<Complaint> complaints = new ArrayList<>();   // newest first
    static Admin admin = new Admin();

    private static Path findRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++, dir = dir.getParent())
            if (Files.isDirectory(dir.resolve("public"))) return dir;
        return Paths.get("").toAbsolutePath();
    }

    static String categoryLabel(String value) {
        for (String[] c : CATEGORIES) if (c[0].equals(value)) return c[1];
        return value;
    }

    // ---------- lookups ----------
    static Student findStudent(String studentId) {
        for (Student s : students) if (s.studentId.equalsIgnoreCase(studentId)) return s;
        return null;
    }
    static Worker findWorker(String workerId) {
        for (Worker w : workers) if (w.workerId.equalsIgnoreCase(workerId)) return w;
        return null;
    }
    static Complaint findComplaint(String id) {
        for (Complaint c : complaints) if (c.id.equals(id)) return c;
        return null;
    }

    // ---------- persistence ----------
    private static String enc(String v) {
        return v.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "");
    }
    private static String dec(String v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch == '\\' && i + 1 < v.length()) {
                char n = v.charAt(++i);
                sb.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else sb.append(ch);
        }
        return sb.toString();
    }
    private static String line(String... f) {
        StringJoiner j = new StringJoiner("\t");
        for (String x : f) j.add(enc(x == null ? "" : x));
        return j.toString();
    }
    private static List<String[]> read(String file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        Path p = DIR.resolve(file);
        if (!Files.exists(p)) return rows;
        for (String l : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            if (l.isBlank()) continue;
            String[] raw = l.split("\t", -1);
            for (int i = 0; i < raw.length; i++) raw[i] = dec(raw[i]);
            rows.add(raw);
        }
        return rows;
    }
    private static String at(String[] r, int i) { return i < r.length ? r[i] : ""; }
    private static int num(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }

    static void load() throws IOException {
        Files.createDirectories(UPLOADS);
        for (String[] r : read("students.tsv")) {
            Student s = new Student();
            s.name = at(r, 0); s.studentId = at(r, 1); s.email = at(r, 2); s.password = at(r, 3);
            s.hostel = at(r, 4); s.room = at(r, 5); s.contact = at(r, 6);
            students.add(s);
        }
        for (String[] r : read("workers.tsv")) {
            Worker w = new Worker();
            w.name = at(r, 0); w.workerId = at(r, 1); w.password = at(r, 2);
            w.category = at(r, 3); w.contact = at(r, 4);
            workers.add(w);
        }
        for (String[] r : read("complaints.tsv")) {
            Complaint c = new Complaint();
            c.id = at(r, 0); c.studentId = at(r, 1); c.studentName = at(r, 2); c.hostel = at(r, 3);
            c.room = at(r, 4); c.contact = at(r, 5); c.category = at(r, 6); c.location = at(r, 7);
            c.description = at(r, 8); c.photo = at(r, 9); c.status = at(r, 10); c.workerName = at(r, 11);
            c.workerId = at(r, 12); c.date = at(r, 13); c.rating = num(at(r, 14)); c.review = at(r, 15);
            c.completionPhoto = at(r, 16); c.workerRating = num(at(r, 17)); c.workerReview = at(r, 18);
            c.completedDate = at(r, 19);
            complaints.add(c);
        }
        for (String[] r : read("admin.tsv")) {
            admin.name = at(r, 0); admin.adminId = at(r, 1); admin.password = at(r, 2);
        }
    }

    static void save() {
        try {
            Files.createDirectories(UPLOADS);
            List<String> out = new ArrayList<>();
            for (Student s : students) out.add(line(s.name, s.studentId, s.email, s.password, s.hostel, s.room, s.contact));
            Files.write(DIR.resolve("students.tsv"), out, StandardCharsets.UTF_8);

            out = new ArrayList<>();
            for (Worker w : workers) out.add(line(w.name, w.workerId, w.password, w.category, w.contact));
            Files.write(DIR.resolve("workers.tsv"), out, StandardCharsets.UTF_8);

            out = new ArrayList<>();
            for (Complaint c : complaints) out.add(line(c.id, c.studentId, c.studentName, c.hostel, c.room, c.contact,
                c.category, c.location, c.description, c.photo, c.status, c.workerName, c.workerId, c.date,
                String.valueOf(c.rating), c.review, c.completionPhoto, String.valueOf(c.workerRating),
                c.workerReview, c.completedDate));
            Files.write(DIR.resolve("complaints.tsv"), out, StandardCharsets.UTF_8);

            Files.write(DIR.resolve("admin.tsv"),
                List.of(line(admin.name, admin.adminId, admin.password)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Could not save data: " + e.getMessage(), e);
        }
    }
}
