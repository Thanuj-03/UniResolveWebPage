package unireslove;

/** Plain data classes (these replace the JSON objects that were kept in localStorage). */
class Student {
    String name = "", studentId = "", email = "", password = "", hostel = "", room = "", contact = "";
}

class Worker {
    String name = "", workerId = "", password = "", category = "", contact = "";
}

class Admin {
    String name = "Admin", adminId = "admin", password = "admin123";
}

class Complaint {
    String id = "", studentId = "", studentName = "", hostel = "", room = "", contact = "";
    String category = "", location = "", description = "", photo = "";
    String status = "Pending", workerName = "", workerId = "", date = "";
    String review = "", completionPhoto = "", workerReview = "", completedDate = "";
    int rating = 0, workerRating = 0;

    boolean solved() { return status.equals("Resolved") || status.equals("Solved"); }
    boolean active() { return status.equals("Assigned") || status.equals("In Progress"); }
}
