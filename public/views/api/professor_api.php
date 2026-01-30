<?php

// =======================
// SESSION + DB CONNECTION
// =======================

session_start();
require_once __DIR__ . '/../../../config/dbconnection.php';

header('Content-Type: application/json; charset=utf-8');

if (!isset($_SESSION['professor_id'])) {
    echo json_encode(['error' => 'Not authenticated']);
    exit;
}

$professorId = (int)$_SESSION['professor_id'];


// =======================
// READ JSON INPUT
// =======================

$inputJSON = file_get_contents('php://input');
$inputData = json_decode($inputJSON, true);


// =======================
// ACTION ROUTER
// =======================

$action = $_GET['action'] ?? $inputData['action'] ?? null;

if (!$action) {
    echo json_encode(['error' => 'Missing action']);
    exit;
}


// =======================
// MAIN SWITCH
// =======================

switch ($action) {


// ==================================
// GET PROFESSOR SCHEDULE (calendar)
// ==================================

    case 'get_professor_schedule':

        try {
            $scheduleStmt = $pdo->prepare("
            SELECT DISTINCT schedule_id
            FROM academic_event
            WHERE schedule_id IS NOT NULL
            ORDER BY schedule_id DESC
            LIMIT 6
        ");
            $scheduleStmt->execute();
            $scheduleIds = $scheduleStmt->fetchAll(PDO::FETCH_COLUMN);
            $scheduleIds = array_reverse($scheduleIds);

            if (!$scheduleIds) {
                echo json_encode([
                    'schedules' => [],
                    'schedule_ids' => []
                ]);
                break;
            }

            $in = implode(',', array_fill(0, count($scheduleIds), '?'));

            $stmt = $pdo->prepare("
            SELECT
                ae.schedule_id,
                ae.day,
                ae.starts_at,
                ae.ends_at,
                c.name AS coursename,
                r.code AS roomcode,
                c.semester
            FROM academic_event ae
            JOIN course c ON ae.course_id = c.id
            LEFT JOIN room r ON ae.room_id = r.id
            WHERE (ae.created_by_professor = ?
               OR EXISTS (
                   SELECT 1 FROM event_professor ep
                   WHERE ep.event_id = ae.id
                   AND ep.professor_id = ?
               ))
              AND ae.type_enum IN ('LECTURE','EXERCISE','LAB')
              AND ae.schedule_id IN ($in)
            ORDER BY ae.schedule_id, c.semester, ae.day, ae.starts_at
        ");

            $params = array_merge([$professorId, $professorId], $scheduleIds);
            $stmt->execute($params);
            $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);

            $data = [
                'schedules' => [],
                'schedule_ids' => $scheduleIds
            ];

            foreach ($scheduleIds as $sid) {
                $data['schedules'][$sid] = [];
            }

            foreach ($rows as $row) {
                $sid = (int)$row['schedule_id'];
                $sem = (int)$row['semester'];

                if (!isset($data['schedules'][$sid][$sem])) {
                    $data['schedules'][$sid][$sem] = [];
                }

                $data['schedules'][$sid][$sem][] = [
                    'day' => (int)$row['day'],
                    'start' => substr($row['starts_at'], 11, 5),
                    'end' => substr($row['ends_at'], 11, 5),
                    'course' => $row['coursename'],
                    'room' => $row['roomcode']
                ];
            }

            echo json_encode($data, JSON_UNESCAPED_UNICODE);

        } catch (PDOException $e) {
            echo json_encode(['error' => $e->getMessage()]);
        }

        break;



// =======================
// GET HOLIDAYS
// =======================

    case 'get_holidays':

        try {
            $stmt = $pdo->query("SELECT date, name FROM holiday");
            echo json_encode($stmt->fetchAll(PDO::FETCH_ASSOC));
        } catch (PDOException $e) {
            echo json_encode([]);
        }

        break;



// =======================
// SAVE NOTE
// =======================

    case 'save_note':

        $date = $inputData['date'] ?? null;
        $content = trim($inputData['content'] ?? '');

        if (!$date || $content === '') {
            echo json_encode(['success' => false]);
            break;
        }

        $stmt = $pdo->prepare("
        INSERT INTO notes (professor_id, note_date, content)
        VALUES (?, ?, ?)
    ");
        $stmt->execute([$professorId, $date, $content]);

        echo json_encode([
            'success' => true,
            'id' => $pdo->lastInsertId()
        ]);

        break;



// =======================
// UPDATE NOTE
// =======================

    case 'update_note':

        $id = $inputData['id'] ?? null;
        $content = trim($inputData['content'] ?? '');

        if (!$id || $content === '') {
            echo json_encode(['success' => false]);
            break;
        }

        $stmt = $pdo->prepare("
        UPDATE notes
        SET content = ?
        WHERE id = ? AND professor_id = ?
    ");
        $stmt->execute([$content, $id, $professorId]);

        echo json_encode(['success' => true]);

        break;



// =======================
// GET NOTES
// =======================

    case 'get_notes':

        $stmt = $pdo->prepare("
        SELECT id, note_date, content
        FROM notes
        WHERE professor_id = ?
    ");
        $stmt->execute([$professorId]);

        echo json_encode($stmt->fetchAll(PDO::FETCH_ASSOC));

        break;



// =======================
// DELETE NOTE
// =======================

    case 'delete_note':

        $id = $inputData['id'] ?? null;

        if (!$id) {
            echo json_encode(['success' => false]);
            break;
        }

        $stmt = $pdo->prepare("
        DELETE FROM notes
        WHERE id = ? AND professor_id = ?
    ");
        $stmt->execute([$id, $professorId]);

        echo json_encode(['success' => true]);

        break;



// =======================
// EVENTS SUMMARY
// =======================

    case 'get_professor_events_summary':

        try {
            $stmt = $pdo->prepare("
            SELECT 
                ae.date::date AS event_date,
                SUM(CASE WHEN ae.type_enum='EXAM' THEN 1 ELSE 0 END) AS exams,
                SUM(CASE WHEN ae.type_enum='COLLOQUIUM' THEN 1 ELSE 0 END) AS colloquiums,
                COUNT(*) AS total
            FROM academic_event ae
            WHERE (ae.created_by_professor = ?
               OR EXISTS (
                   SELECT 1 FROM event_professor ep
                   WHERE ep.event_id = ae.id
                   AND ep.professor_id = ?
               ))
              AND ae.type_enum IN ('EXAM','COLLOQUIUM')
            GROUP BY ae.date::date
        ");

            $stmt->execute([$professorId, $professorId]);
            echo json_encode($stmt->fetchAll(PDO::FETCH_ASSOC));

        } catch (PDOException $e) {
            echo json_encode([]);
        }

        break;



// =======================
// SAVE AVAILABILITY
// =======================

    case 'save_availability':

        $availability = $inputData['data'] ?? [];

        if (!is_array($availability) || count($availability) === 0) {
            echo json_encode(['success' => false]);
            break;
        }

        try {
            $pdo->beginTransaction();

            $pdo->prepare("
            DELETE FROM professor_availability
            WHERE professor_id = ?
        ")->execute([$professorId]);

            $stmt = $pdo->prepare("
            INSERT INTO professor_availability
            (professor_id, weekday, start_time, end_time)
            VALUES (?, ?, ?, ?)
        ");

            $count = 0;

            foreach ($availability as $slot) {
                $day = (int)$slot['day'];
                if ($day < 1 || $day > 5) continue;

                $stmt->execute([
                    $professorId,
                    $day,
                    $slot['from'],
                    $slot['to']
                ]);
                $count++;
            }

            $pdo->commit();
            echo json_encode(['success' => true, 'count' => $count]);

        } catch (PDOException $e) {
            $pdo->rollBack();
            echo json_encode(['success' => false]);
        }

        break;



// =======================
// SAVE COLLOQUIUM WEEKS
// =======================

    case 'save_colloquium_weeks':

        $rows = $inputData['data'] ?? [];

        if (!is_array($rows)) {
            echo json_encode(['success' => false]);
            break;
        }

        try {
            $pdo->beginTransaction();

            $stmt = $pdo->prepare("
            UPDATE course
            SET colloquium_1_week = ?, colloquium_2_week = ?
            WHERE id = ?
        ");

            $count = 0;

            foreach ($rows as $r) {
                $id = (int)$r['course_id'];
                if (!$id) continue;

                $c1 = $r['colloquium_1_week'] ?: null;
                $c2 = $r['colloquium_2_week'] ?: null;

                $stmt->execute([$c1, $c2, $id]);
                $count++;
            }

            $pdo->commit();
            echo json_encode(['success' => true, 'count' => $count]);

        } catch (PDOException $e) {
            $pdo->rollBack();
            echo json_encode(['success' => false]);
        }

        break;



// =======================
// UNKNOWN ACTION
// =======================

    default:
        echo json_encode(['error' => 'Unknown action']);

}
