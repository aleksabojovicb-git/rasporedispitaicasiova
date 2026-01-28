import java.sql.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class EventValidationService {
    private Connection conn;
    private Map<Integer, Course> courses;
    private Map<Integer, Room> rooms;
    private Map<Integer, Professor> professors;
    private Map<Integer, AcademicEvent> academicEvents;
    private List<Holiday> holidays;
    private LocalDate winterStart;
    private LocalDate summerStart;

    public EventValidationService(Connection connection) {
        this.conn = connection;
        this.courses = new HashMap<>();
        this.rooms = new HashMap<>();
        this.professors = new HashMap<>();
        this.academicEvents = new HashMap<>();
        this.holidays = new ArrayList<>();
        loadDataFromDatabase();
    }

    //region DataLoading

    private void loadDataFromDatabase() {
        try {
            loadAcademicYear();
            loadCourses();
            loadRooms();
            loadProfessors();
            loadAcademicEvents();
            loadHolidays();
        } catch (SQLException e) {
            System.err.println("Error loading data: " + e.getMessage());
        }
    }

    private void loadAcademicYear() throws SQLException {
        String query = "SELECT winter_semester_start, summer_semester_start FROM academic_year WHERE is_active = TRUE LIMIT 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                java.sql.Date wStart = rs.getDate("winter_semester_start");
                java.sql.Date sStart = rs.getDate("summer_semester_start");
                if (wStart != null) this.winterStart = wStart.toLocalDate();
                if (sStart != null) this.summerStart = sStart.toLocalDate();
                System.out.println("Loaded academic year: Winter=" + this.winterStart + ", Summer=" + this.summerStart);
            } else {
                System.out.println("WARNING: No active academic year found. Colloquium dates cannot be calculated.");
            }
        }
    }

    private void loadCourses() throws SQLException {
        String query = "SELECT id, name, semester, code, " +
                "COALESCE(lectures_per_week, 2) as lectures_per_week, " +
                "COALESCE(exercises_per_week, 2) as exercises_per_week, " +
                "COALESCE(labs_per_week, 0) as labs_per_week, " +
                "COALESCE(is_online, FALSE) as is_online, " +
                "colloquium_1_week, colloquium_2_week " +
                "FROM course";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            int count = 0;
            while (rs.next()) {
                Course course = new Course();
                course.idCourse = rs.getInt("id");
                course.name = rs.getString("name");
                course.semester = rs.getInt("semester");
                course.code = rs.getString("code");
                course.lecturesPerWeek = rs.getInt("lectures_per_week");
                course.exercisesPerWeek = rs.getInt("exercises_per_week");
                course.labsPerWeek = rs.getInt("labs_per_week");
                course.isOnline = rs.getBoolean("is_online");
                
                // Load colloquium weeks
                int col1 = rs.getInt("colloquium_1_week");
                if (!rs.wasNull()) course.colloquium1Week = col1;
                
                int col2 = rs.getInt("colloquium_2_week");
                if (!rs.wasNull()) course.colloquium2Week = col2;

                courses.put(course.idCourse, course);
                count++;
            }
            System.out.println("Loaded courses: " + count + " (with P+V+L workload and Colloquium weeks)");
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
                "room_id, notes, is_published, locked_by_admin, schedule_id, day FROM academic_event";
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

    //endregion
    
    public static void main(String[] args) {
        Connection conn = null;
        try {
            // Load PostgreSQL driver (since you're using postgresql-42.7.8.jar)
            Class.forName("org.postgresql.Driver");

            // Connect to your database
            conn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/your_database_name",
                    "your_username",
                    "your_password");

            System.out.println("✓ Connected to database");

            // Create the service
            EventValidationService service = new EventValidationService(conn);

            System.out.println("\nStarting schedule generation...\n");

            // Generate the 6 schedules
            ScheduleResult result = service.generateSixSchedulesWithDifferentPriorities();

            // Print result
            System.out.println("FINAL RESULT: " + result.message);


        } catch (Exception e) {
            System.err.println("✗ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close connection
            if (conn != null) {
                try {
                    conn.close();
                    System.out.println("\n✓ Database connection closed");
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //region Helper Functions



    private void saveToAcademicEvent(int scheduleId, int courseId, int professorId, String day,
            LocalDateTime startsAt, LocalDateTime endsAt, int roomId, String typeEnum) throws SQLException {
        String insert = "INSERT INTO academic_event " +
                "(course_id, created_by_professor, type_enum, starts_at, ends_at, " +
                "room_id, notes, is_published, locked_by_admin, schedule_id, day) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
            pstmt.setInt(1, courseId);
            pstmt.setLong(2, professorId);
            pstmt.setString(3, typeEnum);
            pstmt.setTimestamp(4, Timestamp.valueOf(startsAt));
            pstmt.setTimestamp(5, Timestamp.valueOf(endsAt));
            pstmt.setInt(6, roomId);
            pstmt.setNull(7, java.sql.Types.VARCHAR);
            pstmt.setBoolean(8, true);
            pstmt.setBoolean(9, false);
            pstmt.setInt(10, scheduleId);
            pstmt.setString(11, day);
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
                    "starts_at, ends_at, room_id, is_published, locked_by_admin, day) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
                LocalDateTime startsAt = convertDayToDate(day, startTime);
                LocalDateTime endsAt = convertDayToDate(day, endTime);

                pstmt.setInt(1, courseId);
                pstmt.setLong(2, professorId);
                pstmt.setString(3, typeEnum);
                pstmt.setTimestamp(4, Timestamp.valueOf(startsAt));
                pstmt.setTimestamp(5, Timestamp.valueOf(endsAt));
                pstmt.setInt(6, roomId);
                pstmt.setBoolean(7, true);
                pstmt.setBoolean(8, false);
                pstmt.setString(9, day);
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
                    "starts_at, ends_at, room_id, is_published, locked_by_admin) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
                LocalDateTime startsAt = LocalDateTime.of(colloquiumDate, startTime);
                LocalDateTime endsAt = LocalDateTime.of(colloquiumDate, endTime);

                pstmt.setInt(1, courseId);
                pstmt.setLong(2, professorId);
                pstmt.setString(3, "COLLOQUIUM");
                pstmt.setTimestamp(4, Timestamp.valueOf(startsAt));
                pstmt.setTimestamp(5, Timestamp.valueOf(endsAt));
                pstmt.setInt(6, roomId);
                pstmt.setBoolean(7, true);
                pstmt.setBoolean(8, false);
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
                    "starts_at, ends_at, room_id, is_published, locked_by_admin) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
                LocalDateTime startsAt = LocalDateTime.of(examDate, startTime);
                LocalDateTime endsAt = LocalDateTime.of(examDate, endTime);

                pstmt.setInt(1, courseId);
                pstmt.setLong(2, professorId);
                pstmt.setString(3, examType);
                pstmt.setTimestamp(4, Timestamp.valueOf(startsAt));
                pstmt.setTimestamp(5, Timestamp.valueOf(endsAt));
                pstmt.setInt(6, roomId);
                pstmt.setBoolean(7, true);
                pstmt.setBoolean(8, false);
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

    //endregion
    

    //region Automatic Schedule Generation

    public String generateCompleteSchedule() {
        try {
            System.out.println("=== STARTING AUTOMATIC COMPLETE SCHEDULE GENERATION ===\n");
            System.out.println("Using workload-based scheduling (P+V+L hours)\n");

            int scheduleId = generateNewScheduleId();
            System.out.println("Generated schedule_id: " + scheduleId + "\n");

            Map<Integer, Double> professorFlexibility = analyzeProfessorFlexibility();
            List<CoursePriority> priorities = determinePriorities(professorFlexibility);

            System.out.println("--- COURSE PRIORITIES (with workload) ---");
            for (CoursePriority cp : priorities) {
                Course c = cp.course;
                System.out.printf("Course: %-25s | P=%d V=%d L=%d (total=%d hrs) | Priority: %.2f\n",
                        c.name, c.lecturesPerWeek, c.exercisesPerWeek, c.labsPerWeek,
                        c.getTotalHoursPerWeek(), cp.priority);
            }

            int successfulCourses = 0;
            int partialCourses = 0;
            int failedCourses = 0;

            StringBuilder details = new StringBuilder();
            details.append("\n\n=== GENERATION DETAILS ===\n");

            for (CoursePriority cp : priorities) {
                Course course = cp.course;
                details.append("\n--- ").append(course.name)
                        .append(" (ID: ").append(course.idCourse)
                        .append(") P=").append(course.lecturesPerWeek)
                        .append(" V=").append(course.exercisesPerWeek)
                        .append(" L=").append(course.labsPerWeek)
                        .append(" ---\n");

                String result = generateCourseSchedule(course.idCourse, scheduleId);
                details.append(" [SCHEDULE] ").append(result).append("\n");

                if (result.startsWith("OK")) {
                    successfulCourses++;
                } else if (result.startsWith("WARNING")) {
                    partialCourses++;
                } else {
                    failedCourses++;
                }
            }

            System.out.println(details.toString());

            String summary = String.format(
                    "\n=== SCHEDULE GENERATION COMPLETED ===\n" +
                            "SCHEDULE_ID: %d\n" +
                            "\nCOURSES:\n" +
                            " ✓ Successful: %d\n" +
                            " ⚠ Partial: %d\n" +
                            " ✗ Failed: %d\n" +
                            "\nTOTAL COURSES PROCESSED: %d\n" +
                            "================================================",
                    scheduleId, successfulCourses, partialCourses, failedCourses, priorities.size());

            System.out.println(summary);

            loadAcademicEvents();

            if (failedCourses > 0) {
                return "WARNING: Schedule partially generated (schedule_id: " + scheduleId +
                        "). Check details above.";
            }

            return "OK: Complete schedule successfully generated for all courses (schedule_id: " + scheduleId + ")!";
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
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

    /**
     * Generiše raspored za jedan predmet poštujući pravila:
     * - Ukupno P+V+L mora biti 4-6 sati
     * - 4 sata: preferira dijeljenje na 2 dana (P dan1, V+L dan2), ali može i u 1
     * bloku
     * - 5-6 sati: MORA podijeljeno na 2 dana (P dan1, V+L dan2)
     * - Maximum 4 sata u jednom bloku
     * - Predmet se pojavljuje max 2 puta sedmično
     */
    private String generateCourseSchedule(int courseId, int scheduleId) {
        try {
            Course course = courses.get(courseId);
            if (course == null)
                return "ERROR: Course does not exist";

            int totalHours = course.getTotalHoursPerWeek();
            if (totalHours < 4 || totalHours > 6) {
                return "WARNING: Course total hours should be 4-6, got " + totalHours + ". Skipping.";
            }

            // Pronađi profesora za predavanja
            int lectureProfessorId = findLectureProfessor(courseId);
            if (lectureProfessorId == 0) {
                return "ERROR: No professor assigned for lectures";
            }

            // Pronađi asistenta za vježbe/lab (ili koristi profesora)
            int exerciseProfessorId = findExerciseProfessor(courseId);
            if (exerciseProfessorId == 0) {
                exerciseProfessorId = lectureProfessorId;
            }

            // Pronađi dostupne dane za profesora
            List<String> availableDays = getPreferredDays(lectureProfessorId);
            if (availableDays.size() < 2) {
                // Dodaj defaultne dane ako profesor nema dovoljno preferenci
                List<String> defaultDays = Arrays.asList("ponedeljak", "utorak", "srijeda", "cetvrtak", "petak");
                for (String d : defaultDays) {
                    if (!availableDays.contains(d))
                        availableDays.add(d);
                }
            }

            // Pronađi odgovarajuće sale
            List<Room> lectureRooms = getSuitableRooms(false, 30);
            List<Room> labRooms = getSuitableRooms(true, 20);

            if (lectureRooms.isEmpty()) {
                return "ERROR: No suitable rooms for lectures (need capacity >= 30)";
            }

            // Ako ima lab sate ali nema kompjuterskih sala, koristi obične
            if (course.labsPerWeek > 0 && labRooms.isEmpty()) {
                labRooms = lectureRooms;
            }

            int addedTerms = 0;

            // Preferira dijeljenje na 2 dana (uvijek za 5-6 sati, preferirano za 4)
            addedTerms = scheduleAsTwoDays(scheduleId, course,
                    lectureProfessorId, exerciseProfessorId,
                    availableDays, lectureRooms, labRooms);

            // Ako nije uspjelo i ima tačno 4 sata, pokušaj kao jedan blok
            if (addedTerms == 0 && totalHours == 4) {
                addedTerms = scheduleAsOneBlock(scheduleId, course, lectureProfessorId,
                        availableDays, lectureRooms);
            }

            if (addedTerms > 0) {
                return "OK: Course '" + course.name + "' scheduled (" + totalHours +
                        " hours in " + (addedTerms == 1 ? "1 block" : "2 days") + ")";
            }

            return "ERROR: Could not find suitable time slots for " + course.name;

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }


    
    /**
     * Raspoređuje predmet na 2 dana: Predavanja dan1, Vježbe+Lab dan2
     */
    private int scheduleAsTwoDays(int scheduleId, Course course,
            int lectureProfId, int exerciseProfId,
            List<String> days, List<Room> lectureRooms, List<Room> labRooms) throws SQLException {

        String lectureDay = null;
        String exerciseDay = null;
        Room lectureRoom = null;
        Room exerciseRoom = null;
        LocalTime lectureStart = null;
        LocalTime exerciseStart = null;

        // Dan 1: Pronađi slobodan blok za predavanja
        for (String day : days) {
            for (Room room : lectureRooms) {
                for (int hour = 8; hour <= 17 - course.lecturesPerWeek; hour++) {
                    LocalTime start = LocalTime.of(hour, 0);
                    LocalTime end = start.plusHours(course.lecturesPerWeek);

                    if (!hasConflictForBlock(day, room.idRoom, lectureProfId, start, end)) {
                        lectureDay = day;
                        lectureRoom = room;
                        lectureStart = start;
                        break;
                    }
                }
                if (lectureDay != null)
                    break;
            }
            if (lectureDay != null)
                break;
        }

        if (lectureDay == null)
            return 0;

        // Dan 2: Pronađi slobodan blok za Vježbe + Lab (mora biti drugi dan)
        int exerciseHours = course.exercisesPerWeek + course.labsPerWeek;
        List<Room> exerciseRoomList = course.labsPerWeek > 0 ? labRooms : lectureRooms;

        for (String day : days) {
            if (day.equals(lectureDay))
                continue; // Mora biti drugi dan

            for (Room room : exerciseRoomList) {
                for (int hour = 8; hour <= 17 - exerciseHours; hour++) {
                    LocalTime start = LocalTime.of(hour, 0);
                    LocalTime end = start.plusHours(exerciseHours);

                    if (!hasConflictForBlock(day, room.idRoom, exerciseProfId, start, end)) {
                        exerciseDay = day;
                        exerciseRoom = room;
                        exerciseStart = start;
                        break;
                    }
                }
                if (exerciseDay != null)
                    break;
            }
            if (exerciseDay != null)
                break;
        }

        if (exerciseDay == null)
            return 0;

        // Sačuvaj termine za predavanja (svaki sat posebno)
        LocalTime currentTime = lectureStart;
        for (int i = 0; i < course.lecturesPerWeek; i++) {
            LocalDateTime startsAt = convertDayToDate(lectureDay, currentTime);
            LocalDateTime endsAt = startsAt.plusHours(1);
            saveToAcademicEvent(scheduleId, course.idCourse, lectureProfId,
                    lectureDay, startsAt, endsAt, lectureRoom.idRoom, "LECTURE");
            addEventToCache(course.idCourse, lectureRoom.idRoom, lectureProfId,
                    lectureDay, currentTime, currentTime.plusHours(1), "LECTURE", scheduleId);
            currentTime = currentTime.plusHours(1);
        }

        // Sačuvaj termine za vježbe
        currentTime = exerciseStart;
        for (int i = 0; i < course.exercisesPerWeek; i++) {
            LocalDateTime startsAt = convertDayToDate(exerciseDay, currentTime);
            LocalDateTime endsAt = startsAt.plusHours(1);
            saveToAcademicEvent(scheduleId, course.idCourse, exerciseProfId,
                    exerciseDay, startsAt, endsAt, exerciseRoom.idRoom, "EXERCISE");
            addEventToCache(course.idCourse, exerciseRoom.idRoom, exerciseProfId,
                    exerciseDay, currentTime, currentTime.plusHours(1), "EXERCISE", scheduleId);
            currentTime = currentTime.plusHours(1);
        }

        // Sačuvaj termine za lab
        for (int i = 0; i < course.labsPerWeek; i++) {
            LocalDateTime startsAt = convertDayToDate(exerciseDay, currentTime);
            LocalDateTime endsAt = startsAt.plusHours(1);
            saveToAcademicEvent(scheduleId, course.idCourse, exerciseProfId,
                    exerciseDay, startsAt, endsAt, exerciseRoom.idRoom, "LAB");
            addEventToCache(course.idCourse, exerciseRoom.idRoom, exerciseProfId,
                    exerciseDay, currentTime, currentTime.plusHours(1), "LAB", scheduleId);
            currentTime = currentTime.plusHours(1);
        }

        System.out.println("  -> Scheduled: " + course.name + " - " +
                course.lecturesPerWeek + "P on " + lectureDay + " @ " + lectureStart +
                ", " + (course.exercisesPerWeek + course.labsPerWeek) + "(V+L) on " +
                exerciseDay + " @ " + exerciseStart);

        return 2; // Uspješno na 2 dana
    }

    /**
     * Raspoređuje predmet kao jedan blok (samo za 4 sata ako 2-dnevni raspored nije
     * uspio)
     */
    private int scheduleAsOneBlock(int scheduleId, Course course, int professorId,
            List<String> days, List<Room> rooms) throws SQLException {

        int totalHours = course.getTotalHoursPerWeek();

        for (String day : days) {
            for (Room room : rooms) {
                for (int hour = 8; hour <= 17 - totalHours; hour++) {
                    LocalTime startTime = LocalTime.of(hour, 0);
                    LocalTime endTime = startTime.plusHours(totalHours);

                    if (!hasConflictForBlock(day, room.idRoom, professorId, startTime, endTime)) {
                        // Kreiraj termine za svaki sat pojedinačno
                        LocalTime currentTime = startTime;

                        // Predavanja
                        for (int i = 0; i < course.lecturesPerWeek; i++) {
                            LocalDateTime startsAt = convertDayToDate(day, currentTime);
                            LocalDateTime endsAt = startsAt.plusHours(1);
                            saveToAcademicEvent(scheduleId, course.idCourse, professorId,
                                    day, startsAt, endsAt, room.idRoom, "LECTURE");
                            addEventToCache(course.idCourse, room.idRoom, professorId,
                                    day, currentTime, currentTime.plusHours(1), "LECTURE", scheduleId);
                            currentTime = currentTime.plusHours(1);
                        }

                        // Vježbe
                        for (int i = 0; i < course.exercisesPerWeek; i++) {
                            LocalDateTime startsAt = convertDayToDate(day, currentTime);
                            LocalDateTime endsAt = startsAt.plusHours(1);
                            saveToAcademicEvent(scheduleId, course.idCourse, professorId,
                                    day, startsAt, endsAt, room.idRoom, "EXERCISE");
                            addEventToCache(course.idCourse, room.idRoom, professorId,
                                    day, currentTime, currentTime.plusHours(1), "EXERCISE", scheduleId);
                            currentTime = currentTime.plusHours(1);
                        }

                        // Lab
                        for (int i = 0; i < course.labsPerWeek; i++) {
                            LocalDateTime startsAt = convertDayToDate(day, currentTime);
                            LocalDateTime endsAt = startsAt.plusHours(1);
                            saveToAcademicEvent(scheduleId, course.idCourse, professorId,
                                    day, startsAt, endsAt, room.idRoom, "LAB");
                            addEventToCache(course.idCourse, room.idRoom, professorId,
                                    day, currentTime, currentTime.plusHours(1), "LAB", scheduleId);
                            currentTime = currentTime.plusHours(1);
                        }

                        System.out.println("  -> Scheduled: " + course.name + " - " +
                                totalHours + " hours in 1 block on " + day + " @ " + startTime);
                        return 1; // Uspješno kao 1 blok
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Provjerava konflikt za cijeli blok termina (od start do end)
     */
    private boolean hasConflictForBlock(String day, int roomId, int professorId,
            LocalTime startTime, LocalTime endTime) {
        // Provjeri svaki sat u bloku
        LocalTime current = startTime;
        while (current.isBefore(endTime)) {
            if (hasConflict(day, null, roomId, professorId, current, current.plusHours(1))) {
                return true;
            }
            current = current.plusHours(1);
        }
        return false;
    }

    /**
     * Helper za dodavanje eventa u memorijski keš
     */
    private void addEventToCache(int courseId, int roomId, int profId,
            String day, LocalTime start, LocalTime end, String type, int scheduleId) {
        AcademicEvent newEvent = new AcademicEvent();
        newEvent.idCourse = courseId;
        newEvent.idRoom = roomId;
        newEvent.idProfessor = profId;
        newEvent.day = day;
        newEvent.startTime = start;
        newEvent.endTime = end;
        newEvent.typeEnum = type;
        newEvent.scheduleId = scheduleId;
        newEvent.date = convertDayToDate(day, start).toLocalDate();
        academicEvents.put(newEvent.hashCode(), newEvent);
    }

    /**
     * Vraća listu preferiranih dana za profesora
     */
    private List<String> getPreferredDays(int professorId) {
        List<String> days = new ArrayList<>();
        try {
            String query = "SELECT DISTINCT weekday FROM professor_availability WHERE professor_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, professorId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String weekday = rs.getString("weekday");
                        if (weekday != null)
                            days.add(weekday);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting preferred days: " + e.getMessage());
        }
        return days;
    }

    /**
     * Vraća listu odgovarajućih sala
     */
    private List<Room> getSuitableRooms(boolean needsComputerLab, int minCapacity) {
        List<Room> result = new ArrayList<>();
        for (Room room : rooms.values()) {
            if (room.isActive && room.capacity >= minCapacity) {
                if (!needsComputerLab || room.isComputerLab) {
                    result.add(room);
                }
            }
        }
        return result;
    }

    /**
     * Pronalazi profesora za predavanja
     */
    private int findLectureProfessor(int courseId) throws SQLException {
        String query = "SELECT professor_id FROM course_professor WHERE course_id = ? AND is_assistant = false";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("professor_id");
            }
        }
        return 0;
    }

    /**
     * Pronalazi asistenta za vježbe
     */
    private int findExerciseProfessor(int courseId) throws SQLException {
        String query = "SELECT professor_id FROM course_professor WHERE course_id = ? AND is_assistant = true";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt("professor_id");
            }
        }
        return 0;
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

    /**
     * Checks if a course is online (reads is_online boolean from course table)
     */
    private boolean isOnlineClass(Course course) {
        if (course == null)
            return false;
        return course.isOnline;
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

                        saveToAcademicEvent(scheduleId, courseId, professorId, day, startsAt, endsAt, room.idRoom,
                                "LECTURE");

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

                        saveToAcademicEvent(scheduleId, courseId, professorId, day, startsAt, endsAt, room.idRoom,
                                "EXERCISE");

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
                "starts_at, ends_at, room_id, notes, schedule_id " +
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

    /**
     * Generates 6 different weekly schedules with different year/semester
     * priorities
     * 
     * Priority Order for each schedule:
     * Schedule 1: Year 1 → Year 2 → Year 3
     * Schedule 2: Year 1 → Year 3 → Year 2
     * Schedule 3: Year 2 → Year 1 → Year 3
     * Schedule 4: Year 2 → Year 3 → Year 1
     * Schedule 5: Year 3 → Year 1 → Year 2
     * Schedule 6: Year 3 → Year 2 → Year 1
     * 
     * Within each schedule:
     * 1. Priority year LAB classes (lab classes have labs_per_week > 0)
     * 2. Other years LAB classes
     * 3. Priority year NON-LAB classes (labs_per_week == 0, not online)
     * 4. Other years NON-LAB classes
     * 5. Priority year ONLINE classes (course names: "SIZIS" or "IS")
     * 6. Other years ONLINE classes
     * 
     * Each schedule is independent - conflicts checked only within same schedule
     */

    //region Exams

    public ScheduleResult generateCompleteExamAndColloquiumScheduleForAllSemesters(int academicYear) {
        ScheduleResult finalResult = new ScheduleResult();
        
        try {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("GENERATING COMPLETE EXAM AND COLLOQUIUM SCHEDULE");
            System.out.println("Academic Year: " + academicYear);
            System.out.println("=".repeat(70) + "\n");
            
            List<Integer> semestersToProcess = new ArrayList<>();
            Map<Integer, Integer> semesterCourseCount = new HashMap<>();
            
            // Determine which semesters have courses
            for (Course course : courses.values()) {
                if (!semestersToProcess.contains(course.semester)) {
                    semestersToProcess.add(course.semester);
                }
                semesterCourseCount.put(course.semester, 
                    semesterCourseCount.getOrDefault(course.semester, 0) + 1);
            }
            
            Collections.sort(semestersToProcess);
            
            if (semestersToProcess.isEmpty()) {
                finalResult.success = false;
                finalResult.message = "ERROR: No courses found in any semester";
                return finalResult;
            }
            
            System.out.println("Found " + semestersToProcess.size() + " semesters with courses:\n");
            for (Integer sem : semestersToProcess) {
                System.out.println("  • Semester " + sem + ": " + semesterCourseCount.get(sem) + " courses");
            }
            System.out.println("\n" + "=".repeat(70) + "\n");
            
            // Results tracking for each semester
            Map<Integer, ScheduleResult> semesterResults = new LinkedHashMap<>();
            int totalSuccessfulExams = 0;
            int totalFailedCourses = 0;
            List<FailedCourse> allFailedCourses = new ArrayList<>();
            int totalSavedToDatabase = 0;
            int totalSaveErrors = 0;
            
            // Process each semester
            for (Integer semester : semestersToProcess) {
                System.out.println("\n" + "-".repeat(70));
                System.out.println("PROCESSING SEMESTER " + semester);
                System.out.println("-".repeat(70) + "\n");
                
                ScheduleResult semesterResult = generateExamsAndColloquiums(academicYear, semester);
                semesterResults.put(semester, semesterResult);
                
                totalSuccessfulExams += semesterResult.successfulCourses;
                totalFailedCourses += semesterResult.failedCourses;
                
                if (semesterResult.failedCoursesList != null) {
                    allFailedCourses.addAll(semesterResult.failedCoursesList);
                }
                
                System.out.println("\n✓ Semester " + semester + " completed");
                System.out.println("  - Schedule ID: " + semesterResult.scheduleId);
                System.out.println("  - Successful: " + semesterResult.successfulCourses);
                System.out.println("  - Failed: " + semesterResult.failedCourses);
                
                // AUTOMATICALLY SAVE TO DATABASE
                System.out.println("\n" + "-".repeat(70));
                System.out.println("SAVING SEMESTER " + semester + " TO DATABASE");
                System.out.println("-".repeat(70));
                
                try {
                    int savedCount = saveExamAndColloquiumScheduleToDatabase(semesterResult);
                    totalSavedToDatabase += savedCount;
                    System.out.println("✓ Saved " + savedCount + " events for semester " + semester);
                } catch (Exception e) {
                    totalSaveErrors++;
                    System.err.println("✗ Error saving semester " + semester + " to database: " + e.getMessage());
                }
            }
            
            // Generate final summary
            System.out.println("\n\n" + "=".repeat(70));
            System.out.println("FINAL SUMMARY - ALL SEMESTERS");
            System.out.println("=".repeat(70) + "\n");
            
            StringBuilder summary = new StringBuilder();
            summary.append("Academic Year: ").append(academicYear).append("\n");
            summary.append("Total Semesters Processed: ").append(semestersToProcess.size()).append("\n\n");
            
            summary.append("SCHEDULE STRUCTURE:\n");
            summary.append("  • Colloquium 1 (Koloq 1): Week 7-8\n");
            summary.append("  • Colloquium 2 (Koloq 2): Week 13-14\n");
            summary.append("  • Retake Colloquiums (Popravni): Week 15 (before exams)\n");
            summary.append("  • Regular Exams (Ispiti): Week 16-18\n");
            summary.append("  • Exam Weeks (Extra): Week 19-20 (additional exam period)\n");
            summary.append("  • Retake Exams (Popravni ispiti): Week 21-22 (final retake period)\n\n");
            
            summary.append("DETAILED RESULTS BY SEMESTER:\n");
            summary.append("-".repeat(70)).append("\n");
            
            for (Integer semester : semestersToProcess) {
                ScheduleResult sr = semesterResults.get(semester);
                summary.append(String.format("Semester %2d | Schedule ID: %5d | Generated: %3d | Failed: %3d\n",
                        semester, sr.scheduleId, sr.successfulCourses, sr.failedCourses));
            }
            
            summary.append("-".repeat(70)).append("\n\n");
            summary.append("OVERALL STATISTICS:\n");
            summary.append("  GENERATION:\n");
            summary.append("    • Total Exams/Colloquiums Generated: ").append(totalSuccessfulExams).append("\n");
            summary.append("    • Total Failed Courses: ").append(totalFailedCourses).append("\n");
            summary.append("    • Generation Success Rate: ").append(
                totalFailedCourses == 0 ? "100%" : 
                String.format("%.1f%%", (totalSuccessfulExams * 100.0 / 
                    (totalSuccessfulExams + totalFailedCourses)))).append("\n");
            
            summary.append("\n  DATABASE SAVE:\n");
            summary.append("    • Total Saved to Database: ").append(totalSavedToDatabase).append("\n");
            summary.append("    • Save Errors: ").append(totalSaveErrors).append("\n");
            
            if (!allFailedCourses.isEmpty()) {
                summary.append("\nFAILED COURSES:\n");
                summary.append("-".repeat(70)).append("\n");
                
                Map<Integer, List<FailedCourse>> failedBySemester = new TreeMap<>();
                for (FailedCourse fc : allFailedCourses) {
                    failedBySemester.computeIfAbsent(fc.semester, k -> new ArrayList<>()).add(fc);
                }
                
                for (Integer semester : failedBySemester.keySet()) {
                    summary.append("\nSemester ").append(semester).append(":\n");
                    for (FailedCourse fc : failedBySemester.get(semester)) {
                        summary.append(String.format("  • %s (ID: %d) - %s\n", 
                            fc.courseName, fc.courseId, fc.reason));
                    }
                }
            }
            
            summary.append("\n").append("=".repeat(70));
            
            System.out.println(summary.toString());
            
            // Set final result
            finalResult.success = (totalFailedCourses == 0 && totalSaveErrors == 0);
            finalResult.successfulCourses = totalSuccessfulExams;
            finalResult.failedCourses = totalFailedCourses;
            finalResult.failedCoursesList = allFailedCourses;
            finalResult.message = finalResult.success ? 
                "OK: Complete exam and colloquium schedule generated and saved for all semesters" :
                "WARNING: " + totalFailedCourses + " generation failures and " + totalSaveErrors + 
                " save errors. Check details above.";
            
            System.out.println("\n✓ SCHEDULE GENERATION AND SAVE COMPLETE!\n");
            
            return finalResult;
            
        } catch (Exception e) {
            e.printStackTrace();
            finalResult.success = false;
            finalResult.message = "ERROR: " + e.getMessage();
            return finalResult;
        }
    }

    public ScheduleResult generateExamsAndColloquiums(int academicYear, int semester) {
        ScheduleResult result = new ScheduleResult();
        try {
            System.out.println("=== GENERATING EXAMS AND COLLOQUIUMS SCHEDULE ===\n");
            System.out.println("Academic Year: " + academicYear + " | Semester: " + semester + "\n");
            
            int scheduleId = generateNewScheduleId();
            List<FailedCourse> failedList = new ArrayList<>();
            
            // Filter courses by semester
            List<Course> semesterCourses = new ArrayList<>();
            for (Course course : courses.values()) {
                if (course.semester == semester) {
                    semesterCourses.add(course);
                }
            }
            
            if (semesterCourses.isEmpty()) {
                result.success = false;
                result.message = "ERROR: No courses found for semester " + semester;
                return result;
            }
            
            System.out.println("Found " + semesterCourses.size() + " courses for semester " + semester + "\n");
            
            // Determine schedule weeks
            List<LocalDate> colloqium1Weeks = getWeekDates(academicYear, semester, 4, 6);
            List<LocalDate> colloqium2Weeks = getWeekDates(academicYear, semester, 7, 9);
            List<LocalDate> retakeColloqWeeks = getWeekDates(academicYear, semester, 10, 11);
            List<LocalDate> examWeeks = getWeekDates(academicYear, semester, 12, 15);
            List<LocalDate> retakeExamWeeks = getWeekDates(academicYear, semester, 16, 17);
            
            System.out.println("Schedule Timeline:");
            System.out.println("  • Colloquium 1: Week 4-6");
            System.out.println("  • Colloquium 2: Week 7-9");
            System.out.println("  • Retake Colloquiums: Week 10-11");
            System.out.println("  • Regular Exams: Week 12-15");
            System.out.println("  • Retake Exams: Week 16-17\n");
            System.out.println("=".repeat(50) + "\n");
            
            int colloquiumCount = 0;
            int examCount = 0;
            
            // PHASE 1: Schedule Colloquiums 1
            System.out.println("PHASE 1: Scheduling Colloquium 1...\n");
            for (Course course : semesterCourses) {
                int professorId = findLectureProfessor(course.idCourse);
                if (professorId == 0) continue;
                
                String result_colloq = scheduleColloquium(scheduleId, course, professorId, colloqium1Weeks, 1, failedList);
                if (result_colloq.equals("OK")) {
                    colloquiumCount++;
                }
            }
            System.out.println("✓ Colloquium 1 scheduled: " + colloquiumCount + " courses\n");
            
            // PHASE 2: Schedule Colloquiums 2
            System.out.println("PHASE 2: Scheduling Colloquium 2...\n");
            int colloq2Count = 0;
            for (Course course : semesterCourses) {
                int professorId = findLectureProfessor(course.idCourse);
                if (professorId == 0) continue;
                
                String result_colloq = scheduleColloquium(scheduleId, course, professorId, colloqium2Weeks, 2, failedList);
                if (result_colloq.equals("OK")) {
                    colloq2Count++;
                }
            }
            System.out.println("✓ Colloquium 2 scheduled: " + colloq2Count + " courses\n");
            colloquiumCount += colloq2Count;
            
            // PHASE 3: Schedule Retake Colloquiums
            System.out.println("PHASE 3: Scheduling Retake Colloquiums (Popravni kolokvijumi)...\n");
            int retakeColloqCount = 0;
            for (Course course : semesterCourses) {
                int professorId = findLectureProfessor(course.idCourse);
                int supervisorId = findExerciseProfessor(course.idCourse);
                if (supervisorId == 0) supervisorId = professorId;
                if (professorId == 0) continue;
                
                String result_retake = scheduleRetakeColloquium(scheduleId, course, professorId, 
                        supervisorId, retakeColloqWeeks.get(0), failedList);
                if (result_retake.equals("OK")) {
                    retakeColloqCount++;
                }
            }
            System.out.println("✓ Retake Colloquiums scheduled: " + retakeColloqCount + " courses\n");
            
            // PHASE 4: Schedule Main Exams
            System.out.println("PHASE 4: Scheduling Regular Exams (Ispiti)...\n");
            int mainExamCount = 0;
            for (Course course : semesterCourses) {
                int professorId = findLectureProfessor(course.idCourse);
                if (professorId == 0) continue;
                
                String result_exam = scheduleExamInPeriod(scheduleId, course, professorId, 
                        examWeeks, "EXAM", failedList);
                if (result_exam.equals("OK")) {
                    mainExamCount++;
                }
            }
            System.out.println("✓ Regular Exams scheduled: " + mainExamCount + " courses\n");
            examCount += mainExamCount;
            
            
            // PHASE 5: Schedule Retake Exams
            System.out.println("PHASE 5: Scheduling Retake Exams (Popravni ispiti - Week 16-17)...\n");
            int retakeExamCount = 0;
            for (Course course : semesterCourses) {
                int professorId = findLectureProfessor(course.idCourse);
                if (professorId == 0) continue;
                
                String result_retake = scheduleExamInPeriod(scheduleId, course, professorId, 
                        retakeExamWeeks, "RETAKE_EXAM", failedList);
                if (result_retake.equals("OK")) {
                    retakeExamCount++;
                }
            }
            System.out.println("✓ Retake Exams scheduled: " + retakeExamCount + " courses\n");
            
            // Generate summary
            StringBuilder summary = new StringBuilder();
            summary.append("\n").append("=".repeat(50)).append("\n");
            summary.append("EXAM SCHEDULE GENERATION COMPLETED\n");
            summary.append("=".repeat(50)).append("\n");
            summary.append("Schedule ID: ").append(scheduleId).append("\n");
            summary.append("Academic Year: ").append(academicYear).append("\n");
            summary.append("Semester: ").append(semester).append("\n\n");
            summary.append("STATISTICS:\n");
            summary.append(" • Colloquiums scheduled: ").append(colloquiumCount).append("\n");
            summary.append(" • Retake Colloquiums: ").append(retakeColloqCount).append("\n");
            summary.append(" • Regular Exams: ").append(examCount).append("\n");
            summary.append(" • Retake Exams: ").append(retakeExamCount).append("\n");
            summary.append(" • Total events: ").append(colloquiumCount + retakeColloqCount + examCount +  retakeExamCount).append("\n");
            summary.append(" • Failed courses: ").append(failedList.size()).append("\n");
            summary.append("=".repeat(50));
            
            System.out.println(summary.toString());
            
            loadAcademicEvents();
            
            result.scheduleId = scheduleId;
            result.successfulCourses = colloquiumCount + retakeColloqCount + examCount + retakeExamCount;
            result.failedCourses = failedList.size();
            result.failedCoursesList = failedList;
            result.success = (failedList.size() == 0);
            result.message = result.success ? 
                "OK: All exams and colloquiums scheduled successfully" :
                "WARNING: Some courses could not be scheduled";
            
            return result;
            
        } catch (Exception e) {
            e.printStackTrace();
            result.success = false;
            result.message = "ERROR: " + e.getMessage();
            return result;
        }
    }

    private String scheduleColloquium(int scheduleId, Course course, int professorId, 
            List<LocalDate> availableWeekDates, int colloquiumNumber, List<FailedCourse> failedList) {
        try {
            // Find a suitable day within the available weeks (Monday-Friday, not Sunday)
            LocalDate colloquiumDate = null;
            for (LocalDate date : availableWeekDates) {
                DayOfWeek day = date.getDayOfWeek();
                // Skip Sunday, prefer weekdays
                if (day != DayOfWeek.SUNDAY && day != DayOfWeek.SATURDAY) {
                    // Check if there are max 2 tests in this weekend
                    int weekendTestCount = countTestsInWeekend(scheduleId, date);
                    if (weekendTestCount < 2) {
                        colloquiumDate = date;
                        break;
                    }
                } else if (day == DayOfWeek.SATURDAY) {
                    // Saturday can be used as backup if weekdays are full
                    int weekendTestCount = countTestsInWeekend(scheduleId, date);
                    if (weekendTestCount < 2 && colloquiumDate == null) {
                        colloquiumDate = date;
                    }
                }
            }
            
            if (colloquiumDate == null) {
                failedList.add(new FailedCourse(course.idCourse, course.name, 
                    "Could not find available colloquium date", course.semester));
                return "FAILED";
            }
            
            // Check if it's a holiday
            for (Holiday holiday : holidays) {
                if (holiday.date.equals(colloquiumDate)) {
                    failedList.add(new FailedCourse(course.idCourse, course.name, 
                        "Selected date is a holiday: " + holiday.name, course.semester));
                    return "FAILED";
                }
            }
            
            // Find suitable room (vjezbe room with minimum capacity)
            List<Room> suitableRooms = getSuitableRooms(false, 20);
            if (suitableRooms.isEmpty()) {
                failedList.add(new FailedCourse(course.idCourse, course.name, 
                    "No suitable exercise rooms available", course.semester));
                return "FAILED";
            }
            
            // Find time slot (typically during exercise hours: 10:00-12:00, 13:00-15:00, etc.)
            LocalTime[] exerciseHours = {LocalTime.of(10, 0), LocalTime.of(12, 0), 
                                        LocalTime.of(14, 0), LocalTime.of(16, 0)};
            
            LocalTime colloquiumTime = null;
            Room selectedRoom = null;
            
            for (LocalTime time : exerciseHours) {
                for (Room room : suitableRooms) {
                    if (!hasConflict(null, colloquiumDate, room.idRoom, professorId, time, time.plusHours(1))) {
                        colloquiumTime = time;
                        selectedRoom = room;
                        break;
                    }
                }
                if (colloquiumTime != null) break;
            }
            
            if (colloquiumTime == null || selectedRoom == null) {
                failedList.add(new FailedCourse(course.idCourse, course.name, 
                    "Could not find available time/room for colloquium", course.semester));
                return "FAILED";
            }
            
            // Save the colloquium
            LocalDateTime startsAt = LocalDateTime.of(colloquiumDate, colloquiumTime);
            LocalDateTime endsAt = startsAt.plusHours(2); // Colloquiums typically 2 hours
            
            String type = "COLLOQUIUM_" + colloquiumNumber;
            saveToAcademicEvent(scheduleId, course.idCourse, professorId, 
                    colloquiumDate.getDayOfWeek().toString().toLowerCase(), startsAt, endsAt, 
                    selectedRoom.idRoom, type);
            
            System.out.println("  ✓ " + course.name + " (Colloq " + colloquiumNumber + ") scheduled for " + 
                    colloquiumDate + " at " + colloquiumTime);
            
            return "OK";
            
        } catch (Exception e) {
            failedList.add(new FailedCourse(course.idCourse, course.name, 
                "Exception: " + e.getMessage(), course.semester));
            return "ERROR";
        }
    }

    private String scheduleRetakeColloquium(int scheduleId, Course course, int professorId, 
            int supervisorId, LocalDate retakeWeek, List<FailedCourse> failedList) {
        try {
            // Retake colloquiums are all scheduled in one week
            // Find available slot
            LocalDate retakeDate = null;
            for (int i = 0; i < 5; i++) { // Try Monday through Friday
                LocalDate date = retakeWeek.plusDays(i);
                if (date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    retakeDate = date;
                    break;
                }
            }
            
            if (retakeDate == null) {
                failedList.add(new FailedCourse(course.idCourse, course.name, 
                    "Could not find available retake date", course.semester));
                return "FAILED";
            }
            
            List<Room> rooms = getSuitableRooms(false, 20);
            if (rooms.isEmpty()) {
                return "FAILED";
            }
            
            LocalTime[] times = {LocalTime.of(10, 0), LocalTime.of(12, 0), LocalTime.of(14, 0)};
            LocalTime selectedTime = null;
            Room selectedRoom = null;
            
            for (LocalTime time : times) {
                for (Room room : rooms) {
                    // Check supervisor not overloaded (duty tracking)
                    int supervisorCount = countSupervisorDuties(scheduleId, supervisorId);
                    if (supervisorCount < 5) {
                        if (!hasConflict(null, retakeDate, room.idRoom, professorId, time, time.plusHours(1))) {
                            selectedTime = time;
                            selectedRoom = room;
                            break;
                        }
                    }
                }
                if (selectedTime != null) break;
            }
            
            if (selectedTime == null || selectedRoom == null) {
                failedList.add(new FailedCourse(course.idCourse, course.name, 
                    "Could not find available time/room for retake", course.semester));
                return "FAILED";
            }
            
            LocalDateTime startsAt = LocalDateTime.of(retakeDate, selectedTime);
            LocalDateTime endsAt = startsAt.plusHours(2);
            
            saveToAcademicEvent(scheduleId, course.idCourse, professorId, 
                    retakeDate.getDayOfWeek().toString().toLowerCase(), startsAt, endsAt, 
                    selectedRoom.idRoom, "RETAKE_COLLOQUIUM");
            
            System.out.println("  ✓ " + course.name + " (Retake Colloq) scheduled for " + 
                    retakeDate + " at " + selectedTime);
            
            return "OK";
            
        } catch (Exception e) {
            failedList.add(new FailedCourse(course.idCourse, course.name, 
                "Exception: " + e.getMessage(), course.semester));
            return "ERROR";
        }
    }

    private String scheduleExamInPeriod(int scheduleId, Course course, int professorId, 
            List<LocalDate> examWeekDates, String examType, List<FailedCourse> failedList) {
        try {
            // Find suitable date - max 2 exams per weekend
            LocalDate examDate = null;
            for (LocalDate date : examWeekDates) {
                if (date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    int weekendExamCount = countTestsInWeekend(scheduleId, date);
                    if (weekendExamCount < 2) {
                        examDate = date;
                        break;
                    }
                }
            }
            
            if (examDate == null) {
                failedList.add(new FailedCourse(course.idCourse, course.name, 
                    "Could not find available exam date (weekend too crowded)", course.semester));
                return "FAILED";
            }
            
            // Check holiday
            for (Holiday holiday : holidays) {
                if (holiday.date.equals(examDate)) {
                    failedList.add(new FailedCourse(course.idCourse, course.name, 
                        "Selected date is a holiday", course.semester));
                    return "FAILED";
                }
            }
            
            // Find room and time
            List<Room> rooms = getSuitableRooms(false, 30);
            if (rooms.isEmpty()) {
                failedList.add(new FailedCourse(course.idCourse, course.name, 
                    "No suitable exam rooms", course.semester));
                return "FAILED";
            }
            
            LocalTime[] examTimes = {LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(12, 0), 
                                    LocalTime.of(14, 0), LocalTime.of(16, 0)};
            
            LocalTime selectedTime = null;
            Room selectedRoom = null;
            
            for (LocalTime time : examTimes) {
                for (Room room : rooms) {
                    if (!hasConflict(null, examDate, room.idRoom, professorId, time, time.plusHours(2))) {
                        selectedTime = time;
                        selectedRoom = room;
                        break;
                    }
                }
                if (selectedTime != null) break;
            }
            
            if (selectedTime == null || selectedRoom == null) {
                failedList.add(new FailedCourse(course.idCourse, course.name, 
                    "Could not find available exam slot", course.semester));
                return "FAILED";
            }
            
            LocalDateTime startsAt = LocalDateTime.of(examDate, selectedTime);
            LocalDateTime endsAt = startsAt.plusHours(2);
            
            saveToAcademicEvent(scheduleId, course.idCourse, professorId, 
                    examDate.getDayOfWeek().toString().toLowerCase(), startsAt, endsAt, 
                    selectedRoom.idRoom, examType);
            
            System.out.println("  ✓ " + course.name + " (" + examType + ") scheduled for " + 
                    examDate + " at " + selectedTime);
            
            return "OK";
            
        } catch (Exception e) {
            failedList.add(new FailedCourse(course.idCourse, course.name, 
                "Exception: " + e.getMessage(), course.semester));
            return "ERROR";
        }
    }

    private List<LocalDate> getWeekDates(int academicYear, int semester, int startWeek, int endWeek) {
        List<LocalDate> dates = new ArrayList<>();
        
        // Determine semester start date (typically mid-September for fall, mid-February for spring)
        LocalDate semesterStart;
        if (semester % 2 == 1) {
            // Odd semesters (1,3,5) start in September
            semesterStart = LocalDate.of(academicYear, 9, 15);
        } else {
            // Even semesters (2,4,6) start in February
            semesterStart = LocalDate.of(academicYear, 2, 15);
        }
        
        // Calculate week start date (Monday of the week)
        LocalDate weekStart = semesterStart.plusWeeks(startWeek - 1)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        
        // Generate all dates from start week to end week
        LocalDate currentDate = weekStart;
        LocalDate periodEnd = semesterStart.plusWeeks(endWeek);
        
        while (currentDate.isBefore(periodEnd)) {
            dates.add(currentDate);
            currentDate = currentDate.plusDays(1);
        }
        
        return dates;
    }

    private int countTestsInWeekend(int scheduleId, LocalDate date) {
        int count = 0;
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        
        for (AcademicEvent event : academicEvents.values()) {
            if (event.scheduleId == scheduleId && event.date != null &&
                !event.date.isBefore(weekStart) && !event.date.isAfter(weekEnd)) {
                if (event.typeEnum.contains("EXAM") || event.typeEnum.contains("COLLOQUIUM")) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countSupervisorDuties(int scheduleId, int supervisorId) {
        int count = 0;
        for (AcademicEvent event : academicEvents.values()) {
            if (event.scheduleId == scheduleId && event.idProfessor == supervisorId &&
                (event.typeEnum.contains("COLLOQUIUM") || event.typeEnum.contains("EXAM"))) {
                count++;
            }
        }
        return count;
    }

    public int saveExamAndColloquiumScheduleToDatabase(ScheduleResult scheduleResult) {
        int totalSaved = 0;
        
        try {
            System.out.println("\n  Saving exam and colloquium schedule to database...");
            System.out.println("  Schedule ID: " + scheduleResult.scheduleId);
            
            if (scheduleResult == null || !scheduleResult.success) {
                System.err.println("  ✗ Cannot save: Schedule generation was not successful");
                return 0;
            }
            
            // Fetch all events for this schedule
            List<AcademicEvent> eventsToSave = getEventsBySchedule(scheduleResult.scheduleId);
            
            if (eventsToSave.isEmpty()) {
                System.out.println("  ⚠ No events found for schedule ID: " + scheduleResult.scheduleId);
                return 0;
            }
            
            int totalFailed = 0;
            
            // Group events by type for tracking
            Map<String, Integer> eventsByType = new HashMap<>();
            
            for (AcademicEvent event : eventsToSave) {
                try {
                    // Verify all required fields are set
                    if (event.idCourse == 0 || event.idRoom == 0 || event.idProfessor == 0 || 
                        event.typeEnum == null || event.startsAt == null || event.endsAt == null) {
                        System.err.println("    ✗ Invalid event: missing required fields for course " + event.idCourse);
                        totalFailed++;
                        continue;
                    }
                    
                    // Get day of week name
                    String dayName = event.startsAt.getDayOfWeek().toString().toLowerCase();
                    dayName = translateDayToMontenegrin(dayName);
                    
                    // Save to database
                    saveExamEventToDatabase(scheduleResult.scheduleId, event.idCourse, event.idProfessor, 
                            dayName, event.startsAt, event.endsAt, event.idRoom, event.typeEnum);
                    
                    // Update cache
                    AcademicEvent savedEvent = new AcademicEvent();
                    savedEvent.idCourse = event.idCourse;
                    savedEvent.idRoom = event.idRoom;
                    savedEvent.idProfessor = event.idProfessor;
                    savedEvent.typeEnum = event.typeEnum;
                    savedEvent.day = dayName;
                    savedEvent.date = event.startsAt.toLocalDate();
                    savedEvent.startTime = event.startsAt.toLocalTime();
                    savedEvent.endTime = event.endsAt.toLocalTime();
                    savedEvent.startsAt = event.startsAt;
                    savedEvent.endsAt = event.endsAt;
                    savedEvent.scheduleId = scheduleResult.scheduleId;
                    savedEvent.isPublished = true;
                    
                    academicEvents.put(savedEvent.idAcademicEvent, savedEvent);
                    
                    // Track by event type
                    eventsByType.put(event.typeEnum, eventsByType.getOrDefault(event.typeEnum, 0) + 1);
                    totalSaved++;
                    
                } catch (Exception e) {
                    System.err.println("    ✗ Error saving event for course " + event.idCourse + 
                            ": " + e.getMessage());
                    totalFailed++;
                }
            }
            
            // Print summary by type
            System.out.println("\n  Events saved by type:");
            for (Map.Entry<String, Integer> entry : eventsByType.entrySet()) {
                System.out.println("    • " + entry.getKey() + ": " + entry.getValue());
            }
            
            System.out.println("\n  ✓ Successfully saved: " + totalSaved + " events");
            if (totalFailed > 0) {
                System.out.println("  ✗ Failed to save: " + totalFailed + " events");
            }
            
        } catch (Exception e) {
            System.err.println("  ✗ Error during save operation: " + e.getMessage());
            e.printStackTrace();
        }
        
        return totalSaved;
    }

    private void saveExamEventToDatabase(int scheduleId, int courseId, int professorId, String day,
            LocalDateTime startsAt, LocalDateTime endsAt, int roomId, String typeEnum) throws SQLException {
        String insert = "INSERT INTO academic_event " +
                "(course_id, created_by_professor, type_enum, starts_at, ends_at, " +
                "room_id, notes, is_published, locked_by_admin, schedule_id, day) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
            pstmt.setInt(1, courseId);
            pstmt.setLong(2, professorId);
            pstmt.setString(3, typeEnum);
            pstmt.setTimestamp(4, Timestamp.valueOf(startsAt));
            pstmt.setTimestamp(5, Timestamp.valueOf(endsAt));
            pstmt.setInt(6, roomId);
            
            // Add notes with exam type info
            String notes = "Auto-generated " + typeEnum.toLowerCase() + " event";
            pstmt.setString(7, notes);
            
            pstmt.setBoolean(8, true);  // is_published
            pstmt.setBoolean(9, false); // locked_by_admin
            pstmt.setInt(10, scheduleId);
            pstmt.setString(11, day);
            
            pstmt.executeUpdate();
        }
    }

    public void saveAllSemesterExamsToDatabase(int academicYear) {
        try {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("BATCH SAVING ALL SEMESTER EXAMS TO DATABASE");
            System.out.println("Academic Year: " + academicYear);
            System.out.println("=".repeat(70) + "\n");
            
            // Get all semesters with courses
            Set<Integer> semesters = new HashSet<>();
            for (Course course : courses.values()) {
                semesters.add(course.semester);
            }
            
            if (semesters.isEmpty()) {
                System.out.println("✗ No courses found for this academic year");
                return;
            }
            
            int totalSaved = 0;
            int totalFailed = 0;
            
            for (Integer semester : semesters) {
                try {
                    System.out.println("\nProcessing Semester " + semester + "...");
                    
                    // Generate schedule for this semester
                    ScheduleResult result = generateExamsAndColloquiums(academicYear, semester);
                    
                    if (result.success) {
                        // Save to database
                        saveExamAndColloquiumScheduleToDatabase(result);
                        totalSaved += result.successfulCourses;
                    } else {
                        System.err.println("  ⚠ Generation failed for semester " + semester);
                        totalFailed += result.failedCourses;
                    }
                    
                } catch (Exception e) {
                    System.err.println("✗ Error processing semester " + semester + ": " + e.getMessage());
                    totalFailed++;
                }
            }
            
            // Final batch summary
            System.out.println("\n" + "=".repeat(70));
            System.out.println("BATCH SAVE SUMMARY");
            System.out.println("=".repeat(70));
            System.out.println("Academic Year: " + academicYear);
            System.out.println("Semesters processed: " + semesters.size());
            System.out.println("✓ Total saved: " + totalSaved);
            System.out.println("✗ Total failed: " + totalFailed);
            System.out.println("=".repeat(70) + "\n");
            
        } catch (Exception e) {
            System.err.println("✗ Batch save error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void publishExamSchedule(int scheduleId) {
        try {
            System.out.println("\nPublishing exam schedule ID: " + scheduleId);
            
            String updateQuery = "UPDATE academic_event SET is_published = true " +
                    "WHERE schedule_id = ? AND (type_enum LIKE '%EXAM%' OR type_enum LIKE '%COLLOQUIUM%')";
            
            try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                pstmt.setInt(1, scheduleId);
                int updated = pstmt.executeUpdate();
                System.out.println("✓ Published " + updated + " exam/colloquium events");
            }
            
        } catch (SQLException e) {
            System.err.println("✗ Error publishing schedule: " + e.getMessage());
        }
    }

    public void lockExamSchedule(int scheduleId) {
        try {
            System.out.println("\nLocking exam schedule ID: " + scheduleId + " (professors cannot modify)");
            
            String updateQuery = "UPDATE academic_event SET locked_by_admin = true " +
                    "WHERE schedule_id = ? AND (type_enum LIKE '%EXAM%' OR type_enum LIKE '%COLLOQUIUM%')";
            
            try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                pstmt.setInt(1, scheduleId);
                int updated = pstmt.executeUpdate();
                System.out.println("✓ Locked " + updated + " exam/colloquium events");
            }
            
        } catch (SQLException e) {
            System.err.println("✗ Error locking schedule: " + e.getMessage());
        }
    }

    public void unlockExamSchedule(int scheduleId) {
        try {
            System.out.println("\nUnlocking exam schedule ID: " + scheduleId + " (professors can modify)");
            
            String updateQuery = "UPDATE academic_event SET locked_by_admin = false " +
                    "WHERE schedule_id = ? AND (type_enum LIKE '%EXAM%' OR type_enum LIKE '%COLLOQUIUM%')";
            
            try (PreparedStatement pstmt = conn.prepareStatement(updateQuery)) {
                pstmt.setInt(1, scheduleId);
                int updated = pstmt.executeUpdate();
                System.out.println("✓ Unlocked " + updated + " exam/colloquium events");
            }
            
        } catch (SQLException e) {
            System.err.println("✗ Error unlocking schedule: " + e.getMessage());
        }
    }

    private String translateDayToMontenegrin(String englishDay) {
        switch (englishDay.toLowerCase()) {
            case "monday": return "ponedeljak";
            case "tuesday": return "utorak";
            case "wednesday": return "srijeda";
            case "thursday": return "cetvrtak";
            case "friday": return "petak";
            case "saturday": return "subota";
            case "sunday": return "nedelja";
            default: return englishDay;
        }
    }


    //endregion



    //region SixSchedules

    public ScheduleResult generateSixSchedulesWithDifferentPriorities() {
        ScheduleResult result = new ScheduleResult();
        try {
            System.out.println("=== GENERATING 6 SCHEDULES WITH DIFFERENT YEAR PRIORITIES ===\n");

            // Define the 6 different priority orders
            int[][] priorityOrders = {
                    { 1, 2, 3 }, // Schedule 1
                    { 1, 3, 2 }, // Schedule 2
                    { 2, 1, 3 }, // Schedule 3
                    { 2, 3, 1 }, // Schedule 4
                    { 3, 1, 2 }, // Schedule 5
                    { 3, 2, 1 } // Schedule 6
            };

            List<Integer> generatedScheduleIds = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;
            List<FailedCourse> allFailedCourses = new ArrayList<>();


            // Generate each of the 6 schedules
            for (int i = 0; i < 6; i++) {
                int[] priorityOrder = priorityOrders[i];
                System.out.println("\n>>> SCHEDULE " + (i + 1) + " <<<");
                System.out.println("Priority Order: Year " + priorityOrder[0] + " → Year " +
                        priorityOrder[1] + " → Year " + priorityOrder[2]);
                System.out.println("─".repeat(50));

                int scheduleId = generateScheduleWithYearPriority(priorityOrder, allFailedCourses);
                if (scheduleId > 0) {
                    generatedScheduleIds.add(scheduleId);
                    successCount++;
                    System.out.println("✓ Schedule " + (i + 1) + " completed with ID: " + scheduleId);
                } else {
                    failCount++;
                    System.out.println("✗ Schedule " + (i + 1) + " failed to generate");
                }
            }

            // Print summary
            StringBuilder summary = new StringBuilder();
            summary.append("\n").append("=".repeat(50)).append("\n");
            summary.append("GENERATION COMPLETE\n");
            summary.append("=".repeat(50)).append("\n");
            summary.append("Successfully generated: ").append(successCount).append("/6 schedules\n\n");

            for (int i = 0; i < generatedScheduleIds.size(); i++) {
                summary.append("Schedule ").append(i + 1).append(": ID = ").append(generatedScheduleIds.get(i));
                int[] order = priorityOrders[i];
                summary.append(" | Priority: ").append(order[0]).append("→")
                        .append(order[1]).append("→").append(order[2]).append("\n");
            }
            summary.append("=".repeat(50));

            System.out.println(summary.toString());

            loadAcademicEvents(); // Reload to reflect all new events

            result.scheduleId = generatedScheduleIds.isEmpty() ? 0 : generatedScheduleIds.get(0);
            result.successfulCourses = successCount;
            result.failedCourses = failCount;
            result.failedCoursesList = allFailedCourses;  // This is the key
            result.success = (failCount == 0);
            
            if (failCount > 0) {
                result.message = "WARNING: Generated " + successCount + "/6 schedules. " + 
                            allFailedCourses.size() + " courses failed";
            } else {
                result.message = "OK: All 6 schedules generated successfully";
            }
            return result;
            
        } catch (Exception e) {
            result.success = false;
            result.message = "ERROR: " + e.getMessage();
            e.printStackTrace();
            return result;
    }
    }

    /**
     * Generates a single schedule with specified year priority order
     */
    private int generateScheduleWithYearPriority(int[] yearPriorityOrder, 
                                             List<FailedCourse> failedList) throws SQLException {
        int scheduleId = generateNewScheduleId();

        // Group courses by year/semester
        Map<Integer, List<Course>> coursesByYear = new HashMap<>();
        for (int year = 1; year <= 3; year++) {
            coursesByYear.put(year, new ArrayList<>());
        }

        // Load and categorize all courses (convert semester 1-6 to year 1-3)
        for (Course course : courses.values()) {
            // Semester 1,2 -> Year 1; Semester 3,4 -> Year 2; Semester 5,6 -> Year 3
            int year = (course.semester + 1) / 2;
            // Ensure year is within valid range 1-3
            if (year < 1)
                year = 1;
            if (year > 3)
                year = 3;
            coursesByYear.get(year).add(course);
        }

        int totalScheduled = 0;

        // ===== PHASE 1: LAB CLASSES =====
        System.out.println("Phase 1: Scheduling LAB classes...");

        // First: Priority year labs
        for (int priorityYear : yearPriorityOrder) {
            List<Course> yearCourses = coursesByYear.get(priorityYear);
            for (Course course : yearCourses) {
                if (course.labsPerWeek > 0) {
                    String result = scheduleCourseForSchedule(scheduleId, course, failedList);
                    if (result.equals("OK")) {
                        totalScheduled++;
                    }
                }
            }
        }

        // Then: Non-priority year labs (fill gaps)
        for (int year = 1; year <= 3; year++) {
            List<Course> yearCourses = coursesByYear.get(year);
            for (Course course : yearCourses) {
                if (course.labsPerWeek > 0 &&
                        !isAlreadyScheduledInSchedule(scheduleId, course.idCourse)) {
                    String result = scheduleCourseForSchedule(scheduleId, course, failedList);
                    if (result.equals("OK")) {
                        totalScheduled++;
                    }
                }
            }
        }

        // ===== PHASE 2: NON-LAB CLASSES =====
        System.out.println("Phase 2: Scheduling NON-LAB classes...");

        // First: Priority year non-labs
        for (int priorityYear : yearPriorityOrder) {
            List<Course> yearCourses = coursesByYear.get(priorityYear);
            for (Course course : yearCourses) {
                if (course.labsPerWeek == 0 && !isOnlineClass(course)) {
                    String result = scheduleCourseForSchedule(scheduleId, course, failedList);
                    if (result.equals("OK")) {
                        totalScheduled++;
                    }
                }
            }
        }

        // Then: Non-priority year non-labs (fill gaps)
        for (int year = 1; year <= 3; year++) {
            List<Course> yearCourses = coursesByYear.get(year);
            for (Course course : yearCourses) {
                if (course.labsPerWeek == 0 && !isOnlineClass(course) &&
                        !isAlreadyScheduledInSchedule(scheduleId, course.idCourse)) {
                    String result = scheduleCourseForSchedule(scheduleId, course, failedList);
                    if (result.equals("OK")) {
                        totalScheduled++;
                    }
                }
            }
        }

        // ===== PHASE 3: ONLINE CLASSES =====
        System.out.println("Phase 3: Scheduling ONLINE classes...");

        // First: Priority year online
        for (int priorityYear : yearPriorityOrder) {
            List<Course> yearCourses = coursesByYear.get(priorityYear);
            for (Course course : yearCourses) {
                if (isOnlineClass(course)) {
                    String result = scheduleCourseForSchedule(scheduleId, course, failedList);
                    if (result.equals("OK")) {
                        totalScheduled++;
                    }
                }
            }
        }

        // Then: Non-priority year online (fill gaps)
        for (int year = 1; year <= 3; year++) {
            List<Course> yearCourses = coursesByYear.get(year);
            for (Course course : yearCourses) {
                if (isOnlineClass(course) &&
                        !isAlreadyScheduledInSchedule(scheduleId, course.idCourse)) {
                    String result = scheduleCourseForSchedule(scheduleId, course, failedList);
                    if (result.equals("OK")) {
                        totalScheduled++;
                    }
                }
            }
        }

        // ===== PHASE 4: EXAMS/COLLOQUIUMS =====
        generateColloquiums(scheduleId, failedList);

        System.out.println("Total courses scheduled: " + totalScheduled);
        return scheduleId;
    }

    /**
     * Checks if a course is already scheduled in this schedule
     */
    private boolean isAlreadyScheduledInSchedule(int scheduleId, int courseId) {
        for (AcademicEvent event : academicEvents.values()) {
            if (event.scheduleId == scheduleId && event.idCourse == courseId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Schedules a single course for the given schedule
     * Respects existing time slot constraints within the same schedule
     * Returns "OK" on success, error message on failure
     */
    private String scheduleCourseForSchedule(int scheduleId, Course course,
                                            List<FailedCourse> failedList) {
        try {
            if (course == null) {
                failedList.add(new FailedCourse(0, "Unknown", "Course is null", 0));
                return "SKIP";
            }
            
            int totalHours = course.getTotalHoursPerWeek();
            if (totalHours < 4 || totalHours > 6) {
                failedList.add(new FailedCourse(course.idCourse, course.name,
                    "Invalid hours: " + totalHours + " (must be 4-6)", course.semester));
                return "SKIP";
            }
            
            int lectureProfId = findLectureProfessor(course.idCourse);
            if (lectureProfId == 0) {
                failedList.add(new FailedCourse(course.idCourse, course.name,
                    "No professor assigned", course.semester));
                return "SKIP";
            }

            // Find exercise professor
            int exerciseProfId = findExerciseProfessor(course.idCourse);
            if (exerciseProfId == 0) {
                exerciseProfId = lectureProfId;
            }

            // Get available days for lecture professor
            List<String> availableDays = getPreferredDays(lectureProfId);
            if (availableDays.isEmpty()) {
                availableDays.addAll(Arrays.asList("ponedeljak", "utorak", "srijeda", "cetvrtak", "petak"));
            }

            // Get suitable rooms
            List<Room> lectureRooms = getSuitableRooms(false, 30);
            List<Room> labRooms = getSuitableRooms(true, 20);

            if (lectureRooms.isEmpty()) {
                return "SKIP: No lecture rooms available";
            }

            if (course.labsPerWeek > 0 && labRooms.isEmpty()) {
                labRooms = lectureRooms;
            }

            // Try to schedule as two days (preferred for 5-6 hours, optimal for 4)
            int addedTerms = scheduleAsTwoDaysWithinSchedule(scheduleId, course,
                    lectureProfId, exerciseProfId,
                    availableDays, lectureRooms, labRooms);

            // If failed and total hours is 4, try as one block
            if (addedTerms == 0 && totalHours == 4) {
                addedTerms = scheduleAsOneBlockWithinSchedule(scheduleId, course, lectureProfId,
                        availableDays, lectureRooms);
            }

            if (addedTerms > 0) {
                return "OK";
            }
            
            failedList.add(new FailedCourse(course.idCourse, course.name,
                "Could not find suitable time slots", course.semester));
            return "FAILED";


        } catch (SQLException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Schedules course as two days (Lectures on day1, Exercises+Labs on day2)
     * Only checks conflicts WITHIN the same schedule
     */
    private int scheduleAsTwoDaysWithinSchedule(int scheduleId, Course course,
            int lectureProfId, int exerciseProfId,
            List<String> days, List<Room> lectureRooms,
            List<Room> labRooms) throws SQLException {
        String lectureDay = null;
        String exerciseDay = null;
        Room lectureRoom = null;
        Room exerciseRoom = null;
        LocalTime lectureStart = null;
        LocalTime exerciseStart = null;

        // Day 1: Find free slot for lectures
        for (String day : days) {
            for (Room room : lectureRooms) {
                for (int hour = 8; hour <= 17 - course.lecturesPerWeek; hour++) {
                    LocalTime start = LocalTime.of(hour, 0);
                    LocalTime end = start.plusHours(course.lecturesPerWeek);

                    if (!hasConflictInSchedule(scheduleId, day, room.idRoom, lectureProfId, start, end,
                            course.semester)) {
                        lectureDay = day;
                        lectureRoom = room;
                        lectureStart = start;
                        break;
                    }
                }
                if (lectureDay != null)
                    break;
            }
            if (lectureDay != null)
                break;
        }

        if (lectureDay == null) {
            return 0;
        }

        // Day 2: Find free slot for exercises + labs (must be different day)
        int exerciseHours = course.exercisesPerWeek + course.labsPerWeek;
        List<Room> exerciseRoomList = course.labsPerWeek > 0 ? labRooms : lectureRooms;

        for (String day : days) {
            if (day.equals(lectureDay))
                continue; // Must be different day

            for (Room room : exerciseRoomList) {
                for (int hour = 8; hour <= 17 - exerciseHours; hour++) {
                    LocalTime start = LocalTime.of(hour, 0);
                    LocalTime end = start.plusHours(exerciseHours);

                    if (!hasConflictInSchedule(scheduleId, day, room.idRoom, exerciseProfId, start, end,
                            course.semester)) {
                        exerciseDay = day;
                        exerciseRoom = room;
                        exerciseStart = start;
                        break;
                    }
                }
                if (exerciseDay != null)
                    break;
            }
            if (exerciseDay != null)
                break;
        }

        if (exerciseDay == null) {
            return 0;
        }

        // Save lectures (each hour separately)
        LocalTime currentTime = lectureStart;
        for (int i = 0; i < course.lecturesPerWeek; i++) {
            LocalDateTime startsAt = convertDayToDate(lectureDay, currentTime);
            LocalDateTime endsAt = startsAt.plusHours(1);
            saveToAcademicEvent(scheduleId, course.idCourse, lectureProfId,
                    lectureDay, startsAt, endsAt, lectureRoom.idRoom, "LECTURE");
            addEventToCache(course.idCourse, lectureRoom.idRoom, lectureProfId,
                    lectureDay, currentTime, currentTime.plusHours(1), "LECTURE", scheduleId);
            currentTime = currentTime.plusHours(1);
        }

        // Save exercises (each hour separately)
        currentTime = exerciseStart;
        for (int i = 0; i < course.exercisesPerWeek; i++) {
            LocalDateTime startsAt = convertDayToDate(exerciseDay, currentTime);
            LocalDateTime endsAt = startsAt.plusHours(1);
            saveToAcademicEvent(scheduleId, course.idCourse, exerciseProfId,
                    exerciseDay, startsAt, endsAt, exerciseRoom.idRoom, "EXERCISE");
            addEventToCache(course.idCourse, exerciseRoom.idRoom, exerciseProfId,
                    exerciseDay, currentTime, currentTime.plusHours(1), "EXERCISE", scheduleId);
            currentTime = currentTime.plusHours(1);
        }

        // Save labs (each hour separately)
        for (int i = 0; i < course.labsPerWeek; i++) {
            LocalDateTime startsAt = convertDayToDate(exerciseDay, currentTime);
            LocalDateTime endsAt = startsAt.plusHours(1);
            saveToAcademicEvent(scheduleId, course.idCourse, exerciseProfId,
                    exerciseDay, startsAt, endsAt, exerciseRoom.idRoom, "LAB");
            addEventToCache(course.idCourse, exerciseRoom.idRoom, exerciseProfId,
                    exerciseDay, currentTime, currentTime.plusHours(1), "LAB", scheduleId);
            currentTime = currentTime.plusHours(1);
        }

        return 2; // Successfully scheduled on 2 days
    }

    /**
     * Schedules course as one block (only for 4-hour courses if 2-day fails)
     * Only checks conflicts WITHIN the same schedule
     */
    private int scheduleAsOneBlockWithinSchedule(int scheduleId, Course course, int professorId,
            List<String> days, List<Room> rooms) throws SQLException {
        int totalHours = course.getTotalHoursPerWeek();

        for (String day : days) {
            for (Room room : rooms) {
                for (int hour = 8; hour <= 17 - totalHours; hour++) {
                    LocalTime startTime = LocalTime.of(hour, 0);
                    LocalTime endTime = startTime.plusHours(totalHours);

                    if (!hasConflictInSchedule(scheduleId, day, room.idRoom, professorId, startTime, endTime,
                            course.semester)) {
                        // Save each hour separately
                        LocalTime currentTime = startTime;

                        // Lectures
                        for (int i = 0; i < course.lecturesPerWeek; i++) {
                            LocalDateTime startsAt = convertDayToDate(day, currentTime);
                            LocalDateTime endsAt = startsAt.plusHours(1);
                            saveToAcademicEvent(scheduleId, course.idCourse, professorId,
                                    day, startsAt, endsAt, room.idRoom, "LECTURE");
                            addEventToCache(course.idCourse, room.idRoom, professorId,
                                    day, currentTime, currentTime.plusHours(1), "LECTURE", scheduleId);
                            currentTime = currentTime.plusHours(1);
                        }

                        // Exercises
                        for (int i = 0; i < course.exercisesPerWeek; i++) {
                            LocalDateTime startsAt = convertDayToDate(day, currentTime);
                            LocalDateTime endsAt = startsAt.plusHours(1);
                            saveToAcademicEvent(scheduleId, course.idCourse, professorId,
                                    day, startsAt, endsAt, room.idRoom, "EXERCISE");
                            addEventToCache(course.idCourse, room.idRoom, professorId,
                                    day, currentTime, currentTime.plusHours(1), "EXERCISE", scheduleId);
                            currentTime = currentTime.plusHours(1);
                        }

                        // Labs
                        for (int i = 0; i < course.labsPerWeek; i++) {
                            LocalDateTime startsAt = convertDayToDate(day, currentTime);
                            LocalDateTime endsAt = startsAt.plusHours(1);
                            saveToAcademicEvent(scheduleId, course.idCourse, professorId,
                                    day, startsAt, endsAt, room.idRoom, "LAB");
                            addEventToCache(course.idCourse, room.idRoom, professorId,
                                    day, currentTime, currentTime.plusHours(1), "LAB", scheduleId);
                            currentTime = currentTime.plusHours(1);
                        }

                        return 1; // Successfully scheduled as 1 block
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Checks for conflicts ONLY within the same schedule
     * Does NOT check against events in other schedules
     * Also checks for semester conflicts (two courses from the same semester at the
     * same time)
     */
    private boolean hasConflictInSchedule(int scheduleId, String day, int roomId, int professorId,
            LocalTime startTime, LocalTime endTime, int courseSemester) {
        for (AcademicEvent event : academicEvents.values()) {
            // Only check events in the SAME schedule
            if (event.scheduleId != scheduleId) {
                continue;
            }

            // Check day match
            boolean dayMatch = (day != null && event.day != null && event.day.equals(day));
            if (!dayMatch) {
                continue;
            }

            // Check time overlap first
            boolean timeOverlap = false;
            if (startTime != null && endTime != null && event.startTime != null && event.endTime != null) {
                timeOverlap = startTime.isBefore(event.endTime) && endTime.isAfter(event.startTime);
            }

            if (!timeOverlap) {
                continue;
            }

            // Check room conflict
            if (event.idRoom == roomId) {
                return true; // Room conflict
            }

            // Check professor conflict
            if (event.idProfessor == professorId) {
                return true; // Professor conflict
            }

            // Check semester conflict - courses from the same semester cannot overlap
            Course eventCourse = courses.get(event.idCourse);
            if (eventCourse != null && eventCourse.semester == courseSemester) {
                return true; // Semester conflict - students can't attend both
            }
        }

        return false; // No conflict
    }

    // Ne znam od kad je ovo ovjde, izgleda lose, al za svaki slucaj ga necu brisati.
    
    // // Overloaded method for backward compatibility (without semester check)
    // private boolean hasConflictInSchedule(int scheduleId, String day, int roomId, int professorId,
    //         LocalTime startTime, LocalTime endTime) {
    //     return hasConflictInSchedule(scheduleId, day, roomId, professorId, startTime, endTime, -1);
    // }

    private boolean hasConflictForDateInSchedule(int scheduleId, LocalDate date, int roomId, int professorId,
                                                 LocalTime startTime, LocalTime endTime, int courseSemester) {
        for (AcademicEvent event : academicEvents.values()) {
            if (event.scheduleId != scheduleId) {
                continue;
            }

            // Check timing conflict
            boolean timeOverlap = false;
            if (startTime != null && endTime != null && event.startTime != null && event.endTime != null) {
                timeOverlap = startTime.isBefore(event.endTime) && endTime.isAfter(event.startTime);
            }
            if (!timeOverlap) continue;

            // Check date/day match
            boolean conflictMatch = false;
            
            // If event is date-specific
            if (event.date != null) {
                if (event.date.equals(date)) {
                    conflictMatch = true;
                }
            } 
            // If event is recurring (no date, just day)
            else if (event.day != null) {
                 DayOfWeek eventDow = parseDayOfWeek(event.day);
                 if (eventDow == date.getDayOfWeek()) {
                     conflictMatch = true;
                 }
            }

            if (conflictMatch) {
                if (event.idRoom == roomId) return true;
                if (event.idProfessor == professorId) return true;
                
                // Check semester conflict
                Course eventCourse = courses.get(event.idCourse);
                if (eventCourse != null && eventCourse.semester == courseSemester) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addColloquiumToSchedule(int scheduleId, int courseId, int roomId, int professorId, 
                                        LocalDate date, LocalTime startTime, LocalTime endTime, String type) throws SQLException {
        String insert = "INSERT INTO academic_event (course_id, created_by_professor, type_enum, " +
                "starts_at, ends_at, room_id, is_published, locked_by_admin, schedule_id, day) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            LocalDateTime startsAt = LocalDateTime.of(date, startTime);
            LocalDateTime endsAt = LocalDateTime.of(date, endTime);
            
            String dayName = getCroatianDayName(date.getDayOfWeek());

            pstmt.setInt(1, courseId);
            pstmt.setLong(2, professorId);
            pstmt.setString(3, type);
            pstmt.setTimestamp(4, Timestamp.valueOf(startsAt));
            pstmt.setTimestamp(5, Timestamp.valueOf(endsAt));
            pstmt.setInt(6, roomId);
            pstmt.setBoolean(7, true);
            pstmt.setBoolean(8, false);
            pstmt.setInt(9, scheduleId);
            pstmt.setString(10, dayName);
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    AcademicEvent newEvent = new AcademicEvent();
                    newEvent.idAcademicEvent = id;
                    newEvent.idCourse = courseId;
                    newEvent.idRoom = roomId;
                    newEvent.idProfessor = professorId;
                    newEvent.typeEnum = type;
                    newEvent.date = date;
                    newEvent.day = dayName;
                    newEvent.startTime = startTime;
                    newEvent.endTime = endTime;
                    newEvent.startsAt = startsAt;
                    newEvent.endsAt = endsAt;
                    newEvent.scheduleId = scheduleId;
                    academicEvents.put(newEvent.idAcademicEvent, newEvent);
                }
            }
        }
    }

    private String getCroatianDayName(DayOfWeek dow) {
        switch (dow) {
            case MONDAY: return "ponedeljak";
            case TUESDAY: return "utorak";
            case WEDNESDAY: return "srijeda";
            case THURSDAY: return "cetvrtak";
            case FRIDAY: return "petak";
            case SATURDAY: return "subota";
            case SUNDAY: return "nedelja";
            default: return "ponedeljak";
        }
    }

    private void generateColloquiums(int scheduleId, List<FailedCourse> failedList) {
        System.out.println("Phase 4: Scheduling EXAMS/COLLOQUIUMS...");
        System.out.println("Academic Year Check: Winter=" + winterStart + ", Summer=" + summerStart);
        
        if (winterStart == null && summerStart == null) {
            System.out.println("Skipping colloquiums: Semester dates not defined. Please define an active Academic Year in Admin Panel.");
            return;
        }

        // Use larger rooms for exams
        List<Room> examRooms = getSuitableRooms(false, 40); 
        if (examRooms.isEmpty()) examRooms = getSuitableRooms(false, 20); // Fallback

        int colloquiumsScheduled = 0;

        for (Course course : courses.values()) {
            // Determine active semester start
            // Odd semesters = Winter, Even = Summer
            LocalDate semStart = (course.semester % 2 != 0) ? winterStart : summerStart;
            
            if (semStart == null) {
                // System.out.println("Skipping course " + course.code + ": No start date for semester " + course.semester);
                continue;
            }

            try {
                int profId = findLectureProfessor(course.idCourse);
                if (profId == 0) {
                     // System.out.println("Skipping course " + course.code + ": No professor assigned.");
                     continue; 
                }

                // Schedule Colloquium 1
                if (course.colloquium1Week != null && course.colloquium1Week > 0) {
                     boolean success = scheduleColloquium(scheduleId, course, 1, course.colloquium1Week, semStart, profId, examRooms, failedList);
                     if (success) colloquiumsScheduled++;
                } else {
                    // System.out.println("Course " + course.code + " has no Colloquium 1 week set.");
                }

                // Schedule Colloquium 2
                if (course.colloquium2Week != null && course.colloquium2Week > 0) {
                     boolean success = scheduleColloquium(scheduleId, course, 2, course.colloquium2Week, semStart, profId, examRooms, failedList);
                     if (success) colloquiumsScheduled++;
                }

            } catch(Exception e) {
                System.out.println("Error scheduling colloquiums for course " + course.idCourse + ": " + e.getMessage());
            }
        }
        System.out.println("Total Colloquiums Scheduled: " + colloquiumsScheduled);
    }

    private boolean scheduleColloquium(int scheduleId, Course course, int colNum, int week, LocalDate semStart, 
                                   int profId, List<Room> rooms, List<FailedCourse> failedList) throws SQLException {
        // Calculate week start
        LocalDate weekStartDate = semStart.plusWeeks(week - 1);
        // Try Saturday of that week
        LocalDate targetDate = weekStartDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
        
        // Check if date is a holiday
        for (Holiday holiday : holidays) {
            if (holiday.date.equals(targetDate)) {
                 System.out.println("Skipping colloquium for " + course.name + " on " + targetDate + " due to holiday: " + holiday.name);
                 return false;
            }
        }

        // Times to try
        LocalTime[] starts = { LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(15, 0), LocalTime.of(8,0), LocalTime.of(16,0) };
        boolean scheduled = false;

        for (LocalTime start : starts) {
            LocalTime end = start.plusHours(3); // 3 hours duration
            
            for (Room room : rooms) {
                if (!hasConflictForDateInSchedule(scheduleId, targetDate, room.idRoom, profId, start, end, course.semester)) {
                    addColloquiumToSchedule(scheduleId, course.idCourse, room.idRoom, profId, 
                                          targetDate, start, end, "COLLOQUIUM"); 
                    scheduled = true;
                    break;
                }
            }
            if (scheduled) break;
        }
        
        if (!scheduled) {
             System.out.println("Failed to schedule Colloquium " + colNum + " for Course " + course.name + " (" + course.code + ") Week " + week + " at " + targetDate);
        }
        return scheduled;
    }
}


//region Classes
class Course {
    public int idCourse;
    public String name;
    public int semester;
    public String code;
    public int lecturesPerWeek; // broj sati predavanja (P)
    public int exercisesPerWeek; // broj sati vježbi (V)
    public int labsPerWeek; // broj sati laboratorijskih vježbi (L)
    public boolean isOnline;
    public Integer colloquium1Week;
    public Integer colloquium2Week;

    // Vraća ukupan broj sati sedmično (P+V+L)
    public int getTotalHoursPerWeek() {
        return lecturesPerWeek + exercisesPerWeek + labsPerWeek;
    }

    // Da li zahtijeva dijeljenje na 2 dana (> 4 sata)
    public boolean requiresSplit() {
        return getTotalHoursPerWeek() > 4;
    }
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

class ScheduleResult {
    public int scheduleId;
    public int successfulCourses;
    public int failedCourses;
    public List<FailedCourse> failedCoursesList;
    public String message;
    public boolean success;
    public long timestamp;
    
    public ScheduleResult() {
        this.failedCoursesList = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }
}

class FailedCourse {
    public int courseId;
    public String courseName;
    public String reason;
    public int semester;
    
    public FailedCourse(int courseId, String courseName, String reason, int semester) {
        this.courseId = courseId;
        this.courseName = courseName;
        this.reason = reason;
        this.semester = semester;
    }
}

//endregion