package unireslove;

import java.util.*;

/**
 * All HTML is produced here on the server (this replaces the template strings and
 * document.getElementById(...) code that lived in script.js).
 */
final class View {
    private View() {}

    // ================================================================ helpers
    static String esc(String v) {
        if (v == null) return "";
        StringBuilder sb = new StringBuilder(v.length() + 16);
        for (char c : v.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String page(String body) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
            + "<title>uniReslove | Campus Issue Resolution</title>"
            + "<link rel=\"stylesheet\" href=\"/style.css\"><link rel=\"stylesheet\" href=\"/extra.css\">"
            + "<link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&family=Space+Mono&display=swap\" rel=\"stylesheet\">"
            + "<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css\">"
            + "</head><body>" + body + "</body></html>";
    }

    /** form-message paragraph; type is "error" or "success". */
    static String msg(String text, String type) {
        if (text == null || text.isEmpty()) return "<p class=\"form-message\"></p>";
        return "<p class=\"form-message " + type + "\">" + esc(text) + "</p>";
    }

    static String toast(String[] flash) {
        if (flash == null || flash[0].isEmpty()) return "";
        return "<div class=\"admin-toast show\">" + esc(flash[0]) + "</div>";
    }

    static String stars(int rating) {
        int r = Math.max(0, Math.min(5, rating));
        return "★".repeat(r) + "☆".repeat(5 - r);
    }

    static String avg(List<Complaint> rated, boolean worker) {
        if (rated.isEmpty()) return null;
        double sum = 0;
        for (Complaint c : rated) sum += worker ? c.workerRating : c.rating;
        return String.format(Locale.ROOT, "%.1f", sum / rated.size());
    }

    static String categoryIcon(String c) {
        return switch (c) {
            case "Plumber" -> "fa-faucet";
            case "Electrician" -> "fa-bolt";
            case "Wi-Fi" -> "fa-wifi";
            case "Carpenter" -> "fa-hammer";
            case "AC" -> "fa-snowflake";
            default -> "fa-tools";
        };
    }

    static String statusClass(String s) {
        return switch (s) {
            case "Assigned" -> "status-assigned";
            case "In Progress" -> "status-in-progress";
            case "Resolved", "Solved" -> "status-resolved";
            case "Rejected" -> "status-rejected";
            default -> "status-pending";
        };
    }

    static String emptyState(String icon, String title, String text) {
        return "<div class=\"empty-state\"><i class=\"fa-solid " + icon + "\"></i><h3>" + title + "</h3><p>" + text + "</p></div>";
    }

    static String photoImg(String file, String alt) {
        if (file == null || file.isEmpty()) return "";
        return "<a href=\"/uploads/" + esc(file) + "\" target=\"_blank\"><img class=\"complaint-photo\" src=\"/uploads/"
            + esc(file) + "\" alt=\"" + alt + "\"></a>";
    }


    /** A page-sized "modal" (reuses the modal CSS, but is a normal page so no JavaScript is needed). */
    static String modalPage(String closeHref, String inner) {
        return page("<div class=\"modal-overlay show\"><div class=\"modal-box\">"
            + "<a class=\"modal-close\" href=\"" + closeHref + "\"><i class=\"fa-solid fa-xmark\"></i></a>"
            + inner + "</div></div>");
    }

    static String field(String label, String id, String type, String value, String extra) {
        return "<div class=\"modal-field\"><label for=\"" + id + "\">" + label + "</label>"
            + "<input type=\"" + type + "\" id=\"" + id + "\" name=\"" + id + "\" value=\"" + esc(value) + "\" " + extra + "></div>";
    }

    static String starPicker(String name) {
        StringBuilder sb = new StringBuilder("<div class=\"star-picker\">");
        for (int i = 5; i >= 1; i--)
            sb.append("<input type=\"radio\" id=\"").append(name).append(i).append("\" name=\"").append(name)
              .append("\" value=\"").append(i).append("\" required><label for=\"").append(name).append(i)
              .append("\" title=\"").append(i).append(" star\">★</label>");
        return sb.append("</div>").toString();
    }

    // ================================================================ landing + auth
    static String rolePage() {
        return page("""
            <main class="main-page"><div class="selection-container">
              <div class="welcome-heading">
                <div class="small-logo"><i class="fa-solid fa-graduation-cap"></i><span>uniReslove</span></div>
                <h1>Welcome Back!</h1><p>Select your role to continue</p>
              </div>
              <div class="role-buttons">
                <a class="role-btn student-btn" href="/student/login">
                  <div class="role-icon"><i class="fa-solid fa-user-graduate"></i></div>
                  <div class="role-info"><h2>Student</h2><p>Raise and track campus issues</p></div>
                  <div class="arrow-icon"><i class="fa-solid fa-arrow-right"></i></div></a>
                <a class="role-btn admin-btn" href="/admin/login">
                  <div class="role-icon"><i class="fa-solid fa-user-shield"></i></div>
                  <div class="role-info"><h2>Admin</h2><p>Manage and resolve campus issues</p></div>
                  <div class="arrow-icon"><i class="fa-solid fa-arrow-right"></i></div></a>
                <a class="role-btn worker-btn" href="/worker/login">
                  <div class="role-icon"><i class="fa-solid fa-screwdriver-wrench"></i></div>
                  <div class="role-info"><h2>Worker</h2><p>View and handle assigned tasks</p></div>
                  <div class="arrow-icon"><i class="fa-solid fa-arrow-right"></i></div></a>
              </div>
              <div class="footer-text">SECURE CAMPUS ACCESS</div>
            </div></main>""");
    }

    private static String authBrand() {
        return "<div class=\"auth-brand\"><div class=\"auth-logo\"><i class=\"fa-solid fa-graduation-cap\"></i></div><span>uniReslove</span></div>";
    }

    private static String inputGroup(String label, String id, String icon, String type, String value, String placeholder, String extra) {
        return "<div class=\"input-group\"><label for=\"" + id + "\">" + label + "</label><div class=\"input-wrapper\">"
            + "<i class=\"fa-solid " + icon + "\"></i><input type=\"" + type + "\" id=\"" + id + "\" name=\"" + id
            + "\" value=\"" + esc(value) + "\" placeholder=\"" + placeholder + "\" " + extra + " required></div></div>";
    }

    static String studentLogin(String message, String type, String identifier) {
        return page("<section class=\"auth-page\"><div class=\"auth-container\">" + authBrand()
            + "<div class=\"auth-heading\"><h1>Student Login</h1><p>Welcome back! Sign in to access your campus portal.</p></div>"
            + "<form class=\"auth-form\" method=\"post\" action=\"/student/login\">"
            + inputGroup("Student ID, Email or Contact Number", "identifier", "fa-user", "text", identifier, "Enter ID, email or contact number", "")
            + inputGroup("Password", "password", "fa-lock", "password", "", "Enter your password", "")
            + "<div class=\"forgot-row\"><a class=\"forgot-link\" href=\"/student/forgot?id=" + Web.url(identifier) + "\">Forgot password?</a></div>"
            + msg(message, type)
            + "<button type=\"submit\" class=\"primary-btn\">Login <i class=\"fa-solid fa-arrow-right\"></i></button></form>"
            + "<div class=\"create-account-area\"><p>New to uniReslove?</p>"
            + "<a class=\"create-account-btn\" href=\"/student/register\"><i class=\"fa-solid fa-user-plus\"></i> Create New Account</a></div>"
            + "<a class=\"back-link\" href=\"/\"><i class=\"fa-solid fa-arrow-left\"></i> Back to role selection</a>"
            + "</div></section>");
    }

    static String studentRegister(String message, Map<String, String> v) {
        return page("<section class=\"auth-page\"><div class=\"register-container\">" + authBrand()
            + "<div class=\"auth-heading\"><h1>Create Account</h1><p>Register to access the uniReslove student portal.</p></div>"
            + "<form class=\"auth-form\" method=\"post\" action=\"/student/register\"><div class=\"register-grid\">"
            + inputGroup("Full Name", "name", "fa-user", "text", v.getOrDefault("name", ""), "Enter full name", "")
            + inputGroup("Student ID", "studentId", "fa-id-card", "text", v.getOrDefault("studentId", ""), "Enter student ID", "")
            + inputGroup("Email Address", "email", "fa-envelope", "email", v.getOrDefault("email", ""), "Enter email address", "")
            + inputGroup("Password", "password", "fa-lock", "password", "", "Create password", "minlength=\"6\"")
            + inputGroup("Hostel Name", "hostel", "fa-building", "text", v.getOrDefault("hostel", ""), "Enter hostel name", "")
            + inputGroup("Room Number", "room", "fa-door-open", "text", v.getOrDefault("room", ""), "Enter room number", "")
            + "<div class=\"full-width\" style=\"grid-column:1/-1\">"
            + inputGroup("Contact Number", "contact", "fa-phone", "tel", v.getOrDefault("contact", ""), "Enter 10-digit mobile number", "pattern=\"[0-9]{10}\" maxlength=\"10\"")
            + "</div></div>" + msg(message, "error")
            + "<button type=\"submit\" class=\"primary-btn\">Create Account <i class=\"fa-solid fa-arrow-right\"></i></button></form>"
            + "<a class=\"back-link\" href=\"/student/login\"><i class=\"fa-solid fa-arrow-left\"></i> Back to Student Login</a>"
            + "</div></section>");
    }

    static String forgotStep1(String message, String identifier) {
        return modalPage("/student/login",
            "<h2>Forgot Password</h2><p class=\"modal-sub\">Enter your account details and choose where to receive the OTP.</p>"
            + "<form method=\"post\" action=\"/student/forgot\">"
            + field("Student ID, Email or Contact Number", "identifier", "text", identifier, "required")
            + "<div class=\"modal-field\"><label>Send OTP to</label><div class=\"otp-choice\">"
            + "<label class=\"otp-option\"><input type=\"radio\" name=\"method\" value=\"contact\" checked><span><i class=\"fa-solid fa-phone\"></i> Contact No.</span></label>"
            + "<label class=\"otp-option\"><input type=\"radio\" name=\"method\" value=\"email\"><span><i class=\"fa-solid fa-envelope\"></i> Email</span></label>"
            + "</div></div>" + msg(message, "error")
            + "<button type=\"submit\" class=\"primary-btn\">Send OTP <i class=\"fa-solid fa-paper-plane\"></i></button></form>");
    }

    static String forgotStep2(String sentTo, String otp, String message, String type) {
        return modalPage("/student/login",
            "<h2>Forgot Password</h2><p class=\"modal-sub\">" + esc(sentTo) + "</p>"
            + "<div class=\"otp-demo\"><i class=\"fa-solid fa-circle-info\"></i> Demo mode: your OTP is <strong>" + esc(otp) + "</strong></div>"
            + "<form method=\"post\" action=\"/student/forgot/reset\">"
            + field("Enter OTP", "otp", "text", "", "maxlength=\"6\" inputmode=\"numeric\" placeholder=\"6-digit OTP\" required")
            + field("New Password", "newPassword", "password", "", "minlength=\"6\" placeholder=\"Minimum 6 characters\" required")
            + field("Confirm New Password", "confirmPassword", "password", "", "minlength=\"6\" placeholder=\"Re-enter new password\" required")
            + msg(message, type)
            + "<button type=\"submit\" class=\"primary-btn\">Change Password <i class=\"fa-solid fa-check\"></i></button></form>"
            + "<form method=\"post\" action=\"/student/forgot/resend\"><button type=\"submit\" class=\"resend-link\">Resend OTP</button></form>");
    }

    static String adminLogin(String message) { return staffLogin("Admin", "Sign in to access the Admin Portal", "fa-user-shield", "Admin ID", "/admin/login", "", message); }
    static String workerLogin(String message) { return staffLogin("Worker", "Sign in to access the Worker Portal", "fa-screwdriver-wrench", "Worker ID", "/worker/login", " worker-theme", message); }

    private static String staffLogin(String title, String sub, String icon, String idPlaceholder, String action, String theme, String message) {
        return page("<section class=\"admin-auth-page" + theme + "\"><div class=\"admin-auth-card\">"
            + "<div class=\"admin-auth-icon\"><i class=\"fa-solid " + icon + "\"></i></div><h1>" + title + " Login</h1><p>" + sub + "</p>"
            + "<form method=\"post\" action=\"" + action + "\">"
            + "<div class=\"admin-input-group\"><i class=\"fa-solid fa-id-card\"></i><input type=\"text\" name=\"id\" placeholder=\"" + idPlaceholder + "\" required></div>"
            + "<div class=\"admin-input-group\"><i class=\"fa-solid fa-lock\"></i><input type=\"password\" name=\"password\" placeholder=\"Password\" required></div>"
            + "<p class=\"login-error\">" + esc(message) + "</p>"
            + "<button type=\"submit\" class=\"admin-login-btn\">Login <i class=\"fa-solid fa-arrow-right\"></i></button></form>"
            + "<a class=\"admin-back-btn\" href=\"/\"><i class=\"fa-solid fa-arrow-left\"></i> Back</a>"
            + "</div></section>");
    }

    // ================================================================ dashboard shell
    private static String nav(String href, String icon, String label, String count, boolean active) {
        return "<a class=\"nav-btn" + (active ? " active" : "") + "\" href=\"" + href + "\"><i class=\"fa-solid " + icon + "\"></i><span>"
            + label + "</span>" + (count == null ? "" : "<span class=\"nav-count\">" + count + "</span>") + "</a>";
    }

    private static String stat(String cls, String icon, String label, String value) {
        return "<div class=\"stat-card\"><div class=\"stat-icon " + cls + "\"><i class=\"fa-solid " + icon + "\"></i></div><div><p>" + label
            + "</p><h2>" + value + "</h2></div></div>";
    }

    private static String sectionHeading(String title, String sub, String icon) {
        return "<div class=\"section-heading\"><div><h2>" + title + "</h2><p>" + sub + "</p></div><div class=\"section-heading-icon\"><i class=\"fa-solid "
            + icon + "\"></i></div></div>";
    }

    private static String detail(String icon, String label, String value) {
        return "<div class=\"profile-detail\"><i class=\"fa-solid " + icon + "\"></i><div><small>" + label + "</small><span>" + esc(value) + "</span></div></div>";
    }

    private static String shell(String portalClass, String profileHtml, String menuHeading, String navHtml, String logoutHref,
                                String topLabel, String pageTitle, String account, String welcome, String stats,
                                String section, String footer, String[] flash) {
        return page("<section class=\"student-dashboard " + portalClass + "\">"
            + "<aside class=\"dashboard-sidebar\"><div class=\"dashboard-brand\"><div class=\"dashboard-logo\"><i class=\"fa-solid fa-graduation-cap\"></i></div>"
            + "<span>uni<span>Reslove</span></span></div>"
            + "<div class=\"sidebar-profile\">" + profileHtml + "</div>"
            + "<div class=\"sidebar-heading\">" + menuHeading + "</div><nav class=\"dashboard-nav\">" + navHtml + "</nav>"
            + "<a class=\"sidebar-logout\" href=\"" + logoutHref + "\"><i class=\"fa-solid fa-right-from-bracket\"></i> Logout</a></aside>"
            + "<main class=\"dashboard-main\"><header class=\"dashboard-topbar\"><div><p class=\"topbar-label\">" + topLabel + "</p><h2>" + pageTitle
            + "</h2></div><div class=\"topbar-right\"><div class=\"online-indicator\"></div><span>" + account + "</span></div></header>"
            + welcome + "<div class=\"dashboard-stats\">" + stats + "</div>" + section
            + "<footer class=\"dashboard-footer\"><p>© 2026 uniReslove. Campus Issue Resolution Platform.</p><span>" + footer + "</span></footer>"
            + "</main></section>" + toast(flash));
    }

    // ================================================================ student
    static String studentDashboard(Student me, String tab, List<Complaint> mine, String[] flash) {
        long pending = mine.stream().filter(c -> c.status.equals("Pending")).count();
        long progress = mine.stream().filter(c -> c.status.equals("In Progress") || c.status.equals("Assigned")).count();
        List<Complaint> solved = mine.stream().filter(Complaint::solved).toList();

        String profile = "<div class=\"profile-avatar\">" + esc(me.name.substring(0, 1).toUpperCase()) + "</div><h3>" + esc(me.name) + "</h3><p>"
            + esc(me.studentId) + "</p><div class=\"profile-divider\"></div>"
            + detail("fa-building", "Hostel", me.hostel) + detail("fa-door-open", "Room Number", me.room)
            + detail("fa-envelope", "Email", me.email) + detail("fa-phone", "Contact", me.contact)
            + "<a class=\"edit-profile-btn\" href=\"/student/profile\"><i class=\"fa-solid fa-pen\"></i> Edit Details</a>";

        String navHtml = nav("/student?tab=raise", "fa-circle-plus", "Raise Complaint", null, tab.equals("raise"))
            + nav("/student?tab=mine", "fa-clipboard-list", "My Complaints", String.valueOf(mine.size()), tab.equals("mine"))
            + nav("/student?tab=solved", "fa-circle-check", "Complaints Solved", String.valueOf(solved.size()), tab.equals("solved"));

        String stats = stat("purple", "fa-file-lines", "Total Complaints", String.valueOf(mine.size()))
            + stat("orange", "fa-clock", "Pending", String.valueOf(pending))
            + stat("blue", "fa-screwdriver-wrench", "In Progress", String.valueOf(progress))
            + stat("green", "fa-circle-check", "Resolved", String.valueOf(solved.size()));

        String welcome = "<div class=\"dashboard-welcome\"><div><p>WELCOME TO UNIRESLOVE</p><h1>Hello, <span>" + esc(me.name.split(" ")[0])
            + "</span>!</h1><p>We're here to help make your campus experience better.</p></div>"
            + "<div class=\"welcome-icon\"><i class=\"fa-solid fa-headset\"></i></div></div>";

        String title, section;
        switch (tab) {
            case "mine" -> { title = "My Complaints"; section = studentMine(mine); }
            case "solved" -> { title = "Complaints Solved"; section = studentSolved(solved); }
            default -> { title = "Raise Complaint"; section = studentRaise(); }
        }
        return shell("", profile, "STUDENT MENU", navHtml, "/student/logout", "STUDENT PORTAL", title, "Student Account",
            welcome, stats, section, "STUDENT PORTAL", flash);
    }

    private static String studentRaise() {
        StringBuilder opts = new StringBuilder("<option value=\"\">Select complaint type</option>");
        for (String[] c : Store.CATEGORIES) opts.append("<option value=\"").append(c[0]).append("\">").append(c[1]).append("</option>");
        return "<section class=\"dashboard-section active\">"
            + sectionHeading("Raise a Complaint", "Facing an issue? Tell us about it and we'll help you.", "fa-pen-to-square")
            + "<form class=\"complaint-form\" method=\"post\" action=\"/student/complaint\" enctype=\"multipart/form-data\">"
            + "<div class=\"complaint-form-grid\">"
            + "<div class=\"input-group\"><label for=\"category\">Complaint Category</label><div class=\"dashboard-input-wrapper\"><i class=\"fa-solid fa-list\"></i>"
            + "<select id=\"category\" name=\"category\" required>" + opts + "</select></div></div>"
            + "<div class=\"input-group\"><label for=\"location\">Location / Room</label><div class=\"dashboard-input-wrapper\"><i class=\"fa-solid fa-location-dot\"></i>"
            + "<input type=\"text\" id=\"location\" name=\"location\" placeholder=\"Enter issue location\" required></div></div></div>"
            + "<div class=\"input-group\"><label for=\"description\">Describe Your Complaint</label>"
            + "<textarea id=\"description\" name=\"description\" placeholder=\"Explain the issue in detail. Include when it started and any other useful information...\" rows=\"6\" maxlength=\"1000\" required></textarea>"
            + "<div class=\"textarea-footer\"><span>Provide enough details to help us understand the problem. (Max 1000 characters)</span></div></div>"
            + "<div class=\"input-group\"><label>Upload Photo Proof <span class=\"optional-label\">(Optional)</span></label>"
            + "<label for=\"photo\" class=\"upload-area\"><div class=\"upload-icon\"><i class=\"fa-solid fa-cloud-arrow-up\"></i></div>"
            + "<h3>Click to upload a photo</h3><p>PNG, JPG or JPEG (Maximum 2 MB)</p>"
            + "<input type=\"file\" id=\"photo\" name=\"photo\" accept=\"image/png,image/jpeg\" style=\"display:block;margin-top:10px;max-width:100%\"></label></div>"
            + "<button type=\"submit\" class=\"primary-btn\">Submit Complaint <i class=\"fa-solid fa-paper-plane\"></i></button></form></section>";
    }

    private static String cardHead(Complaint c, String badge) {
        return "<div class=\"complaint-card-top\"><div class=\"complaint-category\"><div class=\"category-icon\"><i class=\"fa-solid "
            + categoryIcon(c.category) + "\"></i></div><div><h3>" + esc(c.category) + " Complaint</h3><p>ID: " + esc(c.id)
            + "</p></div></div>" + badge + "</div>";
    }

    private static String meta(Complaint c, String dateIcon) {
        return "<div class=\"complaint-meta\"><span><i class=\"" + dateIcon + "\"></i> " + esc(c.date)
            + "</span><span><i class=\"fa-solid fa-location-dot\"></i> " + esc(c.location) + "</span></div>";
    }

    private static String studentMine(List<Complaint> mine) {
        StringBuilder sb = new StringBuilder("<section class=\"dashboard-section active\">")
            .append(sectionHeading("My Complaints", "Track the status of every complaint you have raised.", "fa-clipboard-list"))
            .append("<div class=\"complaints-list\">");
        if (mine.isEmpty()) sb.append(emptyState("fa-clipboard-list", "No Complaints Yet",
            "You haven't submitted any complaints.<br>Use Raise Complaint to report an issue."));
        for (Complaint c : mine) {
            sb.append("<div class=\"complaint-card\">")
              .append(cardHead(c, "<span class=\"status-badge " + statusClass(c.status) + "\">" + esc(c.status) + "</span>"))
              .append("<p class=\"complaint-description\">").append(esc(c.description)).append("</p>")
              .append(photoImg(c.photo, "Complaint photo"))
              .append("<div class=\"complaint-card-bottom\">").append(meta(c, "fa-regular fa-calendar")).append("</div>");
            if (!c.workerName.isEmpty())
                sb.append("<div class=\"worker-info\"><i class=\"fa-solid fa-user-gear\"></i> Assigned Worker: <strong>")
                  .append(esc(c.workerName)).append("</strong></div>");
            sb.append("</div>");
        }
        return sb.append("</div></section>").toString();
    }

    private static String studentSolved(List<Complaint> solved) {
        StringBuilder sb = new StringBuilder("<section class=\"dashboard-section active\">")
            .append(sectionHeading("Complaints Solved", "Review the workers who resolved your issues.", "fa-circle-check"))
            .append("<div class=\"complaints-list\">");
        if (solved.isEmpty()) sb.append(emptyState("fa-circle-check", "No Solved Complaints Yet",
            "Your resolved complaints will appear here.<br>You can review the worker after an issue is resolved."));
        for (Complaint c : solved) {
            sb.append("<div class=\"complaint-card\">")
              .append(cardHead(c, "<span class=\"status-badge status-resolved\">Resolved</span>"))
              .append("<p class=\"complaint-description\">").append(esc(c.description)).append("</p>")
              .append(photoImg(c.completionPhoto, "Completed work photo"))
              .append("<div class=\"complaint-card-bottom\">").append(meta(c, "fa-regular fa-calendar-check")).append("</div>")
              .append("<div class=\"worker-info\"><i class=\"fa-solid fa-user-gear\"></i> Solved by: <strong>")
              .append(esc(c.workerName.isEmpty() ? "Worker details unavailable" : c.workerName)).append("</strong></div>");
            if (c.rating > 0)
                sb.append("<div class=\"review-display\"><div class=\"review-stars\">").append(stars(c.rating)).append("</div><strong>Your Review</strong><p>")
                  .append(esc(c.review)).append("</p></div><p class=\"reviewed-label\" style=\"margin-top:12px;\"><i class=\"fa-solid fa-circle-check\"></i> Review submitted</p>");
            else
                sb.append("<a class=\"review-action\" href=\"/student/review?id=").append(esc(c.id)).append("\"><i class=\"fa-solid fa-star\"></i> Rate &amp; Review Worker</a>");
            sb.append("</div>");
        }
        return sb.append("</div></section>").toString();
    }

    static String reviewPage(Complaint c, String message) {
        return page("<div class=\"review-modal show\"><div class=\"review-box\">"
            + "<a class=\"close-review\" href=\"/student?tab=solved\"><i class=\"fa-solid fa-xmark\"></i></a>"
            + "<div class=\"review-icon\"><i class=\"fa-solid fa-star\"></i></div><h2>Rate Your Experience</h2><p>How was the worker's service?</p>"
            + "<form method=\"post\" action=\"/student/review\"><input type=\"hidden\" name=\"id\" value=\"" + esc(c.id) + "\">"
            + starPicker("rating")
            + "<textarea name=\"review\" placeholder=\"Write your feedback about the worker...\" rows=\"4\" maxlength=\"500\" required></textarea>"
            + msg(message, "error")
            + "<button type=\"submit\" class=\"primary-btn\">Submit Review <i class=\"fa-solid fa-arrow-right\"></i></button></form></div></div>");
    }

    static String studentProfile(Student s, String message) {
        return modalPage("/student",
            "<h2>Edit Your Details</h2><p class=\"modal-sub\">Update your details. Student ID cannot be changed.</p>"
            + "<form method=\"post\" action=\"/student/profile\">"
            + field("Full Name", "name", "text", s.name, "required")
            + field("Student ID", "studentId", "text", s.studentId, "readonly")
            + field("Email", "email", "email", s.email, "required")
            + field("Hostel Name", "hostel", "text", s.hostel, "required")
            + field("Room Number", "room", "text", s.room, "required")
            + field("Contact Number", "contact", "tel", s.contact, "pattern=\"[0-9]{10}\" maxlength=\"10\" required")
            + msg(message, "error")
            + "<button type=\"submit\" class=\"primary-btn\">Save Changes <i class=\"fa-solid fa-check\"></i></button></form>");
    }

    // ================================================================ admin
    private static String studentInfoBox(Complaint c) {
        return "<div class=\"student-info-box\"><div><small>Student</small><span>" + esc(c.studentName) + "</span></div>"
            + "<div><small>Student ID</small><span>" + esc(c.studentId) + "</span></div>"
            + "<div><small>Hostel / Room</small><span>" + esc(c.hostel) + " / " + esc(c.room) + "</span></div>"
            + "<div><small>Contact</small><span>" + esc(c.contact) + "</span></div></div>";
    }

    private static String adminHead(Complaint c, String badge, boolean tick) {
        return "<div class=\"complaint-card-top\"><div class=\"complaint-category\"><div class=\"category-icon\"><i class=\"fa-solid "
            + categoryIcon(c.category) + "\"></i></div><div><h3>" + esc(c.category) + " Complaint"
            + (tick ? " <span class=\"solved-tick\"><i class=\"fa-solid fa-check\"></i></span>" : "")
            + "</h3><p>ID: " + esc(c.id) + "</p></div></div>" + badge + "</div>";
    }

    private static String reviewBlock(String title, int rating, String text, String emptyText) {
        if (rating <= 0) return "<div class=\"review-display\"><strong>" + title + "</strong><p>" + emptyText + "</p></div>";
        return "<div class=\"review-display\"><div class=\"review-stars\">" + stars(rating) + "</div><strong>" + title + "</strong><p>" + esc(text) + "</p></div>";
    }

    static String adminDashboard(String tab, String search, String filter, String[] flash) {
        List<Complaint> all = Store.complaints;
        List<Complaint> fresh = all.stream().filter(c -> c.status.equals("Pending")).toList();
        List<Complaint> active = all.stream().filter(Complaint::active).toList();
        List<Complaint> solved = all.stream().filter(Complaint::solved).toList();
        Admin a = Store.admin;

        String profile = "<div class=\"profile-avatar\">" + esc(a.name.substring(0, 1).toUpperCase()) + "</div><h3>" + esc(a.name)
            + "</h3><p>Administrator</p><div class=\"profile-divider\"></div>" + detail("fa-id-card", "Admin ID", a.adminId)
            + "<a class=\"edit-profile-btn\" href=\"/admin/profile\"><i class=\"fa-solid fa-pen\"></i> Edit Name, ID &amp; Password</a>";
        String navHtml = nav("/admin?tab=new", "fa-inbox", "New Complaints", String.valueOf(fresh.size()), tab.equals("new"))
            + nav("/admin?tab=pending", "fa-hourglass-half", "Pending Complaints", String.valueOf(active.size()), tab.equals("pending"))
            + nav("/admin?tab=solved", "fa-circle-check", "Complaints Solved", String.valueOf(solved.size()), tab.equals("solved"))
            + nav("/admin?tab=workers", "fa-users-gear", "Worker Details", String.valueOf(Store.workers.size()), tab.equals("workers"));
        String stats = stat("purple", "fa-inbox", "New Complaints", String.valueOf(fresh.size()))
            + stat("orange", "fa-hourglass-half", "Pending", String.valueOf(active.size()))
            + stat("green", "fa-circle-check", "Solved", String.valueOf(solved.size()))
            + stat("blue", "fa-users-gear", "Workers", String.valueOf(Store.workers.size()));

        String title, section;
        switch (tab) {
            case "pending" -> { title = "Pending Complaints"; section = adminPending(active); }
            case "solved" -> { title = "Complaints Solved"; section = adminSolved(solved); }
            case "workers" -> { title = "Worker Details"; section = adminWorkers(search, filter); }
            default -> { title = "New Complaints"; section = adminNew(fresh); }
        }
        return shell("admin-portal", profile, "ADMIN MENU", navHtml, "/admin/logout", "ADMIN PORTAL", title, "Administrator",
            "", stats, section, "ADMIN CONTROL PANEL", flash);
    }

    private static String adminNew(List<Complaint> list) {
        StringBuilder sb = new StringBuilder("<section class=\"dashboard-section active\">")
            .append(sectionHeading("New Complaints", "Review new complaints, then assign a worker or reject them.", "fa-inbox"))
            .append("<div class=\"complaints-list\">");
        if (list.isEmpty()) sb.append(emptyState("fa-inbox", "No New Complaints", "New complaints raised by students will appear here."));
        for (Complaint c : list)
            sb.append("<div class=\"complaint-card\">").append(adminHead(c, "<span class=\"status-badge status-pending\">New</span>", false))
              .append(studentInfoBox(c)).append("<p class=\"complaint-description\">").append(esc(c.description)).append("</p>")
              .append(photoImg(c.photo, "Complaint photo"))
              .append("<div class=\"complaint-card-bottom\">").append(meta(c, "fa-regular fa-calendar")).append("</div>")
              .append("<div class=\"complaint-actions\"><a class=\"action-btn assign-btn\" href=\"/admin/assign?id=").append(esc(c.id))
              .append("\"><i class=\"fa-solid fa-user-check\"></i> Assign Work</a>")
              .append("<a class=\"action-btn reject-btn\" href=\"/admin/reject?id=").append(esc(c.id))
              .append("\"><i class=\"fa-solid fa-ban\"></i> Reject</a></div></div>");
        return sb.append("</div></section>").toString();
    }

    private static String workerLine(Complaint c) {
        Worker w = Store.findWorker(c.workerId);
        String cat = w != null ? " (" + esc(Store.categoryLabel(w.category)) + ")" : "";
        return "<div class=\"worker-info\"><i class=\"fa-solid fa-user-gear\"></i> Assigned Worker: <strong>" + esc(c.workerName)
            + "</strong> &nbsp;•&nbsp; ID: " + esc(c.workerId) + cat + "</div>";
    }

    private static String adminPending(List<Complaint> list) {
        StringBuilder sb = new StringBuilder("<section class=\"dashboard-section active\">")
            .append(sectionHeading("Pending Complaints", "Assigned complaints that are not solved yet.", "fa-hourglass-half"))
            .append("<div class=\"complaints-list\">");
        if (list.isEmpty()) sb.append(emptyState("fa-hourglass-half", "No Pending Complaints",
            "Complaints you assign to workers will appear here until they are solved."));
        for (Complaint c : list)
            sb.append("<div class=\"complaint-card\">")
              .append(adminHead(c, "<span class=\"status-badge " + statusClass(c.status) + "\">" + esc(c.status) + "</span>", false))
              .append(studentInfoBox(c)).append("<p class=\"complaint-description\">").append(esc(c.description)).append("</p>")
              .append(photoImg(c.photo, "Complaint photo")).append(workerLine(c))
              .append("<div class=\"complaint-card-bottom\">").append(meta(c, "fa-regular fa-calendar"))
              .append("<span class=\"not-solved-label\"><i class=\"fa-solid fa-hourglass-half\"></i> Not solved yet</span></div>")
              .append("<div class=\"complaint-actions\"><a class=\"action-btn assign-btn\" href=\"/admin/assign?id=").append(esc(c.id))
              .append("\"><i class=\"fa-solid fa-repeat\"></i> Reassign Worker</a></div></div>");
        return sb.append("</div></section>").toString();
    }

    private static String proof(String label, String file, String alt, String none) {
        return "<div class=\"proof-item\"><small>" + label + "</small>"
            + (file.isEmpty() ? "<div class=\"no-photo\">" + none + "</div>"
                : "<a href=\"/uploads/" + esc(file) + "\" target=\"_blank\"><img src=\"/uploads/" + esc(file) + "\" alt=\"" + alt + "\"></a>")
            + "</div>";
    }

    private static String adminSolved(List<Complaint> list) {
        StringBuilder sb = new StringBuilder("<section class=\"dashboard-section active\">")
            .append(sectionHeading("Complaints Solved", "Work photos and reviews from both student and worker.", "fa-circle-check"))
            .append("<div class=\"complaints-list\">");
        if (list.isEmpty()) sb.append(emptyState("fa-circle-check", "No Solved Complaints Yet",
            "Solved complaints with work photos and reviews will appear here."));
        for (Complaint c : list) {
            sb.append("<div class=\"complaint-card\">").append(adminHead(c, "<span class=\"status-badge status-resolved\">Resolved</span>", true))
              .append(studentInfoBox(c)).append("<p class=\"complaint-description\">").append(esc(c.description)).append("</p>")
              .append("<div class=\"proof-grid\">")
              .append(proof("Student's complaint photo", c.photo, "Complaint photo", "No photo uploaded"))
              .append(proof("Work done photo (by worker)", c.completionPhoto, "Work completed", "No work photo uploaded"))
              .append("</div>");
            if (!c.workerName.isEmpty())
                sb.append("<div class=\"worker-info\"><i class=\"fa-solid fa-user-gear\"></i> Solved by: <strong>").append(esc(c.workerName))
                  .append("</strong> &nbsp;•&nbsp; ID: ").append(esc(c.workerId)).append("</div>");
            sb.append("<div class=\"review-grid\">")
              .append(reviewBlock("Student's review of worker", c.rating, c.review, "Student has not reviewed yet."))
              .append(reviewBlock("Worker's review of student", c.workerRating, c.workerReview, "Worker has not reviewed yet."))
              .append("</div><div class=\"complaint-card-bottom\" style=\"margin-top:14px;\">").append(meta(c, "fa-regular fa-calendar")).append("</div></div>");
        }
        return sb.append("</div></section>").toString();
    }

    private static String categoryOptions(String selected, String first) {
        StringBuilder sb = new StringBuilder("<option value=\"\">" + first + "</option>");
        for (String[] c : Store.CATEGORIES)
            sb.append("<option value=\"").append(c[0]).append("\"").append(c[0].equals(selected) ? " selected" : "").append(">").append(c[1]).append("</option>");
        return sb.toString();
    }

    private static String adminWorkers(String search, String filter) {
        String s = search.toLowerCase();
        List<Worker> shown = new ArrayList<>();
        for (Worker w : Store.workers) {
            boolean catOk = filter.isEmpty() || w.category.equals(filter);
            boolean searchOk = s.isEmpty() || w.name.toLowerCase().contains(s) || w.workerId.toLowerCase().contains(s) || w.contact.contains(s);
            if (catOk && searchOk) shown.add(w);
        }
        StringBuilder sb = new StringBuilder("<section class=\"dashboard-section active\">")
            .append(sectionHeading("Worker Details", "View, filter, edit or delete worker profiles.", "fa-users-gear"))
            .append("<form class=\"admin-toolbar\" method=\"get\" action=\"/admin\"><input type=\"hidden\" name=\"tab\" value=\"workers\">")
            .append("<div class=\"toolbar-search\"><i class=\"fa-solid fa-magnifying-glass\"></i><input type=\"text\" name=\"q\" value=\"").append(esc(search))
            .append("\" placeholder=\"Search by name, ID or contact\"></div>")
            .append("<select name=\"cat\" class=\"toolbar-select\">").append(categoryOptions(filter, "All Categories")).append("</select>")
            .append("<button type=\"submit\" class=\"mini-btn\"><i class=\"fa-solid fa-filter\"></i> Apply</button></form>")
            .append("<div class=\"complaints-list\">");
        if (Store.workers.isEmpty()) sb.append(emptyState("fa-users-gear", "No Workers Yet", "Use the button below to create the first worker profile."));
        else if (shown.isEmpty()) sb.append(emptyState("fa-magnifying-glass", "No Matching Workers", "Try a different search or filter."));
        for (Worker w : shown) {
            List<Complaint> mine = Store.complaints.stream().filter(c -> c.workerId.equals(w.workerId)).toList();
            long act = mine.stream().filter(Complaint::active).count();
            long sol = mine.stream().filter(Complaint::solved).count();
            String avg = avg(mine.stream().filter(c -> c.rating > 0).toList(), false);
            sb.append("<div class=\"worker-card\"><div class=\"worker-avatar\">").append(esc(w.name.substring(0, 1).toUpperCase())).append("</div>")
              .append("<div class=\"worker-main\"><h3>").append(esc(w.name)).append(" <span class=\"category-tag\">")
              .append(esc(Store.categoryLabel(w.category))).append("</span></h3><div class=\"worker-sub\">")
              .append("<span><i class=\"fa-solid fa-id-badge\"></i>").append(esc(w.workerId)).append("</span>")
              .append("<span><i class=\"fa-solid fa-phone\"></i>").append(esc(w.contact)).append("</span>")
              .append("<span><i class=\"fa-solid fa-list-check\"></i>").append(act).append(" active</span>")
              .append("<span><i class=\"fa-solid fa-circle-check\"></i>").append(sol).append(" solved</span>")
              .append("<span><i class=\"fa-solid fa-star\"></i>").append(avg == null ? "No ratings" : avg + " ★").append("</span></div></div>")
              .append("<div class=\"worker-actions\"><a class=\"mini-btn\" href=\"/admin/worker?id=").append(Web.url(w.workerId))
              .append("\"><i class=\"fa-solid fa-pen\"></i> Edit</a><a class=\"mini-btn danger\" href=\"/admin/worker/delete?id=")
              .append(Web.url(w.workerId)).append("\"><i class=\"fa-solid fa-trash\"></i> Delete</a></div></div>");
        }
        return sb.append("</div><a class=\"create-worker-btn\" href=\"/admin/worker\"><i class=\"fa-solid fa-user-plus\"></i> Create New Worker Profile</a></section>").toString();
    }

    static String assignPage(Complaint c) {
        StringBuilder sb = new StringBuilder("<h2>Assign Worker</h2><p class=\"modal-sub\">")
            .append(esc(c.category)).append(" complaint &nbsp;•&nbsp; ").append(esc(c.id)).append(" &nbsp;•&nbsp; ").append(esc(c.location)).append("</p>");
        if (Store.workers.isEmpty())
            sb.append("<div class=\"assign-empty\">No workers added yet.<br>Go to Worker Details and create a worker profile first.</div>");
        List<Worker> sorted = new ArrayList<>(Store.workers);
        sorted.sort((x, y) -> Boolean.compare(y.category.equals(c.category), x.category.equals(c.category)));
        for (Worker w : sorted) {
            boolean match = w.category.equals(c.category);
            long act = Store.complaints.stream().filter(x -> x.workerId.equals(w.workerId) && x.active()).count();
            sb.append("<div class=\"worker-option").append(match ? " match" : "").append("\"><div class=\"worker-avatar\">")
              .append(esc(w.name.substring(0, 1).toUpperCase())).append("</div><div class=\"worker-option-info\"><h4>").append(esc(w.name))
              .append(match ? " <span class=\"match-badge\">BEST MATCH</span>" : "").append("</h4><p>ID: ").append(esc(w.workerId))
              .append(" • Dept: ").append(esc(Store.categoryLabel(w.category))).append("<br>").append(act).append(" active task")
              .append(act == 1 ? "" : "s").append("</p></div>")
              .append("<form method=\"post\" action=\"/admin/assign\"><input type=\"hidden\" name=\"id\" value=\"").append(esc(c.id))
              .append("\"><input type=\"hidden\" name=\"workerId\" value=\"").append(esc(w.workerId))
              .append("\"><button type=\"submit\" class=\"assign-pick-btn\">Assign</button></form></div>");
        }
        return modalPage("/admin?tab=" + (c.status.equals("Pending") ? "new" : "pending"), sb.toString());
    }

    static String confirmPage(String closeHref, String title, String text, String action, String hiddenName, String hiddenValue, String button) {
        return modalPage(closeHref, "<h2>" + title + "</h2><p class=\"modal-sub\">" + text + "</p>"
            + "<form method=\"post\" action=\"" + action + "\"><input type=\"hidden\" name=\"" + hiddenName + "\" value=\"" + esc(hiddenValue) + "\">"
            + "<button type=\"submit\" class=\"primary-btn\">" + button + "</button></form>"
            + "<a class=\"back-link\" style=\"justify-content:center;margin-top:14px\" href=\"" + closeHref + "\">Cancel</a>");
    }

    static String workerForm(Worker editing, Map<String, String> v, String message) {
        boolean edit = editing != null;
        Worker w = edit ? editing : new Worker();
        String name = v.getOrDefault("name", w.name), id = v.getOrDefault("workerId", w.workerId);
        String pw = v.getOrDefault("password", w.password), cat = v.getOrDefault("category", w.category);
        String contact = v.getOrDefault("contact", w.contact);
        return modalPage("/admin?tab=workers",
            "<h2>" + (edit ? "Edit Worker Profile" : "Create Worker Profile") + "</h2><p class=\"modal-sub\">Fill in the worker's details.</p>"
            + "<form method=\"post\" action=\"/admin/worker\"><input type=\"hidden\" name=\"editing\" value=\"" + (edit ? esc(editing.workerId) : "") + "\">"
            + field("Worker Name", "name", "text", name, "placeholder=\"Enter full name\" required")
            + field("Worker ID", "workerId", "text", id, "placeholder=\"e.g. W101\" required" + (edit ? " readonly" : ""))
            + field("Password", "password", "text", pw, "placeholder=\"Minimum 6 characters\" minlength=\"6\" required")
            + "<div class=\"modal-field\"><label for=\"category\">Category</label><select id=\"category\" name=\"category\" required>"
            + categoryOptions(cat, "Select category") + "</select></div>"
            + field("Contact Number", "contact", "tel", contact, "placeholder=\"10-digit mobile number\" pattern=\"[0-9]{10}\" maxlength=\"10\" required")
            + msg(message, "error")
            + "<button type=\"submit\" class=\"primary-btn\">" + (edit ? "Save Changes <i class=\"fa-solid fa-check\"></i>" : "Create Profile <i class=\"fa-solid fa-arrow-right\"></i>") + "</button></form>");
    }

    static String adminProfile(String message) {
        Admin a = Store.admin;
        return modalPage("/admin",
            "<h2>Edit Admin Profile</h2><p class=\"modal-sub\">Update your name, login ID or password.</p>"
            + "<form method=\"post\" action=\"/admin/profile\">"
            + field("Name", "name", "text", a.name, "required") + field("Admin ID", "adminId", "text", a.adminId, "required")
            + field("Password", "password", "text", a.password, "minlength=\"6\" required") + msg(message, "error")
            + "<button type=\"submit\" class=\"primary-btn\">Save Changes <i class=\"fa-solid fa-check\"></i></button></form>");
    }

    // ================================================================ worker
    static String workerDashboard(Worker me, String tab, String sub, String[] flash) {
        List<Complaint> mine = Store.complaints.stream().filter(c -> c.workerId.equals(me.workerId)).toList();
        List<Complaint> fresh = mine.stream().filter(c -> c.status.equals("Assigned")).toList();
        List<Complaint> prog = mine.stream().filter(c -> c.status.equals("In Progress")).toList();
        long solved = mine.stream().filter(Complaint::solved).count();
        List<Complaint> received = mine.stream().filter(c -> c.rating > 0).toList();
        List<Complaint> given = mine.stream().filter(c -> c.workerRating > 0).toList();
        String avg = avg(received, false);

        String profile = "<div class=\"profile-avatar\">" + esc(me.name.substring(0, 1).toUpperCase()) + "</div><h3>" + esc(me.name) + "</h3><p>"
            + esc(me.workerId) + "</p><div class=\"profile-divider\"></div>"
            + detail("fa-briefcase", "Category", Store.categoryLabel(me.category)) + detail("fa-phone", "Contact", me.contact)
            + "<a class=\"edit-profile-btn\" href=\"/worker/profile\"><i class=\"fa-solid fa-pen\"></i> Edit ID &amp; Password</a>";
        String navHtml = nav("/worker?tab=new", "fa-inbox", "New Complaints", String.valueOf(fresh.size()), tab.equals("new"))
            + nav("/worker?tab=pending", "fa-hourglass-half", "Pending Complaints", String.valueOf(prog.size()), tab.equals("pending"))
            + nav("/worker?tab=reviews", "fa-star", "Reviews", String.valueOf(received.size() + given.size()), tab.equals("reviews"));
        String stats = stat("purple", "fa-inbox", "New", String.valueOf(fresh.size()))
            + stat("orange", "fa-hourglass-half", "Pending", String.valueOf(prog.size()))
            + stat("green", "fa-circle-check", "Solved", String.valueOf(solved))
            + stat("blue", "fa-star", "Avg Rating", avg == null ? "-" : avg);

        String title, section;
        switch (tab) {
            case "pending" -> { title = "Pending Complaints"; section = workerList("Pending Complaints", "Work you have started but not finished.",
                "fa-hourglass-half", prog, false, "No Pending Complaints", "Complaints you have started but not finished will appear here."); }
            case "reviews" -> { title = "Reviews"; section = workerReviews(received, given, sub); }
            default -> { title = "New Complaints"; section = workerList("New Complaints", "Complaints assigned to you by the admin.",
                "fa-inbox", fresh, true, "No New Complaints", "Complaints assigned to you by the admin will appear here."); }
        }
        return shell("worker-portal", profile, "WORKER MENU", navHtml, "/worker/logout", "WORKER PORTAL", title, "Worker Account",
            "", stats, section, "WORKER PANEL", flash);
    }

    private static String workerList(String title, String sub, String icon, List<Complaint> list, boolean showStart,
                                     String emptyTitle, String emptyText) {
        StringBuilder sb = new StringBuilder("<section class=\"dashboard-section active\">").append(sectionHeading(title, sub, icon))
            .append("<div class=\"complaints-list\">");
        if (list.isEmpty()) sb.append(emptyState(icon, emptyTitle, emptyText));
        for (Complaint c : list) {
            String label = c.status.equals("Assigned") ? "New" : c.status;
            sb.append("<div class=\"complaint-card\">")
              .append(adminHead(c, "<span class=\"status-badge " + statusClass(c.status) + "\">" + esc(label) + "</span>", false))
              .append(studentInfoBox(c)).append("<p class=\"complaint-description\">").append(esc(c.description)).append("</p>")
              .append(photoImg(c.photo, "Complaint photo"))
              .append("<div class=\"complaint-card-bottom\">").append(meta(c, "fa-regular fa-calendar")).append("</div><div class=\"complaint-actions\">");
            if (showStart)
                sb.append("<form method=\"post\" action=\"/worker/start\"><input type=\"hidden\" name=\"id\" value=\"").append(esc(c.id))
                  .append("\"><button type=\"submit\" class=\"action-btn start-btn\"><i class=\"fa-solid fa-play\"></i> Start Work</button></form>");
            sb.append("<a class=\"action-btn done-btn\" href=\"/worker/complete?id=").append(esc(c.id))
              .append("\"><i class=\"fa-solid fa-circle-check\"></i> Work Completed</a></div></div>");
        }
        return sb.append("</div></section>").toString();
    }

    private static String reviewCard(Complaint c, int rating, String text, boolean behavior) {
        return "<div class=\"complaint-card\"><div class=\"complaint-card-top\"><div class=\"complaint-category\"><div class=\"category-icon\">"
            + "<i class=\"fa-solid fa-user-graduate\"></i></div><div><h3>" + esc(c.studentName) + "</h3><p>" + esc(c.category) + " • ID: " + esc(c.id)
            + "</p></div></div><span class=\"status-badge status-resolved\">" + rating + " / 5</span></div>"
            + "<div class=\"review-display\"><div class=\"review-stars\">" + stars(rating) + "</div>" + (behavior ? "<strong>Behavior</strong>" : "")
            + "<p>" + esc(text) + "</p></div></div>";
    }

    private static String workerReviews(List<Complaint> received, List<Complaint> given, String sub) {
        boolean showReceived = !sub.equals("given");
        StringBuilder sb = new StringBuilder("<section class=\"dashboard-section active\">")
            .append(sectionHeading("Reviews", "What students said about you, and the reviews you gave.", "fa-star"))
            .append("<div class=\"review-tabs\"><a class=\"tab-btn").append(showReceived ? " active" : "").append("\" href=\"/worker?tab=reviews&sub=received\">Reviews Received</a>")
            .append("<a class=\"tab-btn").append(showReceived ? "" : " active").append("\" href=\"/worker?tab=reviews&sub=given\">Reviews Given</a></div>")
            .append("<div class=\"complaints-list\">");
        if (showReceived) {
            if (received.isEmpty()) sb.append(emptyState("fa-star", "No Reviews Yet", "Reviews students give you after solved complaints will appear here."));
            else {
                double avg = received.stream().mapToInt(c -> c.rating).average().orElse(0);
                sb.append("<div class=\"rating-summary\"><div class=\"big-rating\">").append(String.format(Locale.ROOT, "%.1f", avg))
                  .append("</div><div><div class=\"summary-stars\">").append(stars((int) Math.round(avg))).append("</div><p>Overall rating from ")
                  .append(received.size()).append(" review").append(received.size() == 1 ? "" : "s").append("</p></div></div>");
                for (Complaint c : received) sb.append(reviewCard(c, c.rating, c.review, false));
            }
        } else {
            if (given.isEmpty()) sb.append(emptyState("fa-pen-to-square", "No Reviews Given Yet", "When you mark work as completed, your review of the student appears here."));
            for (Complaint c : given) sb.append(reviewCard(c, c.workerRating, c.workerReview, true));
        }
        return sb.append("</div></section>").toString();
    }

    static String completePage(Complaint c, String message) {
        return modalPage("/worker",
            "<h2>Work Completed</h2><p class=\"modal-sub\">" + esc(c.category) + " complaint &nbsp;•&nbsp; " + esc(c.id) + " &nbsp;•&nbsp; " + esc(c.location) + "</p>"
            + "<form method=\"post\" action=\"/worker/complete\" enctype=\"multipart/form-data\"><input type=\"hidden\" name=\"id\" value=\"" + esc(c.id) + "\">"
            + "<div class=\"modal-field\"><label>Photo Proof of Completed Work</label><label for=\"photo\" class=\"proof-upload\">"
            + "<i class=\"fa-solid fa-cloud-arrow-up\"></i><span>Choose a photo (PNG / JPG, max 2 MB)</span>"
            + "<input type=\"file\" id=\"photo\" name=\"photo\" accept=\"image/png,image/jpeg\" required style=\"display:block;margin-top:8px;max-width:100%\"></label></div>"
            + "<div class=\"modal-field\"><label>Rate the Student</label>" + starPicker("rating") + "</div>"
            + "<div class=\"modal-field\"><label for=\"review\">Describe the Student's Behavior</label>"
            + "<textarea id=\"review\" name=\"review\" rows=\"4\" maxlength=\"500\" placeholder=\"Was the student cooperative, polite, available at the location...?\" required></textarea></div>"
            + msg(message, "error")
            + "<button type=\"submit\" class=\"primary-btn\">Submit &amp; Mark Completed <i class=\"fa-solid fa-check\"></i></button></form>");
    }

    static String workerProfile(Worker w, String message) {
        return modalPage("/worker",
            "<h2>Edit Worker Profile</h2><p class=\"modal-sub\">Change your login ID or password.</p>"
            + "<form method=\"post\" action=\"/worker/profile\">"
            + field("Worker ID", "workerId", "text", w.workerId, "required") + field("Password", "password", "text", w.password, "minlength=\"6\" required")
            + msg(message, "error")
            + "<button type=\"submit\" class=\"primary-btn\">Save Changes <i class=\"fa-solid fa-check\"></i></button></form>");
    }
}
