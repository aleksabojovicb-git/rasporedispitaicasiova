<?php

session_start();
header('Content-Type: application/json; charset=utf-8');
require_once __DIR__ . '/../../config/dbconnection.php';
// Ensure user is logged in and is ADMIN
if (!isset($_SESSION['role']) || $_SESSION['role'] !== 'ADMIN') {
    http_response_code(403);
    echo json_encode([
        'success' => false,
        'message' => 'Nemate dozvolu za ovu akciju.'
    ]);
    exit;
}

// Check if request method is POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Samo POST zahtevi su dozvoljeni.'
    ]);
    exit;
}

// Get JSON data
$input = json_decode(file_get_contents('php://input'), true);

if (!isset($input['action']) || !isset($input['is_locked'])) {
    http_response_code(400);
    echo json_encode([
        'success' => false,
        'message' => 'Nedostaju potrebni parametri.'
    ]);
    exit;
}

$action = $input['action'];
$is_locked = (bool)$input['is_locked'];
$winter_schedule_id = isset($input['winter_schedule_id']) ? (int)$input['winter_schedule_id'] : 0;
$summer_schedule_id = isset($input['summer_schedule_id']) ? (int)$input['summer_schedule_id'] : 0;

try {
    // Include database connection
    require_once __DIR__ . '/../../config/dbconnection.php';

    // Save lock state to academic_event table
    if ($action === 'toggle_lock') {
        if ($winter_schedule_id <= 0 || $summer_schedule_id <= 0) {
            http_response_code(400);
            echo json_encode([
                'success' => false,
                'message' => 'Neispravan winter ili summer schedule_id.'
            ]);
            exit;
        }

        // Update locked_by_admin status
        $locked_value = $is_locked ? 1 : 0;
        
        $rows_winter = 0;
        $rows_summer = 0;
        
        // Update zimski raspored (semestri 1, 3, 5)
        // Join with course table to filter by semester
        $stmt_winter = $pdo->prepare("
            UPDATE academic_event ae
            SET locked_by_admin = :locked
            FROM course c
            WHERE ae.course_id = c.id
              AND c.semester IN (1, 3, 5)
              AND ae.schedule_id = :schedule_id
        ");
        $stmt_winter->execute([
            ':locked' => $locked_value,
            ':schedule_id' => $winter_schedule_id
        ]);
        $rows_winter = $stmt_winter->rowCount();
        
        // Update ljetnji raspored (semestri 2, 4, 6) - only if different from winter
        if ($summer_schedule_id !== $winter_schedule_id) {
            $stmt_summer = $pdo->prepare("
                UPDATE academic_event ae
                SET locked_by_admin = :locked
                FROM course c
                WHERE ae.course_id = c.id
                  AND c.semester IN (2, 4, 6)
                  AND ae.schedule_id = :schedule_id
            ");
            $stmt_summer->execute([
                ':locked' => $locked_value,
                ':schedule_id' => $summer_schedule_id
            ]);
            $rows_summer = $stmt_summer->rowCount();
        }
        
        $total_rows_affected = $rows_winter + $rows_summer;

        // Build appropriate message
        if ($winter_schedule_id === $summer_schedule_id) {
            $message = $is_locked 
                ? 'Raspored ID ' . $winter_schedule_id . ' (' . $rows_winter . ' termina) je zaključan.' 
                : 'Raspored ID ' . $winter_schedule_id . ' (' . $rows_winter . ' termina) je otključan.';
        } else {
            $message = $is_locked 
                ? 'Zimski raspored ID ' . $winter_schedule_id . ' (' . $rows_winter . ' termina) i Ljetnji raspored ID ' . $summer_schedule_id . ' (' . $rows_summer . ' termina) su zaključani.' 
                : 'Zimski raspored ID ' . $winter_schedule_id . ' (' . $rows_winter . ' termina) i Ljetnji raspored ID ' . $summer_schedule_id . ' (' . $rows_summer . ' termina) su otključani.';
        }

        echo json_encode([
            'success' => true,
            'message' => $message,
            'is_locked' => $is_locked,
            'winter_schedule_id' => $winter_schedule_id,
            'summer_schedule_id' => $summer_schedule_id,
            'winter_rows_affected' => $rows_winter,
            'summer_rows_affected' => $rows_summer,
            'total_rows_affected' => $total_rows_affected
        ]);
    } else {
        http_response_code(400);
        echo json_encode([
            'success' => false,
            'message' => 'Nepoznata akcija: ' . $action
        ]);
    }

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'Greška baze podataka: ' . $e->getMessage()
    ]);
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'Greška: ' . $e->getMessage()
    ]);
}
?>
