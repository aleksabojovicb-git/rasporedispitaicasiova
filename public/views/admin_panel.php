<?php
session_start();
require_once __DIR__ . '/../../config/dbconnection.php';

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
                    default:
                        echo "<h2>Dobrodošli u Admin Panel</h2>";
                        echo "<p>Odaberite opciju ispod da generišete raspored časova:</p>";

                        echo "<button id='generate-schedule' class='option-button'>Generiši raspored časova</button>";

                        echo "<div id='schedule-container' style='margin-top:20px; display:none;'>";
                        echo "<div style='text-align:right; margin-bottom:10px;>
           <div> <label for='year-select'>Godina:</label>
            <select id='year-select'>
                <option value='1'>1. godina</option>
                <option value='2'>2. godina</option>
                <option value='3'>3. godina</option>
                <option value='4'>4. godina</option>
            </select></div>
            <button id='save-pdf'>Save as PDF</button>
          </div>";

                        echo "<table id='schedule-table' border='1' cellpadding='5' style='width:100%; border-collapse: collapse; text-align:center;'>
            <thead>
                <tr>
                    <th>Vreme</th>
                    <th>Ponedjeljak</th>
                    <th>Utorak</th>
                    <th>Sreda</th>
                    <th>Četvrtak</th>
                    <th>Petak</th>
                </tr>
            </thead>
            <tbody>
            </tbody>
          </table>";
                        echo "</div>";
                        ?>

                        <script src="https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js"></script>
                        <script>
                            const times = [];
                            const start = new Date('1970-01-01T09:15');
                            const end = new Date('1970-01-01T21:15');
                            let current = new Date(start);
                            while (current <= end) {
                                times.push(current.toTimeString().slice(0,5));
                                current.setHours(current.getHours()+1);
                            }

                            const days = ['pon', 'uto', 'sre', 'cet', 'pet'];

                            document.getElementById('generate-schedule').addEventListener('click', () => {
                                const container = document.getElementById('schedule-container');
                                const tbody = document.querySelector('#schedule-table tbody');
                                tbody.innerHTML = '';

                                const year = document.getElementById('year-select').value;

                                times.forEach(time => {
                                    const tr = document.createElement('tr');

                                    const tdTime = document.createElement('td');
                                    tdTime.textContent = time;
                                    tr.appendChild(tdTime);

                                    days.forEach(day => {
                                        const td = document.createElement('td');
                                        td.id = `${time}-${day}-${year}`;
                                        td.className = 'schedule-cell';
                                        tr.appendChild(td);
                                    });

                                    tbody.appendChild(tr);
                                });

                                container.style.display = 'block';
                            });

                            document.getElementById('save-pdf').addEventListener('click', () => {
                                const { jsPDF } = window.jspdf;
                                const doc = new jsPDF();
                                doc.text("Raspored časova", 10, 10);

                                const table = document.getElementById('schedule-table');
                                doc.autoTable({ html: table, startY: 20 });
                                doc.save('raspored.pdf');
                            });
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
