package unireslove;

import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Small web helpers: sessions, request parsing (forms + file uploads) and responses. */
final class Web {
    private Web() {}

    static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    static final long MAX_BODY = 8L * 1024 * 1024;

    static String token() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    static String url(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }

    // ---------------------------------------------------------------- session
    static final class Session {
        final String id;
        String role = "";        // student | admin | worker
        String userId = "";
        String flash = "", flashType = "success";
        // forgot-password state (was resetStudentId / resetOtp ... in script.js)
        String otpStudentId = "", otpMethod = "contact", otp = "";
        long otpExpiry = 0;
        int otpAttempts = 0;

        Session(String id) { this.id = id; }

        void flash(String msg, String type) { flash = msg; flashType = type; }
        void flash(String msg) { flash(msg, "success"); }
        String[] takeFlash() {
            String[] r = {flash, flashType};
            flash = ""; flashType = "success";
            return r;
        }
        void logout() { role = ""; userId = ""; }
    }

    record FilePart(String filename, String contentType, byte[] data) {}

    // ---------------------------------------------------------------- request
    static final class Req {
        final String method, path;
        final Map<String, String> query = new HashMap<>();
        final Map<String, String> form = new HashMap<>();
        final Map<String, FilePart> files = new HashMap<>();
        Session session;
        boolean newSession;

        Req(HttpExchange ex) throws IOException {
            method = ex.getRequestMethod().toUpperCase();
            path = ex.getRequestURI().getPath();
            parseUrlEncoded(ex.getRequestURI().getRawQuery(), query);

            // session cookie
            String sid = null;
            for (String h : ex.getRequestHeaders().getOrDefault("Cookie", List.of())) {
                for (String part : h.split(";")) {
                    String[] kv = part.trim().split("=", 2);
                    if (kv.length == 2 && kv[0].equals("SID")) sid = kv[1];
                }
            }
            session = sid == null ? null : SESSIONS.get(sid);
            if (session == null) {
                session = new Session(token());
                SESSIONS.put(session.id, session);
                newSession = true;
            }

            if (method.equals("POST")) {
                byte[] body = readLimited(ex.getRequestBody());
                String ct = ex.getRequestHeaders().getFirst("Content-Type");
                if (ct != null && ct.toLowerCase().startsWith("multipart/form-data")) {
                    int i = ct.toLowerCase().indexOf("boundary=");
                    if (i >= 0) {
                        String boundary = ct.substring(i + 9).trim();
                        if (boundary.startsWith("\"")) boundary = boundary.substring(1, boundary.indexOf('"', 1));
                        parseMultipart(body, boundary);
                    }
                } else {
                    parseUrlEncoded(new String(body, StandardCharsets.UTF_8), form);
                }
            }
        }

        String q(String k) { return query.getOrDefault(k, ""); }
        String f(String k) { return form.getOrDefault(k, "").trim(); }
        /** Raw form value (passwords are not trimmed). */
        String raw(String k) { return form.getOrDefault(k, ""); }

        private static byte[] readLimited(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BODY) throw new IOException("Request too large");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }

        private static void parseUrlEncoded(String s, Map<String, String> into) {
            if (s == null || s.isEmpty()) return;
            for (String pair : s.split("&")) {
                String[] kv = pair.split("=", 2);
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                into.put(k, v);
            }
        }

        /** Minimal multipart/form-data parser (text fields + file uploads). */
        private void parseMultipart(byte[] body, String boundary) {
            String text = new String(body, StandardCharsets.ISO_8859_1);   // 1 char == 1 byte
            String delim = "--" + boundary;
            int pos = text.indexOf(delim);
            while (pos >= 0) {
                int start = pos + delim.length();
                if (text.startsWith("--", start)) break;                      // closing boundary
                if (text.startsWith("\r\n", start)) start += 2;
                int next = text.indexOf(delim, start);
                if (next < 0) break;
                int headerEnd = text.indexOf("\r\n\r\n", start);
                if (headerEnd > 0 && headerEnd < next) {
                    String headers = text.substring(start, headerEnd);
                    int dataEnd = next - 2;                                    // strip CRLF before boundary
                    if (dataEnd < headerEnd + 4) dataEnd = headerEnd + 4;
                    byte[] data = Arrays.copyOfRange(body, headerEnd + 4, dataEnd);
                    String name = headerParam(headers, "name");
                    String filename = headerParam(headers, "filename");
                    if (name != null) {
                        if (filename != null) {
                            String ctype = "";
                            for (String h : headers.split("\r\n"))
                                if (h.toLowerCase().startsWith("content-type:")) ctype = h.substring(13).trim();
                            if (filename.length() > 0 && data.length > 0)
                                files.put(name, new FilePart(
                                    new String(filename.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8),
                                    ctype, data));
                        } else {
                            form.put(name, new String(data, StandardCharsets.UTF_8));
                        }
                    }
                }
                pos = next;
            }
        }

        private static String headerParam(String headers, String key) {
            String h = "; " + headers.replace("\r\n", "; ");
            int i = h.indexOf("; " + key + "=\"");
            if (i < 0) return null;
            int s = i + key.length() + 4;
            int e = h.indexOf('"', s);
            return e < 0 ? null : h.substring(s, e);
        }
    }

    // --------------------------------------------------------------- response
    static final class Resp {
        int status = 200;
        String contentType = "text/html; charset=UTF-8";
        byte[] body = new byte[0];
        String location;

        static Resp html(String html) {
            Resp r = new Resp();
            r.body = html.getBytes(StandardCharsets.UTF_8);
            return r;
        }
        static Resp redirect(String to) {
            Resp r = new Resp();
            r.status = 303;
            r.location = to;
            return r;
        }
        static Resp bytes(byte[] data, String type) {
            Resp r = new Resp();
            r.body = data;
            r.contentType = type;
            return r;
        }
        static Resp notFound() {
            Resp r = html("<h1>404 - Page not found</h1><p><a href=\"/\">Back to uniReslove</a></p>");
            r.status = 404;
            return r;
        }

        void send(HttpExchange ex, Req req) throws IOException {
            if (req != null && req.newSession)
                ex.getResponseHeaders().add("Set-Cookie", "SID=" + req.session.id + "; Path=/; HttpOnly; SameSite=Lax");
            ex.getResponseHeaders().set("Content-Type", contentType);
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            if (location != null) ex.getResponseHeaders().set("Location", location);
            ex.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) try (OutputStream os = ex.getResponseBody()) { os.write(body); }
            ex.close();
        }
    }
}
