import java.sql.*;
import java.sql.Date;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class EventValidationService {
    private Connection conn;
    private Map<Integer, Course> courses;
    private Map<Integer, Room> rooms;
    private Map<Integer, Professor> professors;
    private Map<Integer, AcademicEvent> academicEvents;
    private List<Holiday> holidays;

    public EventValidationService(Connection connection) {
        this.conn = connection;
        this.courses = new HashMap<>();
        this.rooms = new HashMap<>();
        this.professors = new HashMap<>();
        this.academicEvents = new HashMap<>();
        this.holidays = new ArrayList<>();
        loadDataFromDatabase();
    }

    private void loadDataFromDatabase() {
        try {
            loadCourses();
            loadRooms();
            loadProfessors();
            loadAcademicEvents();
            loadHolidays();
        } catch (SQLException e) {
            System.err.println("Error loading data: " + e.getMessage());
        }
    }

    private void loadCourses() throws SQLException {
        String query = "SELECT id, name, semester, code FROM course";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            int count = 0;
            while (rs.next()) {
                Course course = new Course();
                course.idCourse = rs.getInt("id");
                course.name = rs.getString("name");
                course.semester = rs.getInt("semester");
                course.code = rs.getString("code");
                courses.put(course.idCourse, course);
                count++;
            }
            System.out.println("Loaded courses: " + count);
        }
    }

    private void loadRooms() throws SQLException {
        String query = "SELECT id, code, capacity, is_computer_lab, is_active FROM room";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            int count = 0;
            while (rs.next()) {
                Room room = new Room();
                room.idRoom = rs.getInt("id");
                room.code = rs.getString("code");
                room.capacity = rs.getInt("capacity");
                room.isComputerLab = rs.getBoolean("is_computer_lab");
                room.isActive = rs.getBoolean("is_active");
                rooms.put(room.idRoom, room);
                count++;
            }
            System.out.println("Loaded rooms: " + count);
        }
    }

    private void loadProfessors() throws SQLException {
        String query = "SELECT id, full_name, email, is_active FROM professor";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            int count = 0;
            while (rs.next()) {
                Professor professor = new Professor();
                professor.idProfessor = rs.getInt("id");
                professor.fullName = rs.getString("full_name");
                professor.email = rs.getString("email");
                professor.isActive = rs.getBoolean("is_active");
                professors.put(professor.idProfessor, professor);
                count++;
            }
            System.out.println("Loaded professors: " + count);
        }
    }

    private void loadAcademicEvents() throws SQLException {
        String query = "SELECT id, course_id, created_by_professor, type_enum, starts_at, ends_at, " +
                "is_online, room_id, notes, is_published, locked_by_admin, schedule_id, day FROM academic_event";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            int count = 0;
            while (rs.next()) {
                AcademicEvent event = new AcademicEvent();
                event.idAcademicEvent = rs.getInt("id");
                event.idCourse = rs.getInt("course_id");
                event.idProfessor = (int) rs.getLong("created_by_professor");
                event.typeEnum = rs.getString("type_enum");
                event.day = rs.getString("day");
                event.notes = rs.getString("notes");
                event.isOnline = rs.getBoolean("is_online");
                event.isPublished = rs.getBoolean("is_published");
                event.lockedByAdmin = rs.getBoolean("locked_by_admin");
                event.scheduleId = rs.getInt("schedule_id");
                event.idRoom = rs.getInt("room_id");

                Timestamp startTs = rs.getTimestamp("starts_at");
                Timestamp endTs = rs.getTimestamp("ends_at");

                if (startTs != null) {
                    LocalDateTime startLdt = startTs.toLocalDateTime();
                    event.startsAt = startLdt;
                    event.date = startLdt.toLocalDate();
                    event.startTime = startLdt.toLocalTime();
                }
                if (endTs != null) {
                    LocalDateTime endLdt = endTs.toLocalDateTime();
                    event.endsAt = endLdt;
                    event.endTime = endLdt.toLocalTime();
                }

                academicEvents.put(event.idAcademicEvent, event);
                count++;
            }
            System.out.println("Loaded academic events: " + count);
        }
    }

    private void loadHolidays() throws SQLException {
        String query = "SELECT id, name, date FROM holiday";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            int count = 0;
            while (rs.next()) {
                Holiday holiday = new Holiday();
                holiday.idHoliday = rs.getInt("id");
                holiday.name = rs.getString("name");
                holiday.date = rs.getDate("date").toLocalDate();
                holidays.add(holiday);
                count++;
            }
            System.out.println("Loaded holidays: " + count);
        }
    }

    private int generateNewScheduleId() throws SQLException {
        // Prvo generišemo novi ID
        String queryMaxId = "SELECT COALESCE(MAX(id), 0) + 1 as novi_id FROM schedule";
        int newId = 1;

        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(queryMaxId)) {
            if (rs.next()) {
                newId = rs.getInt("novi_id");
            }
        }

        // Kreiramo zapis u schedule tabeli
        String insertSchedule = "INSERT INTO schedule (id, name) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSchedule)) {
            pstmt.setInt(1, newId);
            pstmt.setString(2, "Auto-generated schedule " + newId + " - " +
                    java.time.LocalDateTime.now()
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            pstmt.executeUpdate();
        }

        return newId;
    }

    private void saveToAcademicEvent(int scheduleId, int courseId, int professorId, String day,
            LocalDateTime startsAt, LocalDateTime endsAt, int roomId) throws SQLException {
        String insert = "INSERT INTO academic_event " +
                "(course_id, created_by_professor, type_enum, starts_at, ends_at, " +
                "is_online, room_id, notes, is_published, locked_by_admin, schedule_id, day) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
            pstmt.setInt(1, courseId);
            pstmt.setLong(2, professorId);
            pstmt.setString(3, "LECTURE");
            pstmt.setTimestamp(4, Timestamp.valueOf(startsAt));
            pstmt.setTimestamp(5, Timestamp.valueOf(endsAt));
            pstmt.setBoolean(6, false);
            pstmt.setInt(7, roomId);
            pstmt.setNull(8, java.sql.Types.VARCHAR);
            pstmt.setBoolean(9, true);
            pstmt.setBoolean(10, false);
            pstmt.setInt(11, scheduleId);
            pstmt.setString(12, day);
            pstmt.executeUpdate();
        }
    }

    private LocalDateTime convertDayToDate(String day, LocalTime time) {
        LocalDate today = LocalDate.now();
        DayOfWeek targetDay = parseDayOfWeek(day);
        LocalDate targetDate = today.with(TemporalAdjusters.nextOrSame(targetDay));
        return LocalDateTime.of(targetDate, time);
    }

    private DayOfWeek parseDayOfWeek(String day) {
        switch (day.toLowerCase()) {
            case "ponedeljak":
            case "monday":
                return DayOfWeek.MONDAY;
            case "utorak":
            case "tuesday":
                return DayOfWeek.TUESDAY;
            case "srijeda":
            case "sreda":
            case "wednesday":
                return DayOfWeek.WEDNESDAY;
            case "cetvrtak":
            case "thursday":
                return DayOfWeek.THURSDAY;
            case "petak":
            case "friday":
                return DayOfWeek.FRIDAY;
            case "subota":
            case "saturday":
                return DayOfWeek.SATURDAY;
            case "nedelja":
            case "nedjelja":
            case "sunday":
                return DayOfWeek.SUNDAY;
            default:
                return DayOfWeek.MONDAY;
        }
    }

    private boolean hasConflict(String day, LocalDate date, int roomId, int professorId,
            LocalTime startTime, LocalTime endTime) {
        for (AcademicEvent event : academicEvents.values()) {
            boolean dayMatch = (day != null && event.day != null && event.day.equals(day));
            boolean dateMatch = (date != null && event.date != null && event.date.equals(date));

            if (dayMatch || dateMatch) {
                boolean roomMatch = (event.idRoom == roomId);
                boolean professorMatch = (event.idProfessor == professorId);

                if (roomMatch || professorMatch) {
                    if (startTime != null && endTime != null && event.startTime != null && event.endTime != null) {
                        if (startTime.isBefore(event.endTime) && endTime.isAfter(event.startTime) &&
                                !startTime.equals(endTime)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private String addTeachingTerm(int courseId, int roomId, int professorId, String day,
            String startTimeStr, String endTimeStr, String typeEnum, String requiredRoomType) {
        try {
            Course course = courses.get(courseId);
            if (course == null) {
                return "ERROR: Course does not exist";
            }

            Room room = rooms.get(roomId);
            if (room == null) {
                return "ERROR: Room does not exist";
            }

            Professor professor = professors.get(professorId);
            if (professor == null) {
                return "ERROR: Professor does not exist";
            }

            if (!professor.isActive) {
                return "ERROR: Professor is not active";
            }

            LocalTime startTime = parseTime(startTimeStr);
            LocalTime endTime = parseTime(endTimeStr);

            if (room.capacity < 0) {
                return "ERROR: Room capacity is invalid";
            }

            // Proveravamo samo da li je sala aktivna
            if (!room.isActive) {
                return "ERROR: Room is not active";
            }

            if (hasConflict(day, null, roomId, professorId, startTime, endTime)) {
                for (AcademicEvent event : academicEvents.values()) {
                    if (event.day != null && event.day.equals(day)) {
                        if (startTime.isBefore(event.endTime) && endTime.isAfter(event.startTime)) {
                            if (event.idRoom == roomId) {
                                return "ERROR: Room is occupied at that time";
                            }
                            if (event.idProfessor == professorId) {
                                return "ERROR: Professor is occupied at that time";
                            }
                        }
                    }
                }
            }

            String insert = "INSERT INTO academic_event (course_id, created_by_professor, type_enum, " +
                    "starts_at, ends_at, is_online, room_id, is_published, locked_by_admin, day) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
                LocalDateTime startsAt = convertDayToDate(day, startTime);
                LocalDateTime endsAt = convertDayToDate(day, endTime);

                pstmt.setInt(1, courseId);
                pstmt.setLong(2, professorId);
                pstmt.setString(3, typeEnum);
                pstmt.setTimestamp(4, Timestamp.valueOf(startsAt));
                pstmt.setTimestamp(5, Timestamp.valueOf(endsAt));
                pstmt.setBoolean(6, false);
                pstmt.setInt(7, roomId);
                pstmt.setBoolean(8, true);
                pstmt.setBoolean(9, false);
                pstmt.setString(10, day);
                pstmt.executeUpdate();

                AcademicEvent newEvent = new AcademicEvent();
                newEvent.idCourse = courseId;
                newEvent.idRoom = roomId;
                newEvent.idProfessor = professorId;
                newEvent.day = day;
                newEvent.startTime = startTime;
                newEvent.endTime = endTime;
                newEvent.typeEnum = typeEnum;
                newEvent.date = startsAt.toLocalDate();
                newEvent.startsAt = startsAt;
                newEvent.endsAt = endsAt;
                academicEvents.put(newEvent.idAcademicEvent, newEvent);
            }

            return "OK: " + typeEnum + " successfully added";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public String addLecture(int courseId, int roomId, int professorId, String day,
            String startTime, String endTime) {
        return addTeachingTerm(courseId, roomId, professorId, day, startTime, endTime, "LECTURE", "predavaliste");
    }

    public String addExercise(int courseId, int roomId, int professorId, String day,
            String startTime, String endTime) {
        return addTeachingTerm(courseId, roomId, professorId, day, startTime, endTime, "EXERCISE", "vjezbe");
    }

    public String addColloquium(int courseId, int roomId, int professorId, int supervisorProfessorId,
            String date, String startTimeStr, String endTimeStr) {
        try {
            Course course = courses.get(courseId);
            if (course == null) {
                return "ERROR: Course does not exist";
            }

            Room room = rooms.get(roomId);
            if (room == null) {
                return "ERROR: Room does not exist";
            }

            Professor professor = professors.get(professorId);
            if (professor == null) {
                return "ERROR: Professor does not exist";
            }

            Professor supervisor = professors.get(supervisorProfessorId);
            if (supervisor == null) {
                return "ERROR: Supervisor professor does not exist";
            }

            if (!professor.isActive || !supervisor.isActive) {
                return "ERROR: One or more professors are not active";
            }

            LocalDate colloquiumDate = LocalDate.parse(date);
            LocalTime startTime = parseTime(startTimeStr);
            LocalTime endTime = parseTime(endTimeStr);

            if (colloquiumDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                return "ERROR: Colloquium cannot be scheduled on Sunday";
            }

            for (Holiday holiday : holidays) {
                if (holiday.date.equals(colloquiumDate)) {
                    return "ERROR: Colloquium cannot be on holiday: " + holiday.name;
                }
            }

            LocalDate weekStart = colloquiumDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEnd = colloquiumDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
            int colloquia = 0;
            for (AcademicEvent event : academicEvents.values()) {
                if ("COLLOQUIUM".equals(event.typeEnum) && event.date != null &&
                        !event.date.isBefore(weekStart) && !event.date.isAfter(weekEnd)) {
                    colloquia++;
                }
            }

            if (colloquia >= 2) {
                return "ERROR: Maximum 2 colloquia per week allowed";
            }

            if (room.capacity <= 0) {
                return "ERROR: Room capacity is invalid";
            }

            if (hasConflict(null, colloquiumDate, roomId, professorId, startTime, endTime)) {
                for (AcademicEvent event : academicEvents.values()) {
                    if (event.date != null && event.date.equals(colloquiumDate)) {
                        if (startTime.isBefore(event.endTime) && endTime.isAfter(event.startTime)) {
                            if (event.idProfessor == professorId) {
                                return "ERROR: Professor is occupied at that time";
                            }
                            if (event.idRoom == roomId) {
                                return "ERROR: Room is occupied at that time";
                            }
                        }
                    }
                }
            }

            int supervisorCount = 0;
            for (AcademicEvent event : academicEvents.values()) {
                if ("COLLOQUIUM".equals(event.typeEnum) && event.idProfessor == supervisorProfessorId) {
                    supervisorCount++;
                }
            }

            if (supervisorCount >= 5) {
                return "WARNING: Supervisor already has " + supervisorCount + " colloquium supervisions";
            }

            String insert = "INSERT INTO academic_event (course_id, created_by_professor, type_enum, " +
                    "starts_at, ends_at, is_online, room_id, is_published, locked_by_admin) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
                LocalDateTime startsAt = LocalDateTime.of(colloquiumDate, startTime);
                LocalDateTime endsAt = LocalDateTime.of(colloquiumDate, endTime);

                pstmt.setInt(1, courseId);
                pstmt.setLong(2, professorId);
                pstmt.setString(3, "COLLOQUIUM");
                pstmt.setTimestamp(4, Timestamp.valueOf(startsAt));
                pstmt.setTimestamp(5, Timestamp.valueOf(endsAt));
                pstmt.setBoolean(6, false);
                pstmt.setInt(7, roomId);
                pstmt.setBoolean(8, true);
                pstmt.setBoolean(9, false);
                pstmt.executeUpdate();

                AcademicEvent newEvent = new AcademicEvent();
                newEvent.idCourse = courseId;
                newEvent.idRoom = roomId;
                newEvent.idProfessor = professorId;
                newEvent.typeEnum = "COLLOQUIUM";
                newEvent.date = colloquiumDate;
                newEvent.startTime = startTime;
                newEvent.endTime = endTime;
                newEvent.startsAt = LocalDateTime.of(colloquiumDate, startTime);
                newEvent.endsAt = LocalDateTime.of(colloquiumDate, endTime);
                academicEvents.put(newEvent.idAcademicEvent, newEvent);
            }

            return "OK: Colloquium successfully added";
        } catch (DateTimeParseException e) {
            return "ERROR: Invalid date format (YYYY-MM-DD) or time format (HH:mm:ss)";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public String addExam(int courseId, int roomId, int professorId, String date,
            String startTimeStr, String endTimeStr, String examType) {
        try {
            Course course = courses.get(courseId);
            if (course == null) {
                return "ERROR: Course does not exist";
            }

            Room room = rooms.get(roomId);
            if (room == null) {
                return "ERROR: Room does not exist";
            }

            Professor professor = professors.get(professorId);
            if (professor == null) {
                return "ERROR: Professor does not exist";
            }

            if (!professor.isActive) {
                return "ERROR: Professor is not active";
            }

            LocalDate examDate = LocalDate.parse(date);
            LocalTime startTime = parseTime(startTimeStr);
            LocalTime endTime = parseTime(endTimeStr);

            if (examDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                return "ERROR: Exam cannot be scheduled on Sunday";
            }

            for (Holiday holiday : holidays) {
                if (holiday.date.equals(examDate)) {
                    return "ERROR: Exam cannot be scheduled on holiday: " + holiday.name;
                }
            }

            if (room.capacity <= 0) {
                return "ERROR: Room capacity is invalid";
            }

            for (AcademicEvent event : academicEvents.values()) {
                if (event.idProfessor == professorId && event.date != null && event.date.equals(examDate)) {
                    if (event.typeEnum.contains("EXAM") || event.typeEnum.contains("COLLOQUIUM")) {
                        if (startTime.isBefore(event.endTime) && endTime.isAfter(event.startTime)) {
                            return "ERROR: Professor is already assigned to another exam at that time";
                        }
                    }
                }

                if (event.idProfessor == professorId &&
                        (event.typeEnum.equals("LECTURE") || event.typeEnum.equals("EXERCISE"))) {
                    if (event.day != null && event.date == null) {
                        LocalDate nextOccurrence = examDate.with(
                                TemporalAdjusters.previousOrSame(DayOfWeek.valueOf(event.day.toUpperCase())));
                        if (nextOccurrence.equals(examDate) &&
                                startTime.isBefore(event.endTime) && endTime.isAfter(event.startTime)) {
                            return "ERROR: Professor has " + event.typeEnum + " on " + event.day;
                        }
                    } else if (event.date != null && event.date.equals(examDate)) {
                        if (startTime.isBefore(event.endTime) && endTime.isAfter(event.startTime)) {
                            return "ERROR: Professor has " + event.typeEnum + " at that time";
                        }
                    }
                }
            }

            if (hasConflict(null, examDate, roomId, professorId, startTime, endTime)) {
                for (AcademicEvent event : academicEvents.values()) {
                    if (event.date != null && event.date.equals(examDate)) {
                        if (startTime.isBefore(event.endTime) && endTime.isAfter(event.startTime)) {
                            if (event.idRoom == roomId) {
                                return "ERROR: Room is occupied at that time";
                            }
                            if (event.idProfessor == professorId) {
                                return "ERROR: Professor is occupied at that time";
                            }
                        }
                    }
                }
            }

            String insert = "INSERT INTO academic_event (course_id, created_by_professor, type_enum, " +
                    "starts_at, ends_at, is_online, room_id, is_published, locked_by_admin) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
                LocalDateTime startsAt = LocalDateTime.of(examDate, startTime);
                LocalDateTime endsAt = LocalDateTime.of(examDate, endTime);

                pstmt.setInt(1, courseId);
                pstmt.setLong(2, professorId);
                pstmt.setString(3, examType);
                pstmt.setTimestamp(4, Timestamp.valueOf(startsAt));
                pstmt.setTimestamp(5, Timestamp.valueOf(endsAt));
                pstmt.setBoolean(6, false);
                pstmt.setInt(7, roomId);
                pstmt.setBoolean(8, true);
                pstmt.setBoolean(9, false);
                pstmt.executeUpdate();

                AcademicEvent newEvent = new AcademicEvent();
                newEvent.idCourse = courseId;
                newEvent.idRoom = roomId;
                newEvent.idProfessor = professorId;
                newEvent.typeEnum = examType;
                newEvent.date = examDate;
                newEvent.startTime = startTime;
                newEvent.endTime = endTime;
                newEvent.startsAt = LocalDateTime.of(examDate, startTime);
                newEvent.endsAt = LocalDateTime.of(examDate, endTime);
                academicEvents.put(newEvent.idAcademicEvent, newEvent);
            }

            return "OK: Exam successfully added";
        } catch (DateTimeParseException e) {
            return "ERROR: Invalid date format (YYYY-MM-DD) or time format (HH:mm:ss)";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public String checkCourseHours(int courseId) {
        Course course = courses.get(courseId);
        if (course == null) {
            return "ERROR: Course does not exist";
        }

        int lectureCount = 0;
        int exerciseCount = 0;

        for (AcademicEvent event : academicEvents.values()) {
            if (event.idCourse == courseId) {
                if ("LECTURE".equals(event.typeEnum)) {
                    lectureCount++;
                } else if ("EXERCISE".equals(event.typeEnum)) {
                    exerciseCount++;
                }
            }
        }

        StringBuilder info = new StringBuilder();
        info.append("COURSE: ").append(course.name).append("\n");
        info.append("CODE: ").append(course.code).append("\n");
        info.append("SEMESTER: ").append(course.semester).append("\n");
        info.append("--- EVENT COUNT ---\n");
        info.append("LECTURES: ").append(lectureCount).append("\n");
        info.append("EXERCISES: ").append(exerciseCount).append("\n");

        return info.toString();
    }

    public String generateCompleteSchedule() {
        try {
            System.out.println("=== STARTING AUTOMATIC COMPLETE SCHEDULE GENERATION ===\n");

            int scheduleId = generateNewScheduleId();
            System.out.println("Generated schedule_id: " + scheduleId + "\n");

            Map<Integer, Double> professorFlexibility = analyzeProfessorFlexibility();
            List<CoursePriority> priorities = determinePriorities(professorFlexibility);

            System.out.println("--- COURSE PRIORITIES ---");
            for (CoursePriority cp : priorities) {
                System.out.printf("Course: %-30s | Priority: %.2f\n",
                        cp.course.name, cp.priority);
            }

            int successfulLectures = 0;
            int partialLectures = 0;
            int failedLectures = 0;
            int successfulExercises = 0;
            int partialExercises = 0;
            int failedExercises = 0;

            StringBuilder details = new StringBuilder();
            details.append("\n\n=== GENERATION DETAILS ===\n");

            for (CoursePriority cp : priorities) {
                Course course = cp.course;
                details.append("\n--- ").append(course.name).append(" (ID: ").append(course.idCourse)
                        .append(") ---\n");

                String lectureResult = generateLectureSchedule(course.idCourse, scheduleId);
                details.append(" [LECTURES] ").append(lectureResult).append("\n");
                if (lectureResult.startsWith("OK")) {
                    successfulLectures++;
                } else if (lectureResult.startsWith("WARNING")) {
                    partialLectures++;
                } else {
                    failedLectures++;
                }

                String exerciseResult = generateExerciseSchedule(course.idCourse, scheduleId);
                details.append(" [EXERCISES] ").append(exerciseResult).append("\n");
                if (exerciseResult.startsWith("OK")) {
                    successfulExercises++;
                } else if (exerciseResult.startsWith("WARNING")) {
                    partialExercises++;
                } else {
                    failedExercises++;
                }
            }

            System.out.println(details.toString());

            String summary = String.format(
                    "\n=== SCHEDULE GENERATION COMPLETED ===\n" +
                            "SCHEDULE_ID: %d\n" +
                            "\nLECTURES:\n" +
                            " ✓ Successful: %d\n" +
                            " ⚠ Partial: %d\n" +
                            " ✗ Failed: %d\n" +
                            "\nEXERCISES:\n" +
                            " ✓ Successful: %d\n" +
                            " ⚠ Partial: %d\n" +
                            " ✗ Failed: %d\n" +
                            "\nTOTAL COURSES PROCESSED: %d\n" +
                            "================================================",
                    scheduleId, successfulLectures, partialLectures, failedLectures,
                    successfulExercises, partialExercises, failedExercises, priorities.size());

            System.out.println(summary);

            loadAcademicEvents();

            if (failedLectures > 0 || failedExercises > 0) {
                return "WARNING: Schedule partially generated (schedule_id: " + scheduleId +
                        "). Check details above.";
            }

            return "OK: Complete schedule successfully generated for all courses (schedule_id: " + scheduleId + ")!";
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    private Map<Integer, Double> analyzeProfessorFlexibility() {
        Map<Integer, Double> flexibility = new HashMap<>();

        for (Professor prof : professors.values()) {
            double score = 1.0;

            try {
                // String queryPref = "SELECT COUNT(DISTINCT day) as broj_dana FROM
                // professor_availability " +
                // "WHERE id = ?"; greska: nije day vec weekday
                String queryPref = "SELECT COUNT(DISTINCT weekday) as broj_dana FROM professor_availability " +
                        "WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(queryPref)) {
                    ps.setInt(1, prof.idProfessor);
                    try (ResultSet rs = ps.executeQuery()) {
                        int preferredDays = 0;
                        if (rs.next()) {
                            preferredDays = rs.getInt("broj_dana");
                        }

                        if (preferredDays == 0) {
                            score *= 0.8;
                        } else if (preferredDays == 1) {
                            score *= 0.3;
                        } else if (preferredDays == 2) {
                            score *= 0.5;
                        } else if (preferredDays == 3) {
                            score *= 0.7;
                        } else {
                            score *= 1.0;
                        }
                    }
                }

                int busyTerms = 0;
                for (AcademicEvent event : academicEvents.values()) {
                    if (event.idProfessor == prof.idProfessor) {
                        busyTerms++;
                    }
                }

                if (busyTerms == 0) {
                    score *= 1.0;
                } else if (busyTerms <= 4) {
                    score *= 0.9;
                } else if (busyTerms <= 9) {
                    score *= 0.7;
                } else if (busyTerms <= 14) {
                    score *= 0.5;
                } else if (busyTerms <= 19) {
                    score *= 0.3;
                } else {
                    score *= 0.1;
                }
            } catch (SQLException e) {
                System.err.println("Error analyzing professor " + prof.idProfessor + ": " + e.getMessage());
                score = 0.5;
            }

            flexibility.put(prof.idProfessor, score);
        }

        return flexibility;
    }

    private List<CoursePriority> determinePriorities(Map<Integer, Double> professorFlexibility) {
        List<CoursePriority> priorities = new ArrayList<>();

        for (Course course : courses.values()) {
            try {
                CoursePriority cp = new CoursePriority();
                cp.course = course;

                String queryLec = "SELECT professor_id FROM course_professor WHERE course_id = ? " +
                        "AND is_assistant = false";
                try (PreparedStatement ps = conn.prepareStatement(queryLec)) {
                    ps.setInt(1, course.idCourse);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int profId = rs.getInt("professor_id");
                            cp.primaryProfessor = professors.get(profId);
                            cp.primaryFlexibility = professorFlexibility.getOrDefault(profId, 0.5);
                        }
                    }
                }

                String queryEx = "SELECT professor_id FROM course_professor WHERE course_id = ? " +
                        "AND is_assistant = true";
                try (PreparedStatement ps = conn.prepareStatement(queryEx)) {
                    ps.setInt(1, course.idCourse);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int profId = rs.getInt("professor_id");
                            cp.secondaryProfessor = professors.get(profId);
                            cp.secondaryFlexibility = professorFlexibility.getOrDefault(profId, 0.5);
                        }
                    }
                }

                double avgFlexibility = (cp.primaryFlexibility + cp.secondaryFlexibility) / 2.0;
                cp.priority = 1.0 - avgFlexibility;

                if (cp.primaryProfessor == null) {
                    cp.priority += 0.5;
                }
                if (cp.secondaryProfessor == null) {
                    cp.priority += 0.5;
                }

                // Dodaj samo predmete koji imaju bar jednog profesora ili asistenta
                if (cp.primaryProfessor != null || cp.secondaryProfessor != null) {
                    priorities.add(cp);
                }
            } catch (SQLException e) {
                System.err.println("Error determining priority for course " + course.idCourse + ": " +
                        e.getMessage());
            }
        }

        priorities.sort((a, b) -> Double.compare(b.priority, a.priority));
        return priorities;
    }

    public String generateLectureSchedule(int courseId) {
        return generateLectureSchedule(courseId, null);
    }

    public String generateLectureSchedule(int courseId, Integer scheduleIdParam) {
        try {
            Course course = courses.get(courseId);
            if (course == null) {
                return "ERROR: Course does not exist";
            }

            int scheduleId = (scheduleIdParam != null) ? scheduleIdParam : generateNewScheduleId();

            String queryProf = "SELECT professor_id FROM course_professor WHERE course_id = ? " +
                    "AND is_assistant = false";
            int professorId = 0;
            try (PreparedStatement ps = conn.prepareStatement(queryProf)) {
                ps.setInt(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        professorId = (int) rs.getLong("professor_id");
                    } else {
                        return "ERROR: No professor assigned for lectures";
                    }
                }
            }

            Professor professor = professors.get(professorId);
            if (professor == null || !professor.isActive) {
                return "ERROR: Professor not found or not active";
            }

            List<String> preferredDays = new ArrayList<>();
            Map<String, String> preferredTimes = new HashMap<>();

            // String queryPref = "SELECT day, starts_at FROM professor_availability WHERE
            // id = ?";
            String queryPref = "SELECT weekday, start_time FROM professor_availability WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(queryPref)) {
                ps.setInt(1, professorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String day = rs.getString("weekday");
                        Time time = rs.getTime("start_time");
                        preferredDays.add(day);
                        preferredTimes.put(day, time != null ? time.toString() : "09:00:00");
                    }
                }
            }

            if (preferredDays.isEmpty()) {
                preferredDays.addAll(Arrays.asList("ponedeljak", "utorak", "srijeda"));
            }

            List<Room> suitableRooms = new ArrayList<>();
            for (Room room : rooms.values()) {
                // Proveravamo da li je sala aktivna i da li ima dovoljno kapaciteta
                if (room.isActive && room.capacity >= 30) {
                    suitableRooms.add(room);
                }
            }

            if (suitableRooms.isEmpty()) {
                return "ERROR: No suitable rooms for lectures";
            }

            int addedTerms = 0;
            for (String day : preferredDays) {
                for (Room room : suitableRooms) {
                    String timeStr = preferredTimes.getOrDefault(day, "09:00:00");
                    LocalTime startTime = parseTime(timeStr);
                    LocalTime endTime = startTime.plusHours(1);

                    if (!hasConflict(day, null, room.idRoom, professorId, startTime, endTime)) {
                        LocalDateTime startsAt = convertDayToDate(day, startTime);
                        LocalDateTime endsAt = convertDayToDate(day, endTime);

                        saveToAcademicEvent(scheduleId, courseId, professorId, day, startsAt, endsAt, room.idRoom);

                        AcademicEvent newEvent = new AcademicEvent();
                        newEvent.idCourse = courseId;
                        newEvent.idRoom = room.idRoom;
                        newEvent.idProfessor = professorId;
                        newEvent.day = day;
                        newEvent.startTime = startTime;
                        newEvent.endTime = endTime;
                        newEvent.typeEnum = "LECTURE";
                        newEvent.scheduleId = scheduleId;
                        academicEvents.put(newEvent.idAcademicEvent, newEvent);
                        addedTerms++;
                        break;
                    }
                }
            }

            return "OK: Lectures scheduled (" + addedTerms + " terms, schedule_id: " + scheduleId + ")";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    public String generateExerciseSchedule(int courseId) {
        return generateExerciseSchedule(courseId, null);
    }

    public String generateExerciseSchedule(int courseId, Integer scheduleIdParam) {
        try {
            Course course = courses.get(courseId);
            if (course == null) {
                return "ERROR: Course does not exist";
            }

            int scheduleId = (scheduleIdParam != null) ? scheduleIdParam : generateNewScheduleId();

            // Prvo pokušavamo da pronađemo asistenta
            String queryAssistant = "SELECT professor_id FROM course_professor WHERE course_id = ? " +
                    "AND is_assistant = true";
            int professorId = 0;
            boolean hasAssistant = false;

            try (PreparedStatement ps = conn.prepareStatement(queryAssistant)) {
                ps.setInt(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        professorId = rs.getInt("professor_id");
                        hasAssistant = true;
                    }
                }
            }

            // Ako nema asistenta, koristimo profesora
            if (!hasAssistant) {
                String queryProfessor = "SELECT professor_id FROM course_professor WHERE course_id = ? " +
                        "AND is_assistant = false";
                try (PreparedStatement ps = conn.prepareStatement(queryProfessor)) {
                    ps.setInt(1, courseId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            professorId = rs.getInt("professor_id");
                        } else {
                            return "ERROR: No professor assigned for exercises";
                        }
                    }
                }
            }

            Professor professor = professors.get(professorId);
            if (professor == null || !professor.isActive) {
                return "ERROR: Professor not found or not active";
            }

            List<String> preferredDays = new ArrayList<>();
            Map<String, String> preferredTimes = new HashMap<>();

            String queryPref = "SELECT weekday, start_time FROM professor_availability WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(queryPref)) {
                ps.setInt(1, professorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String day = rs.getString("weekday");
                        Time time = rs.getTime("start_time");
                        preferredDays.add(day);
                        preferredTimes.put(day, time != null ? time.toString() : "10:00:00");
                    }
                }
            }

            if (preferredDays.isEmpty()) {
                preferredDays.addAll(Arrays.asList("utorak", "cetvrtak"));
            }

            List<Room> suitableRooms = new ArrayList<>();
            for (Room room : rooms.values()) {
                // Proveravamo da li je sala aktivna (za vežbe mogu i kompjuterske i obične
                // sale)
                if (room.isActive && room.capacity >= 20) {
                    suitableRooms.add(room);
                }
            }

            if (suitableRooms.isEmpty()) {
                return "ERROR: No suitable rooms for exercises";
            }

            int addedTerms = 0;
            for (String day : preferredDays) {
                for (Room room : suitableRooms) {
                    String timeStr = preferredTimes.getOrDefault(day, "10:00:00");
                    LocalTime startTime = parseTime(timeStr);
                    LocalTime endTime = startTime.plusHours(1);

                    if (!hasConflict(day, null, room.idRoom, professorId, startTime, endTime)) {
                        LocalDateTime startsAt = convertDayToDate(day, startTime);
                        LocalDateTime endsAt = convertDayToDate(day, endTime);

                        saveToAcademicEvent(scheduleId, courseId, professorId, day, startsAt, endsAt, room.idRoom);

                        AcademicEvent newEvent = new AcademicEvent();
                        newEvent.idCourse = courseId;
                        newEvent.idRoom = room.idRoom;
                        newEvent.idProfessor = professorId;
                        newEvent.day = day;
                        newEvent.startTime = startTime;
                        newEvent.endTime = endTime;
                        newEvent.typeEnum = "EXERCISE";
                        newEvent.scheduleId = scheduleId;
                        academicEvents.put(newEvent.idAcademicEvent, newEvent);
                        addedTerms++;
                        break;
                    }
                }
            }

            return "OK: Exercises scheduled (" + addedTerms + " terms, schedule_id: " + scheduleId + ")";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private LocalTime parseTime(String timeStr) throws IllegalArgumentException {
        try {
            return LocalTime.parse(timeStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid time format: " + timeStr +
                    ". Expected HH:mm:ss or HH:mm", e);
        }
    }

    public List<AcademicEvent> getEventsBySchedule(int scheduleIdFilter) throws SQLException {
        List<AcademicEvent> result = new ArrayList<>();
        String sql = "SELECT id, course_id, created_by_professor, type_enum, " +
                "starts_at, ends_at, is_online, room_id, notes, schedule_id " +
                "FROM academic_event WHERE schedule_id = ? ORDER BY starts_at";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scheduleIdFilter);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AcademicEvent event = new AcademicEvent();
                    event.idAcademicEvent = rs.getInt("id");
                    event.idCourse = rs.getInt("course_id");
                    event.idRoom = rs.getInt("room_id");
                    event.idProfessor = (int) rs.getLong("created_by_professor");
                    event.typeEnum = rs.getString("type_enum");
                    event.notes = rs.getString("notes");
                    event.isOnline = rs.getBoolean("is_online");
                    event.scheduleId = rs.getInt("schedule_id");

                    Timestamp startTs = rs.getTimestamp("starts_at");
                    Timestamp endTs = rs.getTimestamp("ends_at");

                    if (startTs != null) {
                        LocalDateTime startLdt = startTs.toLocalDateTime();
                        event.date = startLdt.toLocalDate();
                        event.startTime = startLdt.toLocalTime();
                        event.startsAt = startLdt;
                    }
                    if (endTs != null) {
                        LocalDateTime endLdt = endTs.toLocalDateTime();
                        event.endTime = endLdt.toLocalTime();
                        event.endsAt = endLdt;
                    }

                    result.add(event);
                }
            }
        }
        return result;
    }
}

class Course {
    public int idCourse;
    public String name;
    public int semester;
    public String code;
}

class Professor {
    public int idProfessor;
    public String fullName;
    public String email;
    public boolean isActive;
}

class Room {
    public int idRoom;
    public String code;
    public int capacity;
    public boolean isComputerLab;
    public boolean isActive;
}

class AcademicEvent {
    public int idAcademicEvent;
    public int idCourse;
    public int idRoom;
    public int idProfessor;
    public String day;
    public LocalTime startTime;
    public LocalTime endTime;
    public String typeEnum;
    public LocalDate date;
    public boolean isOnline;
    public String notes;
    public boolean isPublished;
    public boolean lockedByAdmin;
    public int scheduleId;
    public LocalDateTime startsAt;
    public LocalDateTime endsAt;
}

class Holiday {
    public int idHoliday;
    public String name;
    public LocalDate date;
}

class CoursePriority {
    public Course course;
    public Professor primaryProfessor;
    public Professor secondaryProfessor;
    public double primaryFlexibility = 0.5;
    public double secondaryFlexibility = 0.5;
    public double priority = 0.5;
}

// Popravljene greske: day >> weekday