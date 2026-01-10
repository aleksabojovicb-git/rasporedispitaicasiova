
<?php
session_start();
require_once __DIR__ . '/../../config/dbconnection.php';

if (isset($_GET['action']) && $_GET['action'] === 'getschedule') {
    header('Content-Type: application/json; charset=utf-8');

    try {
        // Get all distinct schedule_ids (latest 6)
        $scheduleStmt = $pdo->prepare("
            SELECT DISTINCT schedule_id 
            FROM academic_event 
            WHERE schedule_id IS NOT NULL 
            ORDER BY schedule_id DESC 
            LIMIT 6
        ");
        $scheduleStmt->execute();
        $scheduleIds = $scheduleStmt->fetchAll(PDO::FETCH_COLUMN);
        
        // Reverse to show oldest first (1, 2, 3, 4, 5, 6)
        $scheduleIds = array_reverse($scheduleIds);

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
            WHERE ae.type_enum IN ('LECTURE', 'EXERCISE', 'LAB')
              AND ae.schedule_id IN (" . implode(',', array_fill(0, count($scheduleIds), '?')) . ")
            ORDER BY ae.schedule_id, c.semester, ae.day, ae.starts_at
        ");
        $stmt->execute($scheduleIds);
        $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);

        // Group by schedule_id -> semester -> events
        $data = [
            'schedules' => [],
            'schedule_ids' => $scheduleIds
        ];
        
        foreach ($scheduleIds as $sid) {
            $data['schedules'][$sid] = [];
        }
        
        foreach ($rows as $row) {
            $schedId = (int)$row['schedule_id'];
            $sem = (int)$row['semester'];

            if (!isset($data['schedules'][$schedId][$sem])) {
                $data['schedules'][$schedId][$sem] = [];
            }

            $data['schedules'][$schedId][$sem][] = [
                'day'    => (int)$row['day'],
                'start'  => substr($row['starts_at'], 11, 5),
                'end'    => substr($row['ends_at'],   11, 5),
                'course' => $row['coursename'],
                'room'   => $row['roomcode']
            ];
        }

        echo json_encode($data, JSON_UNESCAPED_UNICODE);
    } catch (PDOException $e) {
        echo json_encode(['error' => 'Greška pri čitanju rasporeda: ' . $e->getMessage()]);
    }
    exit;
}

if (isset($_GET['action']) && $_GET['action'] === 'generateschedule') {
    // Očisti sve output buffere da osiguramo čist JSON
    while (ob_get_level()) {
        ob_end_clean();
    }
    
    header('Content-Type: application/json; charset=utf-8');
    
    // Provera da li je korisnik ADMIN
    if (!isset($_SESSION['role']) || $_SESSION['role'] !== 'ADMIN') {
        echo json_encode(['status' => 'error', 'message' => 'Nemate dozvolu za ovu akciju.']);
        exit;
    }

    try {
        // Postavi working directory na root projekta (gde se nalazi .env fajl)
        $projectRoot = dirname(__DIR__, 2); // dva nivoa iznad public/views
        if (!is_dir($projectRoot)) {
            echo json_encode(['status' => 'error', 'message' => 'Root direktorijum projekta nije pronađen.']);
            exit;
        }
        chdir($projectRoot);
        
        // Putanja do Java fajlova
        $javaDir = $projectRoot . DIRECTORY_SEPARATOR . 'public' . DIRECTORY_SEPARATOR . 'java';
        $jarFile = $javaDir . DIRECTORY_SEPARATOR . 'postgresql-42.7.8.jar';
        
        // Provera da li Java fajlovi postoje
        if (!file_exists($javaDir . DIRECTORY_SEPARATOR . 'ValidacijaTermina.class')) {
            echo json_encode(['status' => 'error', 'message' => 'Java klasa ValidacijaTermina nije pronađena u: ' . $javaDir]);
            exit;
        }
        
        if (!file_exists($jarFile)) {
            echo json_encode(['status' => 'error', 'message' => 'PostgreSQL JDBC driver nije pronađen: ' . $jarFile]);
            exit;
        }
        
        // Formiranje Java komande
        // Na Windows-u koristimo ; kao separator, na Linux/Mac koristimo :
        $separator = (strtoupper(substr(PHP_OS, 0, 3)) === 'WIN') ? ';' : ':';
        $classpath = $javaDir . $separator . $jarFile;
        
        // Komanda za pokretanje Java programa
        // Koristimo shell_exec za bolje hvatanje output-a
        $command = sprintf(
            'java -cp "%s" ValidacijaTermina generisiKompletan 2>&1',
            $classpath
        );
        
        // Izvršavanje komande i hvatanje output-a
        $outputString = shell_exec($command);
        
        // Ako shell_exec vrati null, pokušaj sa exec
        if ($outputString === null) {
            $output = [];
            $returnCode = 0;
            exec($command, $output, $returnCode);
            $outputString = implode("\n", $output);
            
            if ($returnCode !== 0 && empty($outputString)) {
                echo json_encode(['status' => 'error', 'message' => 'Java program nije mogao biti pokrenut. Proverite da li je Java instaliran i u PATH-u.']);
                exit;
            }
        }
        
        // Provera da li imamo output
        if (empty($outputString) || trim($outputString) === '') {
            echo json_encode(['status' => 'error', 'message' => 'Java program nije vratio nikakav output.']);
            exit;
        }
        
        // Funkcija za očišćavanje UTF-8 stringa
        function cleanUtf8($string) {
            // Prvo pokušaj da konvertuješ u UTF-8
            if (!mb_check_encoding($string, 'UTF-8')) {
                // Ako nije validan UTF-8, pokušaj da konvertuješ
                $string = mb_convert_encoding($string, 'UTF-8', mb_detect_encoding($string, 'UTF-8, ISO-8859-1, Windows-1252', true));
            }
            
            // Ukloni nevalidne UTF-8 karaktere
            $string = mb_convert_encoding($string, 'UTF-8', 'UTF-8');
            
            // Ukloni kontrolne karaktere osim novih linija i tabova
            $string = preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/u', '', $string);
            
            return $string;
        }
        
        // Očisti output od nevalidnih UTF-8 karaktera
        $outputString = cleanUtf8($outputString);
        
        // Parsiranje output-a da vidimo da li je uspešno
        $isSuccess = false;
        $message = '';
        
        if (stripos($outputString, 'OK:') !== false) {
            $isSuccess = true;
            // Izvuci poruku nakon "OK:"
            $okPos = stripos($outputString, 'OK:');
            $message = trim(substr($outputString, $okPos + 3));
            // Uzmi samo prvu liniju poruke
            $lines = explode("\n", $message);
            $message = trim($lines[0]);
        } elseif (stripos($outputString, 'WARNING:') !== false) {
            $isSuccess = true; // Warning se smatra delimičnim uspehom
            $warningPos = stripos($outputString, 'WARNING:');
            $message = trim(substr($outputString, $warningPos + 8));
            $lines = explode("\n", $message);
            $message = trim($lines[0]);
        } elseif (stripos($outputString, 'ERROR:') !== false) {
            $errorPos = stripos($outputString, 'ERROR:');
            $message = trim(substr($outputString, $errorPos + 6));
            $lines = explode("\n", $message);
            $message = trim($lines[0]);
        } else {
            // Ako nema eksplicitne poruke, koristimo ceo output (ali ograničimo dužinu)
            $message = !empty($outputString) ? trim(substr($outputString, 0, 500)) : 'Raspored je generisan.';
            $isSuccess = true; // Pretpostavljamo uspeh ako nema eksplicitne greške
        }
        
        // Očisti message od potencijalnih JSON problematičnih karaktera
        $message = cleanUtf8($message);
        $message = str_replace(["\r", "\n"], " ", $message);
        $message = preg_replace('/\s+/', ' ', $message);
        $message = trim($message);
        
        $response = [
            'status' => $isSuccess ? 'success' : 'error',
            'message' => $message
        ];
        
        // Dodaj output samo ako nije previše dug (da ne pravi probleme sa JSON-om)
        if (strlen($outputString) < 10000) {
            $cleanOutput = cleanUtf8($outputString);
            $response['output'] = $cleanOutput;
        }
        
        // Koristi JSON_INVALID_UTF8_IGNORE flag ako je dostupan (PHP 7.2+)
        $jsonFlags = JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES;
        if (defined('JSON_INVALID_UTF8_IGNORE')) {
            $jsonFlags |= JSON_INVALID_UTF8_IGNORE;
        }
        
        $jsonResponse = json_encode($response, $jsonFlags);
        
        if ($jsonResponse === false) {
            // Ako i dalje ima problema, pokušaj da očistiš sve ne-ASCII karaktere iz poruke
            $safeMessage = preg_replace('/[^\x20-\x7E]/u', '', $message);
            if (empty($safeMessage)) {
                $safeMessage = 'Raspored je generisan (neki karakteri su uklonjeni zbog enkodiranja).';
            }
            echo json_encode(['status' => $isSuccess ? 'success' : 'error', 'message' => $safeMessage], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        } else {
            echo $jsonResponse;
        }
        
    } catch (Exception $e) {
        echo json_encode(['status' => 'error', 'message' => 'Greška: ' . $e->getMessage()]);
    } catch (Error $e) {
        echo json_encode(['status' => 'error', 'message' => 'Fatalna greška: ' . $e->getMessage()]);
    }
    exit;
}




// Only ADMIN can view this page.
// Redirect others.
if (!isset($_SESSION['role']) || !isset($_SESSION['user_id'])) {
    // Not loggedIn -> go to auth
    header('Location: ./authorization.php');
    exit;
}

if ($_SESSION['role'] !== 'ADMIN') {
    // LoggedIn but not admin -> go to professor profile
    header('Location: ./professor_panel.php');
    exit;
}

// Process form submissions
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (isset($_POST['action'])) {
        switch ($_POST['action']) {
            case 'add_profesor':
                $full_name = $_POST['full_name'];
                $email = $_POST['email'];

                try {
                    $stmt = $pdo->prepare("INSERT INTO professor (full_name, email, is_active) VALUES (?, ?, TRUE)");
                    $stmt->execute([$full_name, $email]);
                    header("Location: ?page=profesori&success=1&message=" . urlencode("Profesor je uspješno dodat."));
                    exit;
                } catch (PDOException $e) {
                    $error = "Greška pri dodavanju profesora: " . $e->getMessage();
                }
                break;

            case 'add_predmet':
                $name = $_POST['name'];
                $semester = $_POST['semester'];
                $code = $_POST['code'];
                $is_optional = isset($_POST['is_optional']) ? 1 : 0;

                try {
                    $stmt = $pdo->prepare("INSERT INTO course (name, semester, code, is_optional, is_active) 
                                          VALUES (?, ?, ?, ?, TRUE)");
                    $stmt->execute([$name, $semester, $code, $is_optional]);
                    header("Location: ?page=predmeti&success=1&message=" . urlencode("Predmet je uspješno dodat."));
                    exit;
                } catch (PDOException $e) {
                    $error = "Greška pri dodavanju predmeta: " . $e->getMessage();
                }
                break;

            case 'add_sala':
                $code = $_POST['code'];
                $capacity = $_POST['capacity'];
                $is_computer_lab = isset($_POST['is_computer_lab']) ? 1 : 0;

                try {
                    $stmt = $pdo->prepare("INSERT INTO room (code, capacity, is_computer_lab, is_active) 
                                          VALUES (?, ?, ?, TRUE)");
                    $stmt->execute([$code, $capacity, $is_computer_lab]);
                    header("Location: ?page=sale&success=1&message=" . urlencode("Sala je uspješno dodata."));
                    exit;
                } catch (PDOException $e) {
                    $error = "Greška pri dodavanju sale: " . $e->getMessage();
                }
                break;

            case 'add_dogadjaj':
                $course_id = $_POST['course_id'];
                $professor_id = $_POST['professor_id'];
                $type = $_POST['type'];
                $starts_at = $_POST['starts_at'];
                $ends_at = $_POST['ends_at'];
                $is_online = isset($_POST['is_online']) ? 1 : 0;
                $room_id = $is_online ? null : $_POST['room_id'];
                $notes = $_POST['notes'];
                $is_published = isset($_POST['is_published']) ? 1 : 0;

                try {
                    $pdo->beginTransaction();

                    $stmt = $pdo->prepare("INSERT INTO academic_event 
                                          (course_id, created_by_professor, type_enum, starts_at, ends_at, 
                                           is_online, room_id, notes, is_published, locked_by_admin) 
                                          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)");
                    $stmt->execute([$course_id, $professor_id, $type, $starts_at, $ends_at,
                        $is_online, $room_id, $notes, $is_published]);

                    $event_id = $pdo->lastInsertId();

                    // Dodavanje veze događaj-profesor
                    $stmt = $pdo->prepare("INSERT INTO event_professor (event_id, professor_id) 
                                          VALUES (?, ?)");
                    $stmt->execute([$event_id, $professor_id]);

                    $pdo->commit();
                    header("Location: ?page=dogadjaji&success=1&message=" . urlencode("Događaj je uspješno dodat."));
                    exit;
                } catch (PDOException $e) {
                    $pdo->rollBack();
                    $error = "Greška pri dodavanju događaja: " . $e->getMessage();
                }
                break;

            case 'assign_professor':
                // Pridruživanje profesora predmetu
                $course_id = isset($_POST['course_id']) ? (int)$_POST['course_id'] : 0;
                $professor_id = isset($_POST['professor_id']) ? (int)$_POST['professor_id'] : 0;
                $is_assistant = isset($_POST['is_assistant']) ? 1 : 0;

                if ($course_id <= 0 || $professor_id <= 0) {
                    $error = "Morate izabrati i predmet i profesora.";
                    break;
                }

                try {
                    // Provjeri postojeće veze za predmet
                    $stmt = $pdo->prepare("SELECT professor_id, is_assistant FROM course_professor WHERE course_id = ?");
                    $stmt->execute([$course_id]);
                    $existing = $stmt->fetchAll(PDO::FETCH_ASSOC);

                    // Ako je profesor već pridružen - zabranjeno
                    foreach ($existing as $ex) {
                        if ((int)$ex['professor_id'] === $professor_id) {
                            $error = "Odabrani profesor je već pridružen ovom predmetu.";
                            break 2;
                        }
                    }

                    $count = count($existing);

                    if ($count >= 2) {
                        $error = "Na predmetu već postoje dva predavača. Ne možete dodati trećeg.";
                        break;
                    }

                    if ($count === 1) {
                        $existingRole = (int)$existing[0]['is_assistant'];
                        // Ako bi nastala dva ista tipa (dva profesora ili dva asistenta) - zabranjeno
                        if ($existingRole === $is_assistant) {
                            $error = "Ako postoje dva predavača, moraju biti profesor i asistent (ne mogu biti oba ista uloga).";
                            break;
                        }
                    }

                    // Ubaci vezu (role_enum ima podrazumijevanu vrijednost u bazi)
                    $stmt = $pdo->prepare("INSERT INTO course_professor (course_id, professor_id, is_assistant) VALUES (?, ?, ?)");
                    $stmt->execute([$course_id, $professor_id, $is_assistant]);

                    header("Location: ?page=predmeti&success=1&message=" . urlencode("Profesor je uspješno pridružen predmetu."));
                    exit;
                } catch (PDOException $e) {
                    $error = "Greška pri povezivanju profesora i predmeta: " . $e->getMessage();
                }
                break;


            // Brisanje i deaktiviranje
            case 'delete_professor':
                if (isset($_POST['id']) && is_numeric($_POST['id'])) {
                    $id = (int)$_POST['id'];

                    try {
                        $stmt = $pdo->prepare("UPDATE professor SET is_active = FALSE WHERE id = ?");
                        $stmt->execute([$id]);

                        header("Location: ?page=profesori&success=1&message=" . urlencode("Profesor je uspješno deaktiviran."));
                        exit;
                    } catch (PDOException $e) {
                        $error = "Greška pri deaktiviranju profesora: " . $e->getMessage();
                    }
                }
                break;

            case 'delete_predmet':
                if (isset($_POST['id']) && is_numeric($_POST['id'])) {
                    $id = (int)$_POST['id'];

                    try {
                        $stmt = $pdo->prepare("UPDATE course SET is_active = FALSE WHERE id = ?");
                        $stmt->execute([$id]);

                        header("Location: ?page=predmeti&success=1&message=" . urlencode("Predmet je uspješno deaktiviran."));
                        exit;
                    } catch (PDOException $e) {
                        $error = "Greška pri deaktiviranju predmeta: " . $e->getMessage();
                    }
                }
                break;

            case 'delete_sala':
                if (isset($_POST['id']) && is_numeric($_POST['id'])) {
                    $id = (int)$_POST['id'];

                    try {
                        $stmt = $pdo->prepare("UPDATE room SET is_active = FALSE WHERE id = ?");
                        $stmt->execute([$id]);

                        header("Location: ?page=sale&success=1&message=" . urlencode("Sala je uspješno deaktivirana."));
                        exit;
                    } catch (PDOException $e) {
                        $error = "Greška pri deaktiviranju sale: " . $e->getMessage();
                    }
                }
                break;

            case 'delete_dogadjaj':
                if (isset($_POST['id']) && is_numeric($_POST['id'])) {
                    $id = (int)$_POST['id'];

                    try {
                        $pdo->beginTransaction();

                        $stmt = $pdo->prepare("DELETE FROM event_professor WHERE event_id = ?");
                        $stmt->execute([$id]);

                        $stmt = $pdo->prepare("DELETE FROM academic_event WHERE id = ?");
                        $stmt->execute([$id]);

                        $pdo->commit();
                        header("Location: ?page=dogadjaji&success=1&message=" . urlencode("Događaj je uspješno obrisan."));
                        exit;
                    } catch (PDOException $e) {
                        $pdo->rollBack();
                        $error = "Greška pri brisanju događaja: " . $e->getMessage();
                    }
                }
                break;

            // ****  update  ****

            case 'update_profesor':

                $fields=[];
                $params=[];

                if(isset($_POST['full_name'])){
                    $fields[]="full_name = ?";
                    $params[]=$_POST['full_name'];
                }
                if(isset($_POST['email'])){
                    $fields[]="email = ?";
                    $params[]=$_POST['email'];
                }
                if (!isset($_POST['profesor_id'])) {
                    throw new Exception("Profesorov ID nije validan.");
                }

                $params[] = (int)$_POST['profesor_id'];

                if (empty($fields)) {
                    throw new Exception("Nema podataka za ažuriranje.");
                }


                try {
                    $stmt = $pdo->prepare("UPDATE professor SET ". implode(', ',$fields) ." WHERE id=?");
                    $stmt->execute($params);
                    header("Location: ?page=profesori&success=1&message=" . urlencode("Profesor je uspješno ažuriran."));
                    exit;
                } catch (PDOException $e) {
                    $error = "Greška pri ažuriranju profesora: " . $e->getMessage();
                }
                break;

            case 'update_predmet':
                $fields=[];
                $params=[];

                if(isset($_POST['name'])){
                    $fields[] = "name = ?";
                    $params[] = $_POST['name'];
                }
                if(isset($_POST['semester'])){
                    $fields[] = "semester = ?";
                    $params[] = $_POST['semester'];
                }
                if(isset($_POST['code'])){
                    $fields[] = "code = ?";
                    $params[] = $_POST['code'];
                }
                if(isset($_POST['is_optional'])){
                    $fields[] = "is_optional = ?";
                    $params[] = isset($_POST['is_optional']) ? 1 : 0;
                }

                if (!isset($_POST['course_id'])) {
                    throw new Exception("Course ID nije validan.");
                }

                $params[] = (int)$_POST['course_id'];

                if (empty($fields)) {
                    throw new Exception("Nema podataka za ažuriranje.");
                }


                try {
                    $stmt = $pdo->prepare("UPDATE course SET ". implode(', ',$fields) ." WHERE id=?");
                    $stmt->execute($params);
                    // Ako su poslata prof_assignments polja, obraditi ih (zamijeni postojeće veze)
                    if (isset($_POST['prof_assignments'])) {
                        $raw = $_POST['prof_assignments'];
                        $assignments = json_decode($raw, true);
                        if (!is_array($assignments)) {
                            throw new Exception('Neispravan format podataka o profesorima.');
                        }

                        // Basic validation
                        $count = count($assignments);
                        if ($count > 2) throw new Exception('Ne može biti više od dva predavača.');

                        $ids = [];
                        $assistants = 0;
                        foreach ($assignments as $a) {
                            // accept either professor_id or id (frontend may send {id:...})
                            if (!isset($a['professor_id']) && !isset($a['id'])) throw new Exception('Nedostaje professor_id.');
                            $pid = (int)($a['professor_id'] ?? $a['id']);
                            if ($pid <= 0) throw new Exception('Neispravan professor_id.');
                            if (in_array($pid, $ids)) throw new Exception('Isti profesor ne može biti u više uloga.');
                            $ids[] = $pid;
                            $is_asst = isset($a['is_assistant']) ? (int)$a['is_assistant'] : 0;
                            if ($is_asst) $assistants++;
                        }
                        if ($count === 2 && $assistants !== 1) throw new Exception('Ako su dva predavača, mora biti jedan profesor i jedan asistent.');

                        // Zamijeni veze u transakciji
                        $pdo->beginTransaction();
                        $stmt = $pdo->prepare("DELETE FROM course_professor WHERE course_id = ?");
                        $stmt->execute([(int)$_POST['course_id']]);

                        if ($count > 0) {
                            $ins = $pdo->prepare("INSERT INTO course_professor (course_id, professor_id, is_assistant) VALUES (?, ?, ?)");
                            foreach ($assignments as $a) {
                                $pid = (int)($a['professor_id'] ?? $a['id']);
                                $ins->execute([(int)$_POST['course_id'], $pid, isset($a['is_assistant']) && $a['is_assistant'] ? 1 : 0]);
                            }
                        }
                        $pdo->commit();
                    }
                    header("Location: ?page=predmeti&success=1&message=" . urlencode("Predmet je uspješno ažuriran."));
                    exit;
                } catch (PDOException $e) {
                    if ($pdo->inTransaction()) $pdo->rollBack();
                    $error = "Greška pri ažuriranju predmeta: " . $e->getMessage();
                }
                break;

            case 'update_sala':
                $fields=[];
                $params=[];

                if(isset($_POST['code'])){
                    $fields[] = "code = ?";
                    $params[] = $_POST['code'];
                }
                if(isset($_POST['capacity'])){
                    $fields[] = "capacity = ?";
                    $params[] = $_POST['capacity'];
                }
                if(isset($_POST['is_computer_lab'])){
                    $fields[] = "is_computer_lab = ?";
                    $params[] = isset($_POST['is_computer_lab']) ? 1 : 0;
                }

                if (!isset($_POST['sala_id'])) {
                    throw new Exception("Sala ID nije validan.");
                }

                $params[] = (int)$_POST['sala_id'];

                if (empty($fields)) {
                    throw new Exception("Nema podataka za ažuriranje.");
                }

                try {
                    $stmt = $pdo->prepare("UPDATE room SET ". implode(', ',$fields) ." WHERE id=?");
                    $stmt->execute($params);
                    header("Location: ?page=sale&success=1&message=" . urlencode("Sala je uspješno ažurirana."));
                    exit;
                } catch (PDOException $e) {
                    $error = "Greška pri ažuriranju sale: " . $e->getMessage();
                }
                break;

            case 'update_dogadjaj':
                $fields=[];
                $params=[];

                $fields2=[];
                $params2=[];

                if(isset($_POST['course_id'])){
                    $fields[] = "course_id = ?";
                    $params[] = $_POST['course_id'];
                }
                if(isset($_POST['professor_id'])){
                    $fields2[] = "professor_id = ?";
                    $params2[] = $_POST['professor_id'];
                }
                if(isset($_POST['type'])){
                    // DB column is 'type_enum'
                    $fields[] = "type_enum = ?";
                    $params[] = $_POST['type'];
                }
                if(isset($_POST['starts_at'])){
                    $fields[] = "starts_at = ?";
                    $params[] = $_POST['starts_at'];
                }
                if(isset($_POST['ends_at'])){
                    $fields[] = "ends_at = ?";
                    $params[] = $_POST['ends_at'];
                }
                if(isset($_POST['is_online'])){
                    $fields[] = "is_online = ?";
                    $params[] = isset($_POST['is_online']) ? 1 : 0;
                }
                if(isset($_POST['room_id'])){
                    $fields[] = "room_id = ?";
                    $params[] = $_POST['room_id'];
                }
                if(isset($_POST['notes'])){
                    $fields[] = "notes = ?";
                    $params[] = $_POST['notes'];
                }
                if(isset($_POST['is_published'])){
                    $fields[] = "is_published = ?";
                    $params[] = isset($_POST['is_published']) ? 1 : 0;
                }

                if (!isset($_POST['dogadjaj_id'])) {
                    throw new Exception("Dogadjaj ID nije validan.");
                }
                // dogadjaj id
                $params[] = (int)$_POST['dogadjaj_id'];
                // If frontend didn't provide event_professor_id, try to lookup; if none, we'll insert later.
                $need_insert_event_prof = false;
                if (!isset($_POST['event_professor_id'])) {
                    try {
                        $stmtEp = $pdo->prepare("SELECT id FROM event_professor WHERE event_id = ? LIMIT 1");
                        $stmtEp->execute([(int)$_POST['dogadjaj_id']]);
                        $epRow = $stmtEp->fetch(PDO::FETCH_ASSOC);
                        if ($epRow && isset($epRow['id'])) {
                            $_POST['event_professor_id'] = (int)$epRow['id'];
                        } else {
                            // mark that we need to insert a new event_professor row after updating the event
                            $need_insert_event_prof = true;
                        }
                    } catch (PDOException $e) {
                        // DB error while looking up — mark for insert (so we attempt to create row if possible)
                        $need_insert_event_prof = true;
                    }
                }

                if (empty($fields)) {
                    throw new Exception("Nema podataka za ažuriranje.");
                }


                try {
                    $pdo->beginTransaction();

                    $stmt = $pdo->prepare("UPDATE academic_event SET ". implode(', ',$fields) ." WHERE id=?");
                    $stmt->execute($params);

                    $event_id = (int)$_POST['dogadjaj_id'];;

                    // Handle event_professor relation: update existing or insert new
                    if (isset($_POST['event_professor_id']) && (int)$_POST['event_professor_id'] > 0) {
                        // update the existing relation's professor if professor_id provided
                        if (isset($_POST['professor_id'])) {
                            $upd = $pdo->prepare("UPDATE event_professor SET professor_id = ? WHERE id = ?");
                            $upd->execute([(int)$_POST['professor_id'], (int)$_POST['event_professor_id']]);
                        }
                    } else {
                        // no existing relation id — replace any existing relation for this event with new one (if professor_id present)
                        if (isset($_POST['professor_id'])) {
                            // remove any existing relations for safety
                            $del = $pdo->prepare("DELETE FROM event_professor WHERE event_id = ?");
                            $del->execute([$event_id]);
                            // insert new relation
                            $ins = $pdo->prepare("INSERT INTO event_professor (event_id, professor_id) VALUES (?, ?)");
                            $ins->execute([$event_id, (int)$_POST['professor_id']]);
                        }
                    }

                    $pdo->commit();
                    header("Location: ?page=dogadjaji&success=1&message=" . urlencode("Događaj je uspješno ažuriran."));
                    exit;
                } catch (PDOException $e) {
                    $pdo->rollBack();
                    $error = "Greška pri ažuriranju događaja: " . $e->getMessage();
                }
                break;

            // --------- user_account management ---------
            case 'add_account':
                $username = trim($_POST['username'] ?? '');
                $password = $_POST['password'] ?? '';
                $role = $_POST['role'] ?? 'USER';
                $professor_id = isset($_POST['professor_id']) && is_numeric($_POST['professor_id']) ? (int)$_POST['professor_id'] : null;

                if ($username === '' || $password === '') {
                    $error = 'Username i lozinka su obavezni.';
                    break;
                }

                try {
                    // check unique username
                    $stmt = $pdo->prepare("SELECT id FROM user_account WHERE username = ? LIMIT 1");
                    $stmt->execute([$username]);
                    if ($stmt->fetch()) {
                        $error = 'Korisničko ime već postoji.';
                        break;
                    }

                    $password_hash = password_hash($password, PASSWORD_DEFAULT);
                    $stmt = $pdo->prepare("INSERT INTO user_account (username, password_hash, role_enum, is_active, professor_id) VALUES (?, ?, ?, TRUE, ?)");
                    $stmt->execute([$username, $password_hash, $role, $professor_id]);

                    header("Location: ?page=account&success=1&message=" . urlencode("Korisnik je uspješno dodat."));
                    exit;
                } catch (PDOException $e) {
                    $error = 'Greška pri dodavanju korisnika: ' . $e->getMessage();
                }
                break;

            case 'update_account':
                if (!isset($_POST['account_id']) || !is_numeric($_POST['account_id'])) {
                    $error = 'Neispravan ID korisnika.';
                    break;
                }
                $accId = (int)$_POST['account_id'];
                $fields = [];
                $params = [];

                if (isset($_POST['username']) && trim($_POST['username']) !== '') {
                    $fields[] = 'username = ?';
                    $params[] = trim($_POST['username']);
                }
                if (isset($_POST['password']) && $_POST['password'] !== '') {
                    $fields[] = 'password_hash = ?';
                    $params[] = password_hash($_POST['password'], PASSWORD_DEFAULT);
                }
                if (isset($_POST['role'])) {
                    $fields[] = 'role_enum = ?';
                    $params[] = $_POST['role'];
                }
                if (isset($_POST['is_active'])) {
                    $fields[] = 'is_active = ?';
                    $params[] = isset($_POST['is_active']) && $_POST['is_active'] ? 1 : 0;
                }
                if (array_key_exists('professor_id', $_POST)) {
                    $prof = is_numeric($_POST['professor_id']) ? (int)$_POST['professor_id'] : null;
                    $fields[] = 'professor_id = ?';
                    $params[] = $prof;
                }

                if (empty($fields)) {
                    $error = 'Nema podataka za ažuriranje.';
                    break;
                }

                $params[] = $accId;
                try {
                    $stmt = $pdo->prepare('UPDATE user_account SET ' . implode(', ', $fields) . ' WHERE id = ?');
                    $stmt->execute($params);
                    header("Location: ?page=account&success=1&message=" . urlencode("Korisnik je uspješno ažuriran."));
                    exit;
                } catch (PDOException $e) {
                    $error = 'Greška pri ažuriranju korisnika: ' . $e->getMessage();
                }
                break;

            case 'delete_account':
                if (isset($_POST['id']) && is_numeric($_POST['id'])) {
                    $id = (int)$_POST['id'];
                    try {
                        $stmt = $pdo->prepare("UPDATE user_account SET is_active = FALSE WHERE id = ?");
                        $stmt->execute([$id]);
                        header("Location: ?page=account&success=1&message=" . urlencode("Korisnik je uspješno deaktiviran."));
                        exit;
                    } catch (PDOException $e) {
                        $error = 'Greška pri deaktiviranju korisnika: ' . $e->getMessage();
                    }
                }
                break;

            case 'activate_account':
                if (isset($_POST['id']) && is_numeric($_POST['id'])) {
                    $id = (int)$_POST['id'];
                    try {
                        $stmt = $pdo->prepare("UPDATE user_account SET is_active = TRUE WHERE id = ?");
                        $stmt->execute([$id]);
                        header("Location: ?page=account&success=1&message=" . urlencode("Korisnik je uspješno aktiviran."));
                        exit;
                    } catch (PDOException $e) {
                        $error = 'Greška pri aktiviranju korisnika: ' . $e->getMessage();
                    }
                }
                break;

            // --------- end user_account management ---------
        }
    }
}
?>

<!DOCTYPE html>
<html lang="sr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Admin Panel - Raspored Ispita</title>
    <link rel="stylesheet" href="../assets/css/admin.css" />
    <link rel="stylesheet" href="../assets/css/base.css" />
    <link rel="stylesheet" href="../assets/css/fields.css" />
    <link rel="stylesheet" href="../assets/css/colors.css" />
    <link rel="stylesheet" href="../assets/css/stacks.css" />
    <link rel="stylesheet" href="../assets/css/tabs.css" />
    <link rel="stylesheet" href="../assets/css/table.css" />

    <?php // expose active professors to JS before admin.js loads ?>
    <script>
        window.adminData = window.adminData || {};
        window.adminData.professors = <?php
        try {
            $stmtForJS = $pdo->query("SELECT id, full_name, email FROM professor WHERE is_active = TRUE ORDER BY full_name");
            $rows = $stmtForJS->fetchAll(PDO::FETCH_ASSOC);
            echo json_encode($rows, JSON_UNESCAPED_UNICODE);
        } catch (PDOException $e) {
            echo '[]';
        }
        ?> || [];
    </script>
    <script src="../assets/js/admin.js" defer></script>
</head>
<body>

<header>
    <h1>Admin Panel</h1>
    <nav>
        <ul>
            <li><a href="index.php">Pocetna</a></li>
            <li><a href="?page=profesori">Profesori</a></li>
            <li><a href="?page=predmeti">Predmeti</a></li>
            <li><a href="?page=dogadjaji">Događaji</a></li>
            <li><a href="?page=sale">Sale</a></li>
            <li><a href="?page=account">Nalog</a></li>
            <li><a href="?page=logout">Rasporedi</a></li>
            <li><a href="logout.php">Odjavi se</a></li>
        </ul>
    </nav>
</header>

<main>
    <?php if (isset($error)): ?>
        <div class="error"><?php echo $error; ?></div>
    <?php endif; ?>

    <?php if (isset($_GET['success'])): ?>
        <div class="success">
            <?php
            if (isset($_GET['message'])) {
                echo htmlspecialchars($_GET['message']);
            } else {
                echo "Operacija je uspješno izvršena!";
            }
            ?>
        </div>
    <?php endif; ?>

    <?php
    $page = isset($_GET['page']) ? $_GET['page'] : 'pocetna';

    switch ($page) {
    case 'profesori':
    ?>
    <h2>Upravljanje Profesorima</h2>
    <button class="action-button add-button" onclick="toggleForm('profesorForm')">+ Dodaj Profesora</button>

    <div id="profesorForm" class="form-container" style="display: none">
        <h3>Novi profesor</h3>
        <form method="post">
            <input type="hidden" name="action" value="add_profesor">

            <label for="full_name">Ime i prezime:</label>
            <input type="text" id="full_name" name="full_name" required>

            <label for="email">Email:</label>
            <input type="email" id="email" name="email" required>

            <button type="submit">Sačuvaj</button>
        </form>
    </div>

    <table border="1" cellpadding="5">
        <tr>
            <th>ID</th>
            <th>Ime i prezime</th>
            <th>Email</th>
            <th>Status</th>
            <th>Akcije</th>
        </tr>
        <?php

        try {
            $stmt = $pdo->query("SELECT * FROM professor ORDER BY id");
            while ($row = $stmt->fetch()) {
                echo "<tr>";
                echo "<td>" . htmlspecialchars($row['id']) . "</td>";
                echo "<td>" . htmlspecialchars($row['full_name']) . "</td>";
                echo "<td>" . htmlspecialchars($row['email']) . "</td>";
                echo "<td>" . ($row['is_active'] ? 'Aktivan' : 'Neaktivan') . "</td>";
                echo "<td>";
                echo "<button class='action-button edit-button' data-entity='profesor' data-id='" . $row['id'] . "' data-full_name='" . htmlspecialchars($row['full_name'], ENT_QUOTES) . "' data-email='" . htmlspecialchars($row['email'], ENT_QUOTES) . "'>Uredi</button>";

                if ($row['is_active']) {
                    echo "<form id='delete-profesor-{$row['id']}' style='display:inline' method='post' action='{$_SERVER['PHP_SELF']}'>
                            <input type='hidden' name='action' value='delete_professor'>
                            <input type='hidden' name='id' value='{$row['id']}'>
                            <button type='button' class='action-button delete-button' onclick=\"submitDeleteForm({$row['id']}, 'delete_professor', 'profesor')\">Deaktiviraj</button>
                        </form>";
                }

                echo "</td>";
                echo "</tr>";
            }

        } catch (PDOException $e) {
            echo "<tr><td colspan='5'>Greška pri dohvaćanju profesora: " . $e->getMessage() . "</td></tr>";
        }
        echo "</table>";
        break;
        case 'predmeti':
        ?>

        <h2>Upravljanje Predmetima</h2>
        <button class="action-button add-button" onclick="toggleForm('predmetForm')">+ Dodaj Predmet</button>
        <button class="action-button add-button" onclick="toggleForm('assignForm')">+ Pridruži Profesora</button>
        <div id="assignForm" class="form-container" style="display: none">
            <h3>Pridruži profesora predmetu</h3>
            <form method="post">
                <input type="hidden" name="action" value="assign_professor">

                <label for="assign_course_id">Predmet:</label>
                <select id="assign_course_id" name="course_id" required>
                    <option value="">-- Odaberite predmet --</option>
                    <?php
                    try {
                        $stmt = $pdo->query("SELECT id, name, code FROM course WHERE is_active = TRUE ORDER BY name");
                        while ($c = $stmt->fetch()) {
                            echo "<option value='" . $c['id'] . "'>" . htmlspecialchars($c['name']) . " (" . htmlspecialchars($c['code']) . ")</option>";
                        }
                    } catch (PDOException $e) {
                        echo "<option value=''>Greška pri dohvaćanju predmeta</option>";
                    }
                    ?>
                </select>

                <label for="assign_professor_id">Profesor:</label>
                <select id="assign_professor_id" name="professor_id" required>
                    <option value="">-- Odaberite profesora --</option>
                    <?php
                    try {
                        // Dohvati profesore i izbroji koliko njih ima isti full_name
                        $stmt = $pdo->query("SELECT id, full_name, email FROM professor WHERE is_active = TRUE ORDER BY full_name");
                        $professors = [];
                        $nameCounts = [];
                        while ($p = $stmt->fetch()) {
                            $professors[] = $p;
                            $nameCounts[$p['full_name']] = ($nameCounts[$p['full_name']] ?? 0) + 1;
                        }

                        foreach ($professors as $p) {
                            $label = htmlspecialchars($p['full_name']);
                            if ($nameCounts[$p['full_name']] > 1) {
                                $label .= ' (' . htmlspecialchars($p['email']) . ')';
                            }
                            echo "<option value='" . $p['id'] . "'>" . $label . "</option>";
                        }
                    } catch (PDOException $e) {
                        echo "<option value=''>Greška pri dohvaćanju profesora</option>";
                    }
                    ?>
                </select>

                <label for="is_assistant"><input type="checkbox" id="is_assistant" name="is_assistant"> Označi ako je asistent</label>

                <button type="submit">Sačuvaj</button>
            </form>
        </div>
        <div id="predmetForm" class="form-container" style="display: none">
            <h3>Novi predmet</h3>
            <form method="post">
                <input type="hidden" name="action" value="add_predmet">

                <label for="name">Naziv predmeta:</label>
                <input type="text" id="name" name="name" required>

                <label for="code">Šifra predmeta:</label>
                <input type="text" id="code" name="code" required>

                <label for="semester">Semestar:</label>
                <input type="number" id="semester" name="semester" min="1" max="6" required>

                <label for="is_optional">Izborni predmet:</label>
                <input type="checkbox" id="is_optional" name="is_optional">

                <label for="lecture_hours">Broj časova predavanja (nedjeljno):</label>
                <input type="number" id="lecture_hours" name="lecture_hours" min="0" max="10" value="0">

                <label for="exercise_hours">Broj časova vježbi (nedjeljno):</label>
                <input type="number" id="exercise_hours" name="exercise_hours" min="0" max="10" value="0">

                // za ovu funkciju fali backend
                <button type="submit">Sačuvaj</button>
            </form>
        </div>

        <table border="1" cellpadding="5">
            <tr>
                <th>ID</th>
                <th>Naziv</th>
                <th>Šifra</th>
                <th>Semestar</th>
                <th>Obavezni</th>
                <th>Profesori</th>
                <th>Predavanja / sedmično</th>
                <th>Vježbe / sedmično</th>
                <th>Status</th>
                <th>Akcije</th>


            </tr>
            <?php

            try {
                $stmt = $pdo->query("SELECT * FROM course ORDER BY id");
                while ($row = $stmt->fetch()) {
                    echo "<tr>";
                    echo "<td>" . htmlspecialchars($row['id']) . "</td>";
                    echo "<td>" . htmlspecialchars($row['name']) . "</td>";
                    echo "<td>" . htmlspecialchars($row['code']) . "</td>";
                    echo "<td>" . htmlspecialchars($row['semester']) . "</td>";


                    // Obavezni (is_optional == 0 -> Obavezni = Da)
                    $is_mandatory = $row['is_optional'] ? 'Ne' : 'Da';
                    echo "<td>" . $is_mandatory . "</td>";

                    // Dohvati pridružene profesore za prikaz i za edit payload
                    $stmt2 = $pdo->prepare("SELECT cp.professor_id, cp.is_assistant, p.full_name, p.email FROM course_professor cp JOIN professor p ON cp.professor_id = p.id WHERE cp.course_id = ?");
                    $stmt2->execute([$row['id']]);
                    $assigned = $stmt2->fetchAll(PDO::FETCH_ASSOC);

                    // Build display for professors column
                    $profDisplay = [];
                    $profPayload = [];
                    foreach ($assigned as $a) {
                        $displayName = htmlspecialchars($a['full_name']);
                        if ($a['is_assistant']) $displayName .= ' A';
                        $profDisplay[] = $displayName;
                        $profPayload[] = ['id' => (int)$a['professor_id'], 'full_name' => $a['full_name'], 'email' => $a['email'], 'is_assistant' => (int)$a['is_assistant']];
                    }
                    echo "<td>" . implode(', ', $profDisplay) . "</td>";
                    echo "<td>fali backend</td>";
                    echo "<td>fali backend</td>";
                    echo "<td>" . ($row['is_active'] ? 'Aktivan' : 'Neaktivan') . "</td>";
                    echo "<td>";
                    // Attach professors payload as JSON on the edit button
                    $profJson = htmlspecialchars(json_encode($profPayload), ENT_QUOTES);
                    echo "<button class='action-button edit-button' data-entity='predmet' data-id='" . $row['id'] . "' data-name='" . htmlspecialchars($row['name'], ENT_QUOTES) . "' data-code='" . htmlspecialchars($row['code'], ENT_QUOTES) . "' data-semester='" . htmlspecialchars($row['semester'], ENT_QUOTES) . "' data-is_optional='" . ($row['is_optional'] ? '1' : '0') . "' data-professors='" . $profJson . "'>Uredi</button>";

                    // Ako je predmet neaktivan ne moze imati deaktiviraj dugme
                    if ($row['is_active']) {
                        echo "<form id='delete-predmet-{$row['id']}' style='display:inline' method='post' action='{$_SERVER['PHP_SELF']}'>
                            <input type='hidden' name='action' value='delete_predmet'>
                            <input type='hidden' name='id' value='{$row['id']}'>
                            <button type='button' class='action-button delete-button' onclick=\"submitDeleteForm({$row['id']}, 'delete_predmet', 'predmet')\">Deaktiviraj</button>
                        </form>";
                    }

                    echo "</td>";
                    echo "</tr>";
                }
            } catch (PDOException $e) {
                echo "<tr><td colspan='6'>Greška pri dohvaćanju predmeta: " . $e->getMessage() . "</td></tr>";
            }
            echo "</table>";
            break;
            case 'dogadjaji':
            ?>

            <h2>Upravljanje Događajima</h2>
            <button class="action-button add-button" onclick="toggleForm('dogadjajForm')">+ Dodaj Događaj</button>
            <?php

            echo "<div id='dogadjajForm' class='form-container' style='display: none'>";
            echo "<h3>Novi događaj</h3>";
            echo "<form method='post'>";
            echo "<input type='hidden' name='action' value='add_dogadjaj'>";

            // Odabir predmeta
            echo "<label for='course_id'>Predmet:</label>";
            echo "<select id='course_id' name='course_id' required>";
            try {
                $stmt = $pdo->query("SELECT id, name, code FROM course WHERE is_active = TRUE ORDER BY name");
                while ($row = $stmt->fetch()) {
                    echo "<option value='" . $row['id'] . "'>" . htmlspecialchars($row['name']) . " (" . htmlspecialchars($row['code']) . ")</option>";
                }
            } catch (PDOException $e) {
                echo "<option value=''>Greška pri dohvaćanju predmeta</option>";
            }
            echo "</select>";

            // Odabir profesora
            echo "<label for='professor_id'>Profesor:</label>";
            echo "<select id='professor_id' name='professor_id' required>";
            try {
                $stmt = $pdo->query("SELECT id, full_name, email FROM professor WHERE is_active = TRUE ORDER BY full_name");
                while ($row = $stmt->fetch()) {
                    echo "<option value='" . $row['id'] . "'>" . htmlspecialchars($row['full_name']) . " (" . htmlspecialchars($row['email']) . ")</option>";
                }
            } catch (PDOException $e) {
                echo "<option value=''>Greška pri dohvaćanju profesora</option>";
            }
            echo "</select>";

            // Tip dogadjaja
            echo "<label for='type'>Tip događaja:</label>";
            echo "<select id='type' name='type' required>";
            echo "<option value='EXAM'>Ispit</option>";
            echo "<option value='COLLOQUIUM'>Kolokvijum</option>";
            echo "</select>";

            echo "<label for='starts_at'>Početak:</label>";
            echo "<input type='datetime-local' id='starts_at' name='starts_at' required>";

            echo "<label for='ends_at'>Kraj:</label>";
            echo "<input type='datetime-local' id='ends_at' name='ends_at' required>";

            echo "<label for='is_online'>Online događaj:</label>";
            echo "<input type='checkbox' id='is_online' name='is_online'>";

            // Odabir sale (prikaži se samo ako nije online)
            echo "<div id='room_selection'>";
            echo "<label for='room_id'>Sala:</label>";
            echo "<select id='room_id' name='room_id'>";
            try {
                $stmt = $pdo->query("SELECT id, code, capacity FROM room WHERE is_active = TRUE ORDER BY code");
                while ($row = $stmt->fetch()) {
                    echo "<option value='" . $row['id'] . "'>" . htmlspecialchars($row['code']) . " (kapacitet: " . htmlspecialchars($row['capacity']) . ")</option>";
                }
            } catch (PDOException $e) {
                echo "<option value=''>Greška pri dohvaćanju sala</option>";
            }
            echo "</select></div>";

            echo "<label for='notes'>Napomene:</label>";
            echo "<textarea id='notes' name='notes' rows='3'></textarea>";

            echo "<label for='is_published'>Objavljeno:</label>";
            echo "<input type='checkbox' id='is_published' name='is_published' checked>";

            echo "<button type='submit'>Sačuvaj</button>";
            echo "</form></div>";

            // Prikaz sala je u admin.js????

            // Prikaz događaja
            ?>
            <table border="1" cellpadding="5">
                <tr>
                    <th>ID</th>
                    <th>Predmet</th>
                    <th>Tip</th>
                    <th>Početak</th>
                    <th>Kraj</th>
                    <th>Sala</th>
                    <th>Napomena</th>
                    <th>Akcije</th>
                </tr>
                <?php

                try {
                    $stmt = $pdo->query("
                    SELECT e.*, c.name as course_name, r.code as room_code 
                    FROM academic_event e
                    LEFT JOIN course c ON e.course_id = c.id
                    LEFT JOIN room r ON e.room_id = r.id
                    ORDER BY e.starts_at DESC
                ");
                    while ($row = $stmt->fetch()) {
                        $event_type = '';
                        switch ($row['type_enum']) {
                            case 'EXAM': $event_type = 'Ispit'; break;
                            case 'COLLOQUIUM': $event_type = 'Kolokvijum'; break;
                        }

                        echo "<tr>";
                        echo "<td>" . htmlspecialchars($row['id']) . "</td>";
                        echo "<td>" . htmlspecialchars($row['course_name']) . "</td>";
                        echo "<td>" . $event_type . "</td>";
                        echo "<td>" . date('d.m.Y H:i', strtotime($row['starts_at'])) . "</td>";
                        echo "<td>" . date('d.m.Y H:i', strtotime($row['ends_at'])) . "</td>";
                        echo "<td>" . ($row['is_online'] ? 'Online' : htmlspecialchars($row['room_code'])) . "</td>";
                        echo "<td>" . htmlspecialchars($row['notes']) . "</td>";
                        echo "<td>";
                        echo "<button class='action-button edit-button' data-entity='dogadjaj' data-id='" . $row['id'] . "' data-course_id='" . $row['course_id'] . "' data-professor_id='" . $row['created_by_professor'] . "' data-type='" . htmlspecialchars($row['type_enum'], ENT_QUOTES) . "' data-starts_at='" . htmlspecialchars($row['starts_at'], ENT_QUOTES) . "' data-ends_at='" . htmlspecialchars($row['ends_at'], ENT_QUOTES) . "' data-is_online='" . ($row['is_online'] ? '1' : '0') . "' data-room_id='" . $row['room_id'] . "' data-notes='" . htmlspecialchars($row['notes'], ENT_QUOTES) . "' data-is_published='" . ($row['is_published'] ? '1' : '0') . "'>Uredi</button> ";
                        echo "<form id='delete-dogadjaj-{$row['id']}' style='display:inline' method='post' action='{$_SERVER['PHP_SELF']}'>
                            <input type='hidden' name='action' value='delete_dogadjaj'>
                            <input type='hidden' name='id' value='{$row['id']}'>
                            <button type='button' class='action-button delete-button' onclick=\"submitDeleteForm({$row['id']}, 'delete_dogadjaj', 'dogadjaj')\">Obriši</button>
                        </form>
                    </td>";
                        echo "</tr>";
                    }
                } catch (PDOException $e) {
                    echo "<tr><td colspan='8'>Greška pri dohvaćanju događaja: " . $e->getMessage() . "</td></tr>";
                }
                echo "</table>";
                break;
                case 'sale':
                ?>

                <h2>Upravljanje Salama</h2>
                <button class="action-button add-button" onclick="toggleForm('salaForm')">+ Dodaj Salu</button>

                <div id="salaForm" class="form-container" style='display: none'>
                    <h3>Nova sala</h3>
                    <form method="post">
                        <input type="hidden" name="action" value="add_sala">

                        <label for="code">Oznaka sale:</label>
                        <input type="text" id="code" name="code" required>

                        <label for="capacity">Kapacitet:</label>
                        <input type="number" id="capacity" name="capacity" min="1" required>

                        <label for="is_computer_lab">Računarska sala:</label>
                        <input type="checkbox" id="is_computer_lab" name="is_computer_lab">

                        <button type="submit">Sačuvaj</button>
                    </form>
                </div>

                <table border="1" cellpadding="5">
                    <tr>
                        <th>ID</th>
                        <th>Oznaka</th>
                        <th>Kapacitet</th>
                        <th>Tip</th>
                        <th>Status</th>
                        <th>Akcije</th>
                    </tr>
                    <?php

                    try {
                        $stmt = $pdo->query("SELECT * FROM room ORDER BY code");
                        while ($row = $stmt->fetch()) {
                            echo "<tr>";
                            echo "<td>" . htmlspecialchars($row['id']) . "</td>";
                            echo "<td>" . htmlspecialchars($row['code']) . "</td>";
                            echo "<td>" . htmlspecialchars($row['capacity']) . "</td>";
                            echo "<td>" . ($row['is_computer_lab'] ? 'Računarska' : 'Standardna') . "</td>";
                            echo "<td>" . ($row['is_active'] ? 'Aktivna' : 'Neaktivna') . "</td>";
                            echo "<td>";
                            echo "<button class='action-button edit-button' data-entity='sala' data-id='" . $row['id'] . "' data-code='" . htmlspecialchars($row['code'], ENT_QUOTES) . "' data-capacity='" . htmlspecialchars($row['capacity'], ENT_QUOTES) . "' data-is_computer_lab='" . ($row['is_computer_lab'] ? '1' : '0') . "'>Uredi</button>";

                            // Ako je sala neaktivna ne moze imati deaktiviraj dugme
                            if ($row['is_active']) {
                                echo "<form id='delete-sala-{$row['id']}' style='display:inline' method='post' action='{$_SERVER['PHP_SELF']}'>
                            <input type='hidden' name='action' value='delete_sala'>
                            <input type='hidden' name='id' value='{$row['id']}'>
                            <button type='button' class='action-button delete-button' onclick=\"submitDeleteForm({$row['id']}, 'delete_sala', 'salu')\">Deaktiviraj</button>
                        </form>";
                            }

                            echo "</td>";
                            echo "</tr>";
                        }
                    } catch (PDOException $e) {
                        echo "<tr><td colspan='6'>Greška pri dohvaćanju sala: " . $e->getMessage() . "</td></tr>";
                    }
                    echo "</table>";
                    break;
                    case 'account':
                        ?>

                        <h2>Upravljanje Nalozima</h2>
                        <button class="action-button add-button" onclick="toggleForm('accountForm')">+ Dodaj Nalog</button>

                        <div id="accountForm" class="form-container" style="display: none">
                            <h3>Novi nalog</h3>
                            <form method="post">
                                <input type="hidden" name="action" value="add_account">

                                <label for="username">Korisničko ime:</label>
                                <input type="text" id="username" name="username" required>

                                <label for="password">Lozinka:</label>
                                <input type="password" id="password" name="password" required>

                                <label for="role">Uloga:</label>
                                <select id="role" name="role">
                                    <option value="ADMIN">ADMIN</option>
                                    <option value="PROFESSOR">PROFESSOR</option>
                                    <option value="USER">USER</option>
                                </select>

                                <label for="professor_id">Povezan profesor (opcionalno):</label>
                                <select id="professor_id" name="professor_id">
                                    <option value="">-- Nema --</option>
                                    <?php
                                    try {
                                        $stmt = $pdo->query("SELECT id, full_name, email FROM professor WHERE is_active = TRUE ORDER BY full_name");
                                        while ($p = $stmt->fetch()) {
                                            echo "<option value='" . $p['id'] . "'>" . htmlspecialchars($p['full_name']) . " (" . htmlspecialchars($p['email']) . ")</option>";
                                        }
                                    } catch (PDOException $e) {
                                        echo "<option value=''>Greška pri dohvaćanju profesora</option>";
                                    }
                                    ?>
                                </select>

                                <button type="submit">Sačuvaj</button>
                            </form>
                        </div>

                        <table border="1" cellpadding="5">
                            <tr>
                                <th>ID</th>
                                <th>Username</th>
                                <th>Role</th>
                                <th>Profesor</th>
                                <th>Status</th>
                                <th>Akcije</th>
                            </tr>
                            <?php
                            try {
                                // Sada takođe biramo email povezane profesorke
                                $stmt = $pdo->query("SELECT ua.*, p.full_name as professor_name, p.email as professor_email FROM user_account ua LEFT JOIN professor p ON ua.professor_id = p.id ORDER BY ua.id");
                                while ($row = $stmt->fetch()) {
                                    echo "<tr>";
                                    echo "<td>" . htmlspecialchars($row['id']) . "</td>";
                                    echo "<td>" . htmlspecialchars($row['username']) . "</td>";
                                    echo "<td>" . htmlspecialchars($row['role_enum']) . "</td>";
                                    // Pokazujemo ime profesora i, ako postoji, njegov email
                                    $profDisplay = htmlspecialchars($row['professor_name']);
                                    if (!empty($row['professor_email'])) {
                                        $profDisplay .= ' (' . htmlspecialchars($row['professor_email']) . ')';
                                    }
                                    echo "<td>" . $profDisplay . "</td>";
                                    echo "<td>" . ($row['is_active'] ? 'Aktivan' : 'Neaktivan') . "</td>";
                                    echo "<td>";
                                    // Edit button: rely on admin.js generic edit handler
                                    $dataAttr = htmlspecialchars(json_encode(['id' => (int)$row['id'], 'username' => $row['username'], 'role' => $row['role_enum'], 'professor_id' => $row['professor_id']]), ENT_QUOTES);
                                    echo "<button class='action-button edit-button' data-entity='account' data-payload='" . $dataAttr . "'>Uredi</button> ";

                                    if ($row['is_active']) {
                                        echo "<form method='post' action='{$_SERVER['PHP_SELF']}' style='display:inline-block; margin-left:2px;'>";
                                        echo "<input type='hidden' name='action' value='delete_account'>";
                                        echo "<input type='hidden' name='id' value='" . $row['id'] . "'>";
                                        echo "<button type='button' class='action-button delete-button' onclick=\"submitDeleteForm({$row['id']}, 'delete_account', 'nalog')\">Deaktiviraj</button>";
                                        echo "</form>";
                                    } else {
                                        // account is inactive -> show activate button
                                        echo "<form method='post' style='display:inline-block; margin-left:2px;' action='{$_SERVER['PHP_SELF']}'>";
                                        echo "<input type='hidden' name='action' value='activate_account'>";
                                        echo "<input type='hidden' name='id' value='" . $row['id'] . "'>";
                                        echo " <button type='button' class='action-button activation-button' onclick=\"submitDeleteForm({$row['id']}, 'activate_account', 'nalog')\">Aktiviraj</button>";
                                        echo "</form>";
                                    }

                                    echo "</td>";
                                    echo "</tr>";
                                }
                            } catch (PDOException $e) {
                                echo "<tr><td colspan='6'>Greška pri dohvaćanju naloga: " . $e->getMessage() . "</td></tr>";
                            }
                            ?>

                        </table>

                        <?php
                        break;
                    default:
                        echo "<h2>Dobrodošli u Admin Panel</h2>";
                        echo "<p>Odaberite opciju ispod da generišete raspored časova:</p>";

                   echo "<button id='generate-schedule' class='option-button'>Generiši raspored časova</button>";
                    echo "<div id='schedule-status' style='margin-top:20px; display:none'></div>";
                    echo "<div id='schedule-container' style='margin-top:20px; display:none'></div>";

    ?>

  <script src="https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/sortablejs@latest/Sortable.min.js"></script>


        <script>
const days = ['Ponedjeljak', 'Utorak', 'Srijeda', 'Četvrtak', 'Petak'];

// Event listener za generisanje rasporeda (Java program)
document.getElementById('generate-schedule').addEventListener('click', async () => {
    const button = document.getElementById('generate-schedule');
    const statusDiv = document.getElementById('schedule-status');
    const container = document.getElementById('schedule-container');
    
    // Disable dugme i prikaži loading stanje
    button.disabled = true;
    button.textContent = 'Generiše se...';
    button.classList.add('loading');
    
    statusDiv.style.display = 'block';
    statusDiv.innerHTML = '<p style="color: #3b82f6;">Generisanje rasporeda u toku, molimo sačekajte...</p>';
    container.style.display = 'none';
    container.innerHTML = '';

    try {
        // Pozovi Java program za generisanje rasporeda
        const generateRes = await fetch('admin_panel.php?action=generateschedule');
        
        // Provera da li je odgovor validan
        if (!generateRes.ok) {
            throw new Error('HTTP greška: ' + generateRes.status + ' ' + generateRes.statusText);
        }
        
        // Provera da li je odgovor JSON
        const contentType = generateRes.headers.get('content-type');
        if (!contentType || !contentType.includes('application/json')) {
            const text = await generateRes.text();
            throw new Error('Server nije vratio JSON. Odgovor: ' + text.substring(0, 200));
        }
        
        let generateData;
        try {
            const responseText = await generateRes.text();
            if (!responseText || responseText.trim() === '') {
                throw new Error('Server je vratio prazan odgovor');
            }
            generateData = JSON.parse(responseText);
        } catch (jsonError) {
            throw new Error('Greška pri parsiranju JSON odgovora: ' + jsonError.message);
        }
        
        if (generateData.status === 'error') {
            statusDiv.innerHTML = '<p style="color: #ef4444; padding: 12px; background: rgba(239, 68, 68, 0.1); border-radius: 8px; border: 1px solid #ef4444;">Greška: ' + generateData.message + '</p>';
            button.disabled = false;
            button.textContent = 'Generiši raspored časova';
            button.classList.remove('loading');
            return;
        }
        
        // Ako je uspešno generisano, prikaži poruku o uspehu
        statusDiv.innerHTML = '<p style="color: #22c55e; padding: 12px; background: rgba(34, 197, 94, 0.1); border-radius: 8px; border: 1px solid #22c55e;">✓ ' + generateData.message + '</p>';
        
        // Sada učitaj i prikaži generisani raspored
        container.style.display = 'block';
        
        const res = await fetch('admin_panel.php?action=getschedule');
        const data = await res.json();
        if (data.error) {
            statusDiv.innerHTML += '<p style="color: #ef4444; margin-top: 10px;">Greška pri učitavanju rasporeda: ' + data.error + '</p>';
            button.disabled = false;
            button.textContent = 'Generiši raspored časova';
            button.classList.remove('loading');
            return;
        }
        // Nova struktura: data.schedules[scheduleId][semester] = events[]
        // data.schedule_ids = [id1, id2, ...]
        
        const schedules = data.schedules || {};
        const scheduleIds = data.schedule_ids || [];
        
        if (scheduleIds.length === 0) {
            container.innerHTML = '<p>Nema rasporeda u bazi.</p>';
            button.disabled = false;
            button.textContent = 'Generiši raspored časova';
            button.classList.remove('loading');
            return;
        }

        // Skupi sve događaje za vremenske slotove
        const allEvents = [];
        scheduleIds.forEach(sid => {
            Object.keys(schedules[sid] || {}).forEach(sem => {
                (schedules[sid][sem] || []).forEach(ev => allEvents.push(ev));
            });
        });

        const timeSlots = Array.from(
            new Set(allEvents.map(e => e.start + '-' + e.end))
        ).sort();

        // State: koji raspored je trenutno prikazan za svaki semestar
        const currentScheduleIndex = {};
        [1, 2, 3, 4, 5, 6].forEach(sem => {
            currentScheduleIndex[sem] = 0;
        });

        function enableTdSwap(tableEl) {
            const rows = tableEl.querySelectorAll('tbody tr');

            rows.forEach((tr) => {
                new Sortable(tr, {
                animation: 150,
                draggable: 'td',          // stavke su td [web:15]
                filter: '.no-drag',       // ne draguj vrijeme kolonu [web:15]
                preventOnFilter: true,    // spriječi drag start na filtriranim [web:15]

                swap: true,               // swap plugin (zamjena) [web:4][web:3]
                swapClass: 'td-swap-hl',  // klasa za “hover” target [web:4]

                // Samo unutar istog reda (default) — jer je svaki <tr> zaseban Sortable.
                // onEnd: (evt) => {
                //     // evt.oldIndex / evt.newIndex su indeksi td unutar tog reda [web:15]
                //     // Ovdje možeš poslati fetch da snimiš promjenu u bazi (po potrebi)
                // }
                });
            });
        }


        function buildTableForSemester(sem, events, scheduleIdx, totalSchedules) {
            const wrapper = document.createElement('div');
            wrapper.className = 'semester-wrapper';
            wrapper.id = 'semester-wrapper-' + sem;
            wrapper.style.marginBottom = '40px';
            wrapper.style.border = '1px solid #444';
            wrapper.style.borderRadius = '12px';
            wrapper.style.padding = '20px';
            wrapper.style.background = 'transparent';

            // Header sa strelicama
            const header = document.createElement('div');
            header.style.display = 'flex';
            header.style.justifyContent = 'space-between';
            header.style.alignItems = 'center';
            header.style.marginBottom = '15px';

            const leftArrow = document.createElement('button');
            leftArrow.innerHTML = '◀';
            leftArrow.className = 'nav-arrow';
            leftArrow.style.cssText = 'font-size: 24px; padding: 8px 16px; cursor: pointer; border: none; background: #3b82f6; color: white; border-radius: 8px; transition: all 0.2s;';
            leftArrow.disabled = scheduleIdx === 0;
            leftArrow.style.opacity = scheduleIdx === 0 ? '0.5' : '1';

            const h3 = document.createElement('h3');
            const semType = (sem % 2 === 1) ? 'Zimski semestar' : 'Ljetnji semestar';
            h3.style.margin = '0';
            h3.style.textAlign = 'center';
            h3.innerHTML = sem + '. semestar – ' + semType + 
                '<br><small style="color: #666; font-weight: normal;">Raspored ' + (scheduleIdx + 1) + ' od ' + totalSchedules + '</small>';

            const rightArrow = document.createElement('button');
            rightArrow.innerHTML = '▶';
            rightArrow.className = 'nav-arrow';
            rightArrow.style.cssText = 'font-size: 24px; padding: 8px 16px; cursor: pointer; border: none; background: #3b82f6; color: white; border-radius: 8px; transition: all 0.2s;';
            rightArrow.disabled = scheduleIdx === totalSchedules - 1;
            rightArrow.style.opacity = scheduleIdx === totalSchedules - 1 ? '0.5' : '1';

            // Event listeneri za strelice
            leftArrow.addEventListener('click', () => {
                if (currentScheduleIndex[sem] > 0) {
                    currentScheduleIndex[sem]--;
                    updateSemesterTable(sem);
                }
            });

            rightArrow.addEventListener('click', () => {
                if (currentScheduleIndex[sem] < scheduleIds.length - 1) {
                    currentScheduleIndex[sem]++;
                    updateSemesterTable(sem);
                }
            });

            header.appendChild(leftArrow);
            header.appendChild(h3);
            header.appendChild(rightArrow);
            wrapper.appendChild(header);

            // Tabela
            if (!events || events.length === 0) {
                const noData = document.createElement('p');
                noData.textContent = 'Nema podataka za ovaj raspored.';
                noData.style.textAlign = 'center';
                noData.style.color = '#999';
                wrapper.appendChild(noData);
            } else {
                const table = document.createElement('table');
                table.border = '1';
                table.cellPadding = '5';
                table.style.width = '100%';
                table.style.borderCollapse = 'collapse';
                table.className = 'schedule-table';
                table.setAttribute('data-semester', sem);

                const thead = document.createElement('thead');
                const trHead = document.createElement('tr');
                const thTime = document.createElement('th');
                thTime.textContent = 'Vrijeme';
                trHead.appendChild(thTime);
                days.forEach(d => {
                    const th = document.createElement('th');
                    th.textContent = d;
                    trHead.appendChild(th);
                });
                thead.appendChild(trHead);
                table.appendChild(thead);

                const tbody = document.createElement('tbody');

                timeSlots.forEach(slot => {
                    const hasAnyEvent = events.some(ev =>
                        (ev.start + '-' + ev.end) === slot
                    );
                    if (!hasAnyEvent) return;

                    const tr = document.createElement('tr');
                    const tdTime = document.createElement('td');
                    tdTime.textContent = slot;
                    tdTime.classList.add('no-drag');
                    tr.appendChild(tdTime);

                    for (let d = 1; d <= 5; d++) {
                        const td = document.createElement('td');
                        const cellEvents = events.filter(ev =>
                            ev.day === d && (ev.start + '-' + ev.end) === slot
                        );
                        if (cellEvents.length > 0) {
                            td.innerHTML = cellEvents
                                .map(ev => ev.course + ' (' + ev.room + ')')
                                .join('<br>');
                        }
                        tr.appendChild(td);
                    }

                    tbody.appendChild(tr);
                });

                table.appendChild(tbody);
                wrapper.appendChild(table);
                enableTdSwap(table);

                // PDF dugme
                const pdfBtn = document.createElement('button');
                pdfBtn.textContent = 'Sačuvaj kao PDF';
                pdfBtn.className = 'action-button add-button';
                pdfBtn.style.marginTop = '10px';
                pdfBtn.addEventListener('click', () => {
                    saveTableAsPDF(table, sem);
                });
                wrapper.appendChild(pdfBtn);
            }

            return wrapper;
        }

        function updateSemesterTable(sem) {
            const oldWrapper = document.getElementById('semester-wrapper-' + sem);
            if (!oldWrapper) return;

            const schedIdx = currentScheduleIndex[sem];
            const schedId = scheduleIds[schedIdx];
            const events = (schedules[schedId] && schedules[schedId][sem]) || [];

            const newWrapper = buildTableForSemester(sem, events, schedIdx, scheduleIds.length);
            oldWrapper.replaceWith(newWrapper);
        }

        // Inicijalni prikaz - svi semestri sa prvim rasporedom
        [1, 3, 5, 2, 4, 6].forEach(sem => {
            const schedId = scheduleIds[0];
            const events = (schedules[schedId] && schedules[schedId][sem]) || [];
            const wrapper = buildTableForSemester(sem, events, 0, scheduleIds.length);
            container.appendChild(wrapper);
        });

        // Dugme za PDF svih
        const pdfAllBtn = document.createElement('button');
        pdfAllBtn.textContent = 'Sačuvaj kompletan raspored kao PDF';
        pdfAllBtn.className = 'action-button add-button';
        pdfAllBtn.style.marginTop = '20px';
        pdfAllBtn.addEventListener('click', saveFullScheduleAsPDF);
        container.appendChild(pdfAllBtn);
        
        // Vrati dugme u normalno stanje
        button.disabled = false;
        button.textContent = 'Generiši raspored časova';
        button.classList.remove('loading');

    } catch (e) {
        statusDiv.innerHTML = '<p style="color: #ef4444; padding: 12px; background: rgba(239, 68, 68, 0.1); border-radius: 8px; border: 1px solid #ef4444;">Greška pri generisanju rasporeda: ' + e.message + '</p>';
        button.disabled = false;
        button.textContent = 'Generiši raspored časova';
        button.classList.remove('loading');
    }
});
</script>
                        <script>
                            function saveFullScheduleAsPDF() {
                                const { jsPDF } = window.jspdf;
                                const doc = new jsPDF('landscape', 'pt', 'a4');

                                let y = 40;

                                doc.setFontSize(18);
                                doc.text('Kompletan raspored časova', 40, y);
                                y += 30;

                                const tables = document.querySelectorAll('.schedule-table');

                                tables.forEach((table, index) => {
                                    if (index > 0) {
                                        doc.addPage();
                                        y = 40;
                                    }

                                    // Naslov semestra (uzimamo h3 iznad tabele)
                                    const title = table.previousSibling?.textContent || `Semestar ${index + 1}`;
                                    doc.setFontSize(14);
                                    doc.text(title, 40, y);
                                    y += 20;

                                    const headers = [];
                                    const rows = [];

                                    table.querySelectorAll('thead th').forEach(th => {
                                        headers.push(th.innerText);
                                    });

                                    table.querySelectorAll('tbody tr').forEach(tr => {
                                        const row = [];
                                        tr.querySelectorAll('td').forEach(td => {
                                            row.push(td.innerText);
                                        });
                                        rows.push(row);
                                    });

                                    doc.autoTable({
                                        head: [headers],
                                        body: rows,
                                        startY: y,
                                        styles: {
                                            fontSize: 9,
                                            cellPadding: 4
                                        },
                                        headStyles: {
                                            fillColor: [15, 23, 42] // tamna (kao tvoj UI)
                                        }
                                    });
                                });

                                doc.save('kompletan_raspored_casova.pdf');
                            }
                        </script>
                        <script src="https://cdnjs.cloudflare.com/ajax/libs/jspdf-autotable/3.5.25/jspdf.plugin.autotable.min.js"></script>


                        <script>
                        function saveTableAsPDF(table, semester) {
                            const { jsPDF } = window.jspdf;
                            const doc = new jsPDF('landscape', 'pt', 'a4');

                            doc.setFontSize(16);
                            doc.text(`Raspored časova – ${semester}. semestar`, 40, 40);
                            let startY = 70;

                            const rows = [];
                            const headers = [];

                            // headeri
                            table.querySelectorAll('thead th').forEach(th => {
                                headers.push(th.innerText);
                            });

                            // redovi
                            table.querySelectorAll('tbody tr').forEach(tr => {
                                const row = [];
                                tr.querySelectorAll('td').forEach(td => {
                                    row.push(td.innerText);
                                });
                                rows.push(row);
                            });

                            doc.autoTable({
                                head: [headers],
                                body: rows,
                                startY: startY,
                                styles: {
                                    fontSize: 9,
                                    cellPadding: 4
                                },
                                headStyles: {
                                    fillColor: [22, 101, 52] // tamno zelena
                                }
                            });

                            doc.save(`raspored_semestar_${semester}.pdf`);
                        }
                    </script>



                        <?php
    break;

      }
          ?>
</main>

<footer>
    <p>© <?php echo date('Y'); ?> Raspored Ispita | Admin Panel</p>
</footer>

</body>
</html>
