package unireslove;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import unireslove.Web.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/** uniReslove - campus complaint platform, written fully in Java (no JavaScript). */
public class Main {

    static final Pattern TEN_DIGITS = Pattern.compile("[0-9]{10}");
    static final Pattern WORKER_ID = Pattern.compile("[A-Za-z0-9_-]+");
    static final Path PUBLIC = Store.ROOT.resolve("public");

    public static void main(String[] args) throws Exception {
        Store.load();
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = null;
        for (int i = 0; i < 10 && server == null; i++) {          // if the port is busy, try the next one
            try {
                server = HttpServer.create(new InetSocketAddress(port + i), 0);
                port += i;
            } catch (java.net.BindException e) {
                System.out.println("Port " + (port + i) + " is busy, trying " + (port + i + 1) + " ...");
            }
        }
        if (server == null) throw new IllegalStateException("No free port found. Close the old uniReslove server and try again.");
        server.createContext("/", Main::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("uniReslove is running:  http://localhost:" + port);
        System.out.println("Default admin login:    admin / admin123");
    }

    // ------------------------------------------------------------ dispatcher
    static void handle(HttpExchange ex) {
        Req req = null;
        try {
            req = new Req(ex);
            Resp resp;
            synchronized (Store.LOCK) {
                resp = route(req);
            }
            resp.send(ex, req);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                Resp r = Resp.html("<h1>Something went wrong</h1><p>" + View.esc(String.valueOf(e.getMessage()))
                    + "</p><p><a href=\"/\">Back to uniReslove</a></p>");
                r.status = 500;
                r.send(ex, req);
            } catch (IOException ignored) { }
        }
    }

    static Resp route(Req r) throws IOException {
        String p = r.path;
        boolean get = r.method.equals("GET"), post = r.method.equals("POST");

        if (get && p.equals("/")) return Resp.html(View.rolePage());
        if (get && (p.equals("/style.css") || p.equals("/extra.css"))) return staticFile(PUBLIC.resolve(p.substring(1)), "text/css; charset=UTF-8");
        if (get && p.startsWith("/uploads/")) return upload(p.substring(9));

        if (p.startsWith("/student")) return student(r, p, get, post);
        if (p.startsWith("/admin")) return admin(r, p, get, post);
        if (p.startsWith("/worker")) return worker(r, p, get, post);
        return Resp.notFound();
    }

    // ------------------------------------------------------------ static + uploads
    static Resp staticFile(Path file, String type) throws IOException {
        if (!Files.exists(file)) return Resp.notFound();
        return Resp.bytes(Files.readAllBytes(file), type);
    }

    static Resp upload(String name) throws IOException {
        if (!name.matches("[A-Za-z0-9._-]+")) return Resp.notFound();
        Path f = Store.UPLOADS.resolve(name);
        String type = name.endsWith(".png") ? "image/png" : "image/jpeg";
        return staticFile(f, type);
    }

    /** Validates and stores an uploaded image. Returns the saved file name, "" for no file, or null (and sets error[0]). */
    static String saveImage(FilePart part, String[] error) throws IOException {
        if (part == null) return "";
        byte[] d = part.data();
        boolean png = d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
        boolean jpg = d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8;
        if (!png && !jpg) { error[0] = "Please select a valid image file."; return null; }
        if (d.length > 2 * 1024 * 1024) { error[0] = "Image must be smaller than 2 MB."; return null; }
        String name = Web.token() + (png ? ".png" : ".jpg");
        Files.write(Store.UPLOADS.resolve(name), d);
        return name;
    }

    static String today() { return LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")); }

    // ============================================================== STUDENT
    static Student matchStudent(String value) {
        String typed = value.trim(), lower = typed.toLowerCase();
        for (Student s : Store.students)
            if (s.studentId.toLowerCase().equals(lower) || s.email.toLowerCase().equals(lower) || s.contact.equals(typed)) return s;
        return null;
    }

    static Student currentStudent(Req r) {
        Session s = r.session;
        return s.role.equals("student") ? Store.findStudent(s.userId) : null;
    }

    static Resp student(Req r, String p, boolean get, boolean post) throws IOException {
        Session s = r.session;

        // ---- public pages
        if (p.equals("/student/login")) {
            if (get) {
                String[] f = s.takeFlash();
                return Resp.html(View.studentLogin(f[0], f[1], r.q("id")));
            }
            if (post) {
                String id = r.f("identifier");
                Student st = matchStudent(id);
                if (st != null && st.password.equals(r.raw("password"))) {
                    s.role = "student"; s.userId = st.studentId;
                    return Resp.redirect("/student");
                }
                return Resp.html(View.studentLogin("Invalid ID / Email / Contact Number or Password.", "error", id));
            }
        }
        if (p.equals("/student/register")) {
            if (get) return Resp.html(View.studentRegister("", Map.of()));
            if (post) return registerStudent(r);
        }
        if (p.equals("/student/forgot") && get) {
            s.otp = ""; s.otpStudentId = "";
            return Resp.html(View.forgotStep1("", r.q("id")));
        }
        if (p.equals("/student/forgot") && post) {
            String id = r.f("identifier");
            Student st = matchStudent(id);
            if (st == null) return Resp.html(View.forgotStep1("No account found with these details.", id));
            s.otpStudentId = st.studentId;
            s.otpMethod = r.f("method").equals("email") ? "email" : "contact";
            return Resp.html(newOtp(s, st, "", "success"));
        }
        if (p.equals("/student/forgot/resend") && post) {
            Student st = Store.findStudent(s.otpStudentId);
            if (st == null) return Resp.redirect("/student/forgot");
            return Resp.html(newOtp(s, st, "A new OTP has been sent.", "success"));
        }
        if (p.equals("/student/forgot/reset") && post) return resetPassword(r);

        // ---- logged-in pages
        Student me = currentStudent(r);
        if (me == null) return Resp.redirect("/student/login");

        if (p.equals("/student/logout")) { s.logout(); return Resp.redirect("/"); }

        if (p.equals("/student")) {
            String tab = r.q("tab");
            if (!tab.equals("mine") && !tab.equals("solved")) tab = "raise";
            List<Complaint> mine = Store.complaints.stream().filter(c -> c.studentId.equalsIgnoreCase(me.studentId)).toList();
            return Resp.html(View.studentDashboard(me, tab, mine, s.takeFlash()));
        }
        if (p.equals("/student/complaint") && post) return submitComplaint(r, me);

        if (p.equals("/student/review")) {
            Complaint c = Store.findComplaint(get ? r.q("id") : r.f("id"));
            if (c == null || !c.studentId.equalsIgnoreCase(me.studentId) || !c.solved()) return Resp.redirect("/student?tab=solved");
            if (get) return Resp.html(View.reviewPage(c, ""));
            int rating = parseInt(r.f("rating"));
            String review = r.f("review");
            if (rating < 1 || rating > 5) return Resp.html(View.reviewPage(c, "Please select a star rating."));
            if (review.isEmpty()) return Resp.html(View.reviewPage(c, "Please write a short review."));
            if (c.rating == 0) {
                c.rating = rating;
                c.review = review.length() > 500 ? review.substring(0, 500) : review;
                Store.save();
            }
            s.flash("Review submitted successfully!");
            return Resp.redirect("/student?tab=solved");
        }
        if (p.equals("/student/profile")) {
            if (get) return Resp.html(View.studentProfile(me, ""));
            if (post) return updateStudent(r, me);
        }
        return Resp.notFound();
    }

    static Resp registerStudent(Req r) {
        Map<String, String> v = new HashMap<>();
        for (String k : List.of("name", "studentId", "email", "hostel", "room", "contact")) v.put(k, r.f(k));
        String password = r.raw("password");
        String error = null;
        if (v.values().stream().anyMatch(String::isEmpty) || password.isEmpty()) error = "Please fill in all fields.";
        else if (password.length() < 6) error = "Password must contain at least 6 characters.";
        else if (!TEN_DIGITS.matcher(v.get("contact")).matches()) error = "Enter a valid 10-digit contact number.";
        else {
            for (Student st : Store.students) {
                if (st.studentId.equalsIgnoreCase(v.get("studentId"))) { error = "This Student ID is already registered."; break; }
                if (st.email.equalsIgnoreCase(v.get("email"))) { error = "This email is already registered."; break; }
                if (st.contact.equals(v.get("contact"))) { error = "This contact number is already registered."; break; }
            }
        }
        if (error != null) return Resp.html(View.studentRegister(error, v));

        Student st = new Student();
        st.name = v.get("name"); st.studentId = v.get("studentId"); st.email = v.get("email"); st.password = password;
        st.hostel = v.get("hostel"); st.room = v.get("room"); st.contact = v.get("contact");
        Store.students.add(st);
        Store.save();
        r.session.flash("Account created successfully! Please login.");
        return Resp.redirect("/student/login?id=" + Web.url(st.studentId));
    }

    static String mask(String value, String method) {
        if (method.equals("email")) {
            String[] parts = value.split("@", 2);
            return parts[0].substring(0, 1) + "*****@" + (parts.length > 1 ? parts[1] : "");
        }
        return "******" + value.substring(Math.max(0, value.length() - 4));
    }

    static String newOtp(Session s, Student st, String message, String type) {
        s.otp = String.valueOf(100000 + new Random().nextInt(900000));
        s.otpExpiry = System.currentTimeMillis() + 5 * 60 * 1000;
        s.otpAttempts = 0;
        String dest = s.otpMethod.equals("email") ? st.email : st.contact;
        // Demo only: no SMS / e-mail service is connected, so the OTP is shown on the page.
        return View.forgotStep2("We sent a 6-digit OTP to " + mask(dest, s.otpMethod) + ". It is valid for 5 minutes.", s.otp, message, type);
    }

    static Resp resetPassword(Req r) {
        Session s = r.session;
        Student st = Store.findStudent(s.otpStudentId);
        if (st == null || s.otp.isEmpty()) return Resp.redirect("/student/forgot");
        String sent = "We sent a 6-digit OTP to " + mask(s.otpMethod.equals("email") ? st.email : st.contact, s.otpMethod) + ". It is valid for 5 minutes.";
        String otp = r.f("otp"), pw = r.raw("newPassword"), confirm = r.raw("confirmPassword");
        String error = null;
        if (System.currentTimeMillis() > s.otpExpiry) error = "OTP expired. Please click Resend OTP.";
        else if (s.otpAttempts >= 3) error = "Too many wrong attempts. Please click Resend OTP.";
        else if (!otp.equals(s.otp)) { s.otpAttempts++; error = "Incorrect OTP. Please try again."; }
        else if (pw.length() < 6) error = "Password must contain at least 6 characters.";
        else if (!pw.equals(confirm)) error = "Passwords do not match.";
        if (error != null) return Resp.html(View.forgotStep2(sent, s.otp, error, "error"));

        st.password = pw;
        Store.save();
        s.otp = ""; s.otpStudentId = "";
        s.flash("Password changed. Please login with your new password.");
        return Resp.redirect("/student/login?id=" + Web.url(st.studentId));
    }

    static Resp submitComplaint(Req r, Student me) throws IOException {
        Session s = r.session;
        String category = r.f("category"), location = r.f("location"), description = r.f("description");
        boolean validCategory = Arrays.stream(Store.CATEGORIES).anyMatch(c -> c[0].equals(category));
        if (!validCategory || location.isEmpty() || description.isEmpty()) {
            s.flash("Please fill in all required fields.", "error");
            return Resp.redirect("/student?tab=raise");
        }
        String[] err = {""};
        String photo = saveImage(r.files.get("photo"), err);
        if (photo == null) { s.flash(err[0], "error"); return Resp.redirect("/student?tab=raise"); }

        Complaint c = new Complaint();
        String millis = String.valueOf(System.currentTimeMillis());
        c.id = "UR" + millis.substring(millis.length() - 8);
        c.studentId = me.studentId; c.studentName = me.name; c.hostel = me.hostel; c.room = me.room; c.contact = me.contact;
        c.category = category; c.location = location;
        c.description = description.length() > 1000 ? description.substring(0, 1000) : description;
        c.photo = photo; c.status = "Pending"; c.date = today();
        Store.complaints.add(0, c);
        Store.save();
        s.flash("Complaint submitted successfully!");
        return Resp.redirect("/student?tab=mine");
    }

    static Resp updateStudent(Req r, Student me) {
        String name = r.f("name"), email = r.f("email"), hostel = r.f("hostel"), room = r.f("room"), contact = r.f("contact");
        String error = null;
        if (name.isEmpty() || email.isEmpty() || hostel.isEmpty() || room.isEmpty()) error = "Please fill in all fields.";
        else if (!TEN_DIGITS.matcher(contact).matches()) error = "Enter a valid 10-digit contact number.";
        else {
            for (Student o : Store.students) {
                if (o == me) continue;
                if (o.email.equalsIgnoreCase(email)) { error = "This email is already used by another student."; break; }
                if (o.contact.equals(contact)) { error = "This contact number is already used by another student."; break; }
            }
        }
        if (error != null) {
            Student draft = new Student();
            draft.name = name; draft.studentId = me.studentId; draft.email = email; draft.hostel = hostel; draft.room = room; draft.contact = contact;
            return Resp.html(View.studentProfile(draft, error));
        }
        me.name = name; me.email = email; me.hostel = hostel; me.room = room; me.contact = contact;
        for (Complaint c : Store.complaints)          // keep details on existing complaints up to date
            if (c.studentId.equalsIgnoreCase(me.studentId)) { c.studentName = name; c.hostel = hostel; c.room = room; c.contact = contact; }
        Store.save();
        r.session.flash("Details updated");
        return Resp.redirect("/student");
    }

    static int parseInt(String v) { try { return Integer.parseInt(v.trim()); } catch (Exception e) { return 0; } }

    // ============================================================== ADMIN
    static Resp admin(Req r, String p, boolean get, boolean post) {
        Session s = r.session;
        if (p.equals("/admin/login")) {
            if (get) return Resp.html(View.adminLogin(""));
            if (post) {
                Admin a = Store.admin;
                if (r.f("id").equals(a.adminId) && r.raw("password").equals(a.password)) {
                    s.role = "admin"; s.userId = "admin";
                    return Resp.redirect("/admin");
                }
                return Resp.html(View.adminLogin("Invalid Admin ID or Password!"));
            }
        }
        if (!s.role.equals("admin")) return Resp.redirect("/admin/login");
        if (p.equals("/admin/logout")) { s.logout(); return Resp.redirect("/"); }

        if (p.equals("/admin") && get) {
            String tab = r.q("tab");
            if (!List.of("pending", "solved", "workers").contains(tab)) tab = "new";
            return Resp.html(View.adminDashboard(tab, r.q("q").trim(), r.q("cat"), s.takeFlash()));
        }

        if (p.equals("/admin/reject")) {
            Complaint c = Store.findComplaint(get ? r.q("id") : r.f("id"));
            if (c == null || !c.status.equals("Pending")) return Resp.redirect("/admin?tab=new");
            if (get) return Resp.html(View.confirmPage("/admin?tab=new", "Reject Complaint", "Reject this complaint?",
                "/admin/reject", "id", c.id, "Yes, Reject"));
            c.status = "Rejected";
            Store.save();
            s.flash("Complaint rejected");
            return Resp.redirect("/admin?tab=new");
        }

        if (p.equals("/admin/assign")) {
            if (get) {
                Complaint c = Store.findComplaint(r.q("id"));
                if (c == null) return Resp.redirect("/admin");
                return Resp.html(View.assignPage(c));
            }
            Complaint c = Store.findComplaint(r.f("id"));
            Worker w = Store.findWorker(r.f("workerId"));
            if (c == null || w == null) return Resp.redirect("/admin");
            c.status = "Assigned"; c.workerName = w.name; c.workerId = w.workerId;
            Store.save();
            s.flash("Assigned to " + w.name);
            return Resp.redirect("/admin?tab=pending");
        }

        if (p.equals("/admin/worker")) {
            if (get) {
                String id = r.q("id");
                Worker w = id.isEmpty() ? null : Store.findWorker(id);
                if (!id.isEmpty() && w == null) return Resp.redirect("/admin?tab=workers");
                return Resp.html(View.workerForm(w, Map.of(), ""));
            }
            if (post) return saveWorker(r);
        }

        if (p.equals("/admin/worker/delete")) {
            Worker w = Store.findWorker(get ? r.q("id") : r.f("id"));
            if (w == null) return Resp.redirect("/admin?tab=workers");
            long activeCount = Store.complaints.stream().filter(c -> c.workerId.equals(w.workerId) && c.active()).count();
            if (get) {
                String text = "Delete " + View.esc(w.name) + "'s profile?"
                    + (activeCount > 0 ? "<br><br>" + activeCount + " active complaint(s) will go back to New Complaints so you can reassign them." : "");
                return Resp.html(View.confirmPage("/admin?tab=workers", "Delete Worker", text, "/admin/worker/delete", "id", w.workerId, "Yes, Delete"));
            }
            for (Complaint c : Store.complaints)
                if (c.workerId.equals(w.workerId) && c.active()) { c.status = "Pending"; c.workerName = ""; c.workerId = ""; }
            Store.workers.remove(w);
            Store.save();
            s.flash("Worker profile deleted");
            return Resp.redirect("/admin?tab=workers");
        }

        if (p.equals("/admin/profile")) {
            if (get) return Resp.html(View.adminProfile(""));
            if (post) {
                String name = r.f("name"), id = r.f("adminId"), pw = r.raw("password");
                if (name.isEmpty() || id.isEmpty()) return Resp.html(View.adminProfile("Name and Admin ID cannot be empty."));
                if (pw.length() < 6) return Resp.html(View.adminProfile("Password must contain at least 6 characters."));
                Store.admin.name = name; Store.admin.adminId = id; Store.admin.password = pw;
                Store.save();
                s.flash("Profile updated");
                return Resp.redirect("/admin");
            }
        }
        return Resp.notFound();
    }

    static Resp saveWorker(Req r) {
        String editing = r.f("editing");
        Worker existing = editing.isEmpty() ? null : Store.findWorker(editing);
        String name = r.f("name"), id = editing.isEmpty() ? r.f("workerId") : editing, pw = r.raw("password");
        String category = r.f("category"), contact = r.f("contact");
        Map<String, String> v = new HashMap<>(Map.of("name", name, "workerId", id, "password", pw, "category", category, "contact", contact));

        String error = null;
        boolean validCategory = Arrays.stream(Store.CATEGORIES).anyMatch(c -> c[0].equals(category));
        if (name.isEmpty() || id.isEmpty() || !validCategory) error = "Please fill in all fields.";
        else if (!WORKER_ID.matcher(id).matches()) error = "Worker ID can only contain letters, numbers, - and _.";
        else if (pw.length() < 6) error = "Password must contain at least 6 characters.";
        else if (!TEN_DIGITS.matcher(contact).matches()) error = "Enter a valid 10-digit contact number.";
        else if (!editing.isEmpty() && existing == null) error = "Worker not found.";
        else if (editing.isEmpty() && Store.findWorker(id) != null) error = "This Worker ID already exists.";
        if (error != null) return Resp.html(View.workerForm(existing, v, error));

        if (existing != null) {
            existing.name = name; existing.password = pw; existing.category = category; existing.contact = contact;
            for (Complaint c : Store.complaints) if (c.workerId.equals(existing.workerId)) c.workerName = name;
            r.session.flash("Worker profile updated");
        } else {
            Worker w = new Worker();
            w.name = name; w.workerId = id; w.password = pw; w.category = category; w.contact = contact;
            Store.workers.add(w);
            r.session.flash("Worker profile created");
        }
        Store.save();
        return Resp.redirect("/admin?tab=workers");
    }

    // ============================================================== WORKER
    static Resp worker(Req r, String p, boolean get, boolean post) throws IOException {
        Session s = r.session;
        if (p.equals("/worker/login")) {
            if (get) return Resp.html(View.workerLogin(""));
            if (post) {
                for (Worker w : Store.workers)
                    if (w.workerId.equalsIgnoreCase(r.f("id")) && w.password.equals(r.raw("password"))) {
                        s.role = "worker"; s.userId = w.workerId;
                        return Resp.redirect("/worker");
                    }
                return Resp.html(View.workerLogin("Invalid Worker ID or Password!"));
            }
        }
        Worker me = s.role.equals("worker") ? Store.findWorker(s.userId) : null;
        if (me == null) return Resp.redirect("/worker/login");
        if (p.equals("/worker/logout")) { s.logout(); return Resp.redirect("/"); }

        if (p.equals("/worker") && get) {
            String tab = r.q("tab");
            if (!tab.equals("pending") && !tab.equals("reviews")) tab = "new";
            return Resp.html(View.workerDashboard(me, tab, r.q("sub"), s.takeFlash()));
        }

        if (p.equals("/worker/start") && post) {
            Complaint c = Store.findComplaint(r.f("id"));
            if (c != null && c.workerId.equals(me.workerId) && c.status.equals("Assigned")) {
                c.status = "In Progress";
                Store.save();
                s.flash("Moved to Pending Complaints");
            }
            return Resp.redirect("/worker?tab=pending");
        }

        if (p.equals("/worker/complete")) {
            Complaint c = Store.findComplaint(get ? r.q("id") : r.f("id"));
            if (c == null || !c.workerId.equals(me.workerId) || !c.active()) return Resp.redirect("/worker");
            if (get) return Resp.html(View.completePage(c, ""));
            int rating = parseInt(r.f("rating"));
            String review = r.f("review");
            FilePart part = r.files.get("photo");
            if (part == null) return Resp.html(View.completePage(c, "Please upload a photo as proof of the work."));
            if (rating < 1 || rating > 5) return Resp.html(View.completePage(c, "Please rate the student."));
            if (review.isEmpty()) return Resp.html(View.completePage(c, "Please describe the student's behavior."));
            String[] err = {""};
            String photo = saveImage(part, err);
            if (photo == null) return Resp.html(View.completePage(c, err[0]));
            c.status = "Resolved"; c.completionPhoto = photo; c.workerRating = rating;
            c.workerReview = review.length() > 500 ? review.substring(0, 500) : review;
            c.completedDate = today();
            Store.save();
            s.flash("Work marked as completed");
            return Resp.redirect("/worker");
        }

        if (p.equals("/worker/profile")) {
            if (get) return Resp.html(View.workerProfile(me, ""));
            if (post) {
                String newId = r.f("workerId"), pw = r.raw("password");
                Worker draft = new Worker();
                draft.workerId = newId; draft.password = pw;
                if (newId.isEmpty()) return Resp.html(View.workerProfile(draft, "Worker ID cannot be empty."));
                if (!WORKER_ID.matcher(newId).matches()) return Resp.html(View.workerProfile(draft, "Worker ID can only contain letters, numbers, - and _."));
                if (pw.length() < 6) return Resp.html(View.workerProfile(draft, "Password must contain at least 6 characters."));
                for (Worker o : Store.workers)
                    if (o != me && o.workerId.equalsIgnoreCase(newId))
                        return Resp.html(View.workerProfile(draft, "This Worker ID is already used by another worker."));
                String oldId = me.workerId;
                me.workerId = newId; me.password = pw;
                if (!newId.equals(oldId))
                    for (Complaint c : Store.complaints) if (c.workerId.equals(oldId)) c.workerId = newId;
                s.userId = newId;
                Store.save();
                s.flash("Profile updated");
                return Resp.redirect("/worker");
            }
        }
        return Resp.notFound();
    }
}
