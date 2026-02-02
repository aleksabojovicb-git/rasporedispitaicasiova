import java.sql.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

public class ColloquiumService {

    private static class Course {
        int id;
        String name;
        int semester;
        Integer c1Week;
        Integer c2Week;
        String major;

        public Course(int id, String name, int semester, Integer c1Week, Integer c2Week, String major) {
            this.id = id;
            this.name = name;
            this.semester = semester;
            this.c1Week = c1Week;
            this.c2Week = c2Week;
            this.major = major;
        }
    }

    private static class TemplateEvent {
        int courseId;
        String day;
        LocalTime startTime;
        LocalTime endTime;
        int roomId;
        long professorId;
        int scheduleId;
    }

    private static class ProposedColloquium {
        Course course;
        String type; 
        int week;
        TemplateEvent template;
        LocalDate finalDate;

        public ProposedColloquium(Course course, String type, int week, TemplateEvent template) {
            this.course = course;
            this.type = type;
            this.week = week;
            this.template = template;
        }
    }

    private static class AcademicYear {
        LocalDate winterStart;
        LocalDate summerStart;
    }

    public String generateColloquiums() {
        Connection conn = null;
        try {
            conn = BazaInicijalizacija.uspostaviKonekciju();

            if (!isScheduleLocked(conn)) {
                return "Raspored predavanja nije zaključan od strane admina. Nije moguće generisati kolokvijume.";
            }

            AcademicYear year = loadAcademicYear(conn);
            if (year == null) return "GRESKA: Nema aktivne akademske godine.";

            List<Course> courses = loadCourses(conn);
            Map<Integer, TemplateEvent> templates = loadTemplates(conn);

            List<ProposedColloquium> finalProposals = new ArrayList<>();

            Map<Integer, List<Course>> bySemester = courses.stream().collect(Collectors.groupingBy(c -> c.semester));

            for (Map.Entry<Integer, List<Course>> entry : bySemester.entrySet()) {
                int semester = entry.getKey();
                List<Course> semCourses = entry.getValue();
                
                boolean isWinter = Arrays.asList(1, 3, 5).contains(semester);
                boolean isSummer = Arrays.asList(2, 4, 6).contains(semester);
                if (!isWinter && !isSummer) continue; 

                LocalDate semStart = isWinter ? year.winterStart : year.summerStart;
                if (semStart == null) continue;

                List<ProposedColloquium> col1List = new ArrayList<>();
                List<ProposedColloquium> col2List = new ArrayList<>();

                for (Course c : semCourses) {
                    TemplateEvent t = templates.get(c.id);
                    if (t == null) continue; 

                    if (c.c1Week == null) return "Za predmet " + c.name + " nije definisana sedmica za Kolokvijum 1.";
                    if (c.c1Week > 0) col1List.add(new ProposedColloquium(c, "COLLOQUIUM_1", c.c1Week, t));

                    if (c.c2Week == null) return "Za predmet " + c.name + " nije definisana sedmica za Kolokvijum 2.";
                    if (c.c2Week > 0) col2List.add(new ProposedColloquium(c, "COLLOQUIUM_2", c.c2Week, t));
                }

                try {
                    processSemesterList(col1List, semester);
                    processSemesterList(col2List, semester);
                } catch (Exception e) {
                    return "GRESKA: " + e.getMessage();
                }

                for (ProposedColloquium p : col1List) {
                    calculateDate(p, semStart);
                    finalProposals.add(p);
                }
                for (ProposedColloquium p : col2List) {
                    calculateDate(p, semStart);
                    finalProposals.add(p);
                }
            }

            deleteOldColloquiums(conn);
            insertNewColloquiums(conn, finalProposals);

            return "OK"; 

        } catch (Exception e) {
            e.printStackTrace();
            return "GRESKA: " + e.getMessage();
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    private void processSemesterList(List<ProposedColloquium> proposals, int semester) throws Exception {
        if (proposals.isEmpty()) return;

        // Apply special rules for 4th, 5th and 6th semester
        if (semester == 4 || semester == 5 || semester == 6) {
            processSpecialSemester(proposals, semester);
        } else {
            processStandardSemester(proposals, semester);
        }
    }

    private void processSpecialSemester(List<ProposedColloquium> proposals, int semester) throws Exception {
        // Sort proposals by requested week to respect original preferences where possible
        proposals.sort(Comparator.comparingInt(p -> p.week));

        Map<Integer, WeekStatus> schedule = new TreeMap<>();
        
        for (ProposedColloquium p : proposals) {
            int currentWeek = p.week;
            boolean placed = false;
            
            // Try subsequent weeks until a slot is found
            while (!placed) {
                // Safety break to prevent infinite loops (e.g., if we go way beyond semester bounds)
                if (currentWeek > p.week + 15) {
                    throw new Exception("Nemoguće rasporediti kolokvijume za semestar " + semester + 
                        " (Previše konflikata za predmet " + p.course.name + ")");
                }

                WeekStatus status = schedule.computeIfAbsent(currentWeek, k -> new WeekStatus());
                
                boolean isCommon = (p.course.major == null);
                boolean isSE = "Softverski inženjering".equals(p.course.major);
                boolean isICT = "Informaciono komunikacione tehnologije".equals(p.course.major);
                
                // RULES:
                // 1. Max 3 colloquiums per week
                if (status.count >= 3) {
                    currentWeek++;
                    continue;
                }
                
                // 2. Common courses MUST be in different weeks
                if (isCommon && status.hasCommon) {
                    currentWeek++;
                    continue;
                }
                
                // 3. Two courses of the same major CANNOT be in the same week
                if (isSE && status.hasSE) {
                    currentWeek++;
                    continue;
                }
                
                if (isICT && status.hasICT) {
                    currentWeek++;
                    continue;
                }

                // If all checks pass, place the colloquium here
                p.week = currentWeek;
                status.count++;
                if (isCommon) status.hasCommon = true;
                if (isSE) status.hasSE = true;
                if (isICT) status.hasICT = true;
                
                placed = true;
            }
        }
    }

    private static class WeekStatus {
        int count = 0;
        boolean hasCommon = false;
        boolean hasSE = false;
        boolean hasICT = false;
    }

    private void processStandardSemester(List<ProposedColloquium> proposals, int semester) throws Exception {
        if (proposals.isEmpty()) return;

        TreeMap<Integer, List<ProposedColloquium>> byWeek = new TreeMap<>();
        for (ProposedColloquium p : proposals) {
            byWeek.computeIfAbsent(p.week, k -> new ArrayList<>()).add(p);
        }

        if (byWeek.isEmpty()) return;

        int minWeek = byWeek.firstKey();
        int maxWeek = byWeek.lastKey(); 
        
        for (int w = minWeek; w <= maxWeek + 10; w++) { 
            List<ProposedColloquium> list = byWeek.get(w);
            if (list == null || list.size() <= 2) {
                 if (w > maxWeek && (list == null || list.isEmpty())) break;
                 continue;
            }

            List<ProposedColloquium> keep = new ArrayList<>(list.subList(0, 2));
            List<ProposedColloquium> move = new ArrayList<>(list.subList(2, list.size()));

            byWeek.put(w, keep);

            int nextW = w + 1;
            for (ProposedColloquium p : move) {
                p.week = nextW;
            }
            byWeek.computeIfAbsent(nextW, k -> new ArrayList<>()).addAll(move);
            if (nextW > maxWeek) maxWeek = nextW;
        }

        Set<Integer> weeksUsed = new HashSet<>();
        for (Map.Entry<Integer, List<ProposedColloquium>> entry : byWeek.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                weeksUsed.add(entry.getKey());
            }
        }
        
        if (weeksUsed.size() > 3) {
             throw new Exception("Kolokvijumi za semestar " + semester + " se rasprostiru na vise od 3 sedmice.");
        }
    }

    private boolean isScheduleLocked(Connection conn) throws SQLException {
        String query = "SELECT 1 FROM academic_event WHERE locked_by_admin = true LIMIT 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            return rs.next();
        }
    }

    private AcademicYear loadAcademicYear(Connection conn) throws SQLException {
        String query = "SELECT winter_semester_start, summer_semester_start FROM academic_year WHERE is_active = TRUE LIMIT 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                AcademicYear ay = new AcademicYear();
                java.sql.Date winter = rs.getDate("winter_semester_start");
                java.sql.Date summer = rs.getDate("summer_semester_start");
                if (winter != null) ay.winterStart = winter.toLocalDate();
                if (summer != null) ay.summerStart = summer.toLocalDate();
                return ay;
            }
        }
        return null;
    }

    private List<Course> loadCourses(Connection conn) throws SQLException {
        List<Course> list = new ArrayList<>();
        String query = "SELECT id, name, semester, colloquium_1_week, colloquium_2_week, major FROM course";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                Integer c1 = rs.getInt("colloquium_1_week");
                if (rs.wasNull()) c1 = null;
                Integer c2 = rs.getInt("colloquium_2_week");
                if (rs.wasNull()) c2 = null;
                String major = rs.getString("major");
                if (rs.wasNull()) major = null;
                list.add(new Course(rs.getInt("id"), rs.getString("name"), rs.getInt("semester"), c1, c2, major));
            }
        }
        return list;
    }

    private Map<Integer, TemplateEvent> loadTemplates(Connection conn) throws SQLException {
        Map<Integer, TemplateEvent> map = new HashMap<>();
        String query = "SELECT course_id, day, starts_at, ends_at, room_id, created_by_professor, schedule_id " +
                       "FROM academic_event " +
                       "WHERE type_enum = 'EXERCISE' AND locked_by_admin = true";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                TemplateEvent t = new TemplateEvent();
                t.courseId = rs.getInt("course_id");
                t.day = rs.getString("day");
                t.roomId = rs.getInt("room_id");
                t.professorId = rs.getLong("created_by_professor");
                t.scheduleId = rs.getInt("schedule_id");
                Timestamp start = rs.getTimestamp("starts_at");
                Timestamp end = rs.getTimestamp("ends_at");
                if (start != null) t.startTime = start.toLocalDateTime().toLocalTime();
                if (end != null) t.endTime = end.toLocalDateTime().toLocalTime();
                map.putIfAbsent(t.courseId, t);
            }
        }
        return map;
    }
    
    private void calculateDate(ProposedColloquium p, LocalDate semesterStart) {
        if (p.week <= 0) return;
        LocalDate startOfWeek1 = semesterStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate startOfTargetWeek = startOfWeek1.plusWeeks(p.week - 1);
        
        DayOfWeek dow = parseDayOfWeek(p.template.day);
        
        LocalDate date = startOfTargetWeek;
        while (date.getDayOfWeek() != dow) {
            date = date.plusDays(1);
        }
        p.finalDate = date;
    }

    private DayOfWeek parseDayOfWeek(String day) {
        if (day == null) return DayOfWeek.MONDAY;
        String d = day.toLowerCase();
        if (d.contains("pon")) return DayOfWeek.MONDAY;
        if (d.contains("uto")) return DayOfWeek.TUESDAY;
        if (d.contains("sri") || d.contains("sre")) return DayOfWeek.WEDNESDAY;
        if (d.contains("cet") || d.contains("čet")) return DayOfWeek.THURSDAY;
        if (d.contains("pet")) return DayOfWeek.FRIDAY;
        if (d.contains("sub")) return DayOfWeek.SATURDAY;
        if (d.contains("ned")) return DayOfWeek.SUNDAY;
        return DayOfWeek.MONDAY; 
    }

    private void deleteOldColloquiums(Connection conn) throws SQLException {
        String sql = "DELETE FROM academic_event WHERE type_enum IN ('COLLOQUIUM', 'COLLOQUIUM_1', 'COLLOQUIUM_2')";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void insertNewColloquiums(Connection conn, List<ProposedColloquium> proposals) throws SQLException {
        String sql = "INSERT INTO academic_event " +
                "(course_id, type_enum, starts_at, ends_at, room_id, created_by_professor, schedule_id, locked_by_admin, notes, day, is_published) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ProposedColloquium p : proposals) {
                LocalDateTime startDt = LocalDateTime.of(p.finalDate, p.template.startTime);
                LocalDateTime endDt = LocalDateTime.of(p.finalDate, p.template.endTime);
                
                ps.setInt(1, p.course.id);
                ps.setString(2, p.type); 
                ps.setTimestamp(3, Timestamp.valueOf(startDt));
                ps.setTimestamp(4, Timestamp.valueOf(endDt));
                ps.setInt(5, p.template.roomId);
                ps.setLong(6, p.template.professorId);
                ps.setInt(7, p.template.scheduleId);
                // Prompt rule: "ne smijes mijenjati... raspored predavanja". 
                // But this is inserting NEW colloquiums.
                // Prompt: "Ako vrijednost 0 -> taj kolokvijum se ne odrzava" (Handled by >0 check)
                
                // Are we locking these? The prompt implies they are generated ON TOP OF locked schedule.
                // It doesn't strictly say if colloquiums are locked. Usually they are visible.
                ps.setBoolean(8, true); // Let's lock them to avoid accidental manual move that breaks the logic, or false?
                // Actually, if we lock them, admins can't move them. Usually they need to be adjustable.
                // BUT "Kolokvijumi se ne raspoređuju slobodno... u istom terminu kao vježbe".
                // If the user wants specific rules, maybe locking is correct. I'll stick to true as per prev logic.
                
                ps.setString(9, "generated");
                ps.setString(10, p.template.day);
                ps.setBoolean(11, true);

                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
