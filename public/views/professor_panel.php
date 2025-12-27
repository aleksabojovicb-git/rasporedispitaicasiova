<?php
session_start();
require_once __DIR__ . '/../../config/dbconnection.php';

if (isset($_GET['logout'])) {
    session_unset();
    session_destroy();
    header('Location: ./authorization.php');
    exit;
}

// USER mora biti ulogovan
if (!isset($_SESSION['user_id'])) {
    header('Location: authorization.php');
    exit;
}

// PROFESOR mora imati professor_id (ADMIN ne ide ovdje)
if (!isset($_SESSION['professor_id'])) {
    header('Location: authorization.php');
    exit;
}


$successMessage = null;
$errorMessage = null;

$professorId = (int) $_SESSION['professor_id'];

$stmt = $pdo->prepare("
    SELECT p.id, p.full_name, p.email, u.username
    FROM professor p
    LEFT JOIN user_account u ON u.professor_id = p.id
    WHERE p.id = ? AND p.is_active = TRUE
");
$stmt->execute([$professorId]);
$currentProfessor = $stmt->fetch(PDO::FETCH_ASSOC);

if (!$currentProfessor) {
    session_unset();
    session_destroy();
    header('Location: ./authorization.php');
    exit;
}

function buildUsernameFromFullName(string $fullName): string
{
    $fullName = strtolower(trim(preg_replace('/\s+/', ' ', $fullName)));
    if ($fullName === '') return '';

    $parts = explode(' ', $fullName);
    $first = $parts[0];
    $last = $parts[count($parts) - 1];
    if ($last === '') $last = $first;

    return $first . '.' . $last;
}

// 1) Obrada JSON POST zahtjeva (AJAX)
$inputJSON = file_get_contents('php://input');
$inputData = json_decode($inputJSON, true);

if ($inputData && isset($inputData['action']) && $inputData['action'] === 'save_availability') {
    header('Content-Type: application/json');
    
    $availability = $inputData['data'] ?? [];
    
    // Validacija
    if (!is_array($availability) || count($availability) === 0) {
        echo json_encode(['success' => false, 'error' => 'Morate izabrati bar jedan termin']);
        exit;
    }

    try {
        $pdo->beginTransaction();

        // Brisanje starih termina
        $stmt = $pdo->prepare("DELETE FROM professor_availability WHERE professor_id = ?");
        $stmt->execute([$professorId]);

        // Dodavanje novih
        $stmt = $pdo->prepare("
            INSERT INTO professor_availability (professor_id, weekday, start_time, end_time) 
            VALUES (?, ?, ?, ?)
        ");

        $inserted = 0;

        foreach ($availability as $slot) {
            $day = (int)($slot['day'] ?? 0);
            if ($day < 1 || $day > 5) continue;

            $stmt->execute([$professorId, $day, $slot['from'], $slot['to']]);
            $inserted++;
        }

        $pdo->commit();
        echo json_encode(['success' => true, 'count' => $inserted]);
    } catch (PDOException $e) {
        $pdo->rollBack();
        echo json_encode(['success' => false, 'error' => $e->getMessage()]);
    }
    exit; // Prekida dalje izvršavanje jer je ovo AJAX response
}

// 2) Obrada JSON POST zahtjeva za čuvanje sedmica kolokvijuma
if ($inputData && isset($inputData['action']) && $inputData['action'] === 'save_colloquium_weeks') {
    header('Content-Type: application/json');
    
    $colloquiumData = $inputData['data'] ?? [];
    
    if (!is_array($colloquiumData) || count($colloquiumData) === 0) {
        echo json_encode(['success' => false, 'error' => 'Nema podataka za čuvanje']);
        exit;
    }

    try {
        $pdo->beginTransaction();

        $stmt = $pdo->prepare("
            UPDATE course 
            SET colloquium_1_week = ?, colloquium_2_week = ? 
            WHERE id = ?
        ");

        $updated = 0;

        foreach ($colloquiumData as $item) {
            $courseId = (int)($item['course_id'] ?? 0);
            $col1Week = $item['colloquium_1_week'];
            $col2Week = $item['colloquium_2_week'];
            
            if ($courseId === 0) continue;
            
            // Convert to null or int (1 means "ne odrzava se")
            $col1Value = ($col1Week === '' || $col1Week === null) ? null : (int)$col1Week;
            $col2Value = ($col2Week === '' || $col2Week === null) ? null : (int)$col2Week;
            
            $stmt->execute([$col1Value, $col2Value, $courseId]);
            $updated++;
        }

        $pdo->commit();
        echo json_encode(['success' => true, 'count' => $updated]);
    } catch (PDOException $e) {
        $pdo->rollBack();
        echo json_encode(['success' => false, 'error' => $e->getMessage()]);
    }
    exit;
}

// 2) Učitavanje postojećih termina za prikaz
$stmt = $pdo->prepare("
    SELECT weekday, start_time, end_time
    FROM professor_availability
    WHERE professor_id = ?
    ORDER BY weekday, start_time
");
$stmt->execute([$professorId]);
$existingAvailability = $stmt->fetchAll(PDO::FETCH_ASSOC);

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';

    if ($action === 'update_profile') {
        $fullName = trim($_POST['full_name'] ?? '');
        if ($fullName === '') {
            $errorMessage = "Ime i prezime je obavezno.";
        } else {
            $username = buildUsernameFromFullName($fullName);
            if ($username === '') {
                $errorMessage = "Ime i prezime nisu validni.";
            } else {
                try {
                    $pdo->beginTransaction();
                    $stmt = $pdo->prepare("UPDATE professor SET full_name = ? WHERE id = ?");
                    $stmt->execute([$fullName, $professorId]);

                    $stmt = $pdo->prepare("UPDATE user_account SET username = ? WHERE professor_id = ?");
                    $stmt->execute([$username, $professorId]);

                    $pdo->commit();

                    $currentProfessor['full_name'] = $fullName;
                    $currentProfessor['username'] = $username;
                    $_SESSION['professor_name'] = $fullName;
                    $successMessage = "Podaci su uspješno ažurirani.";
                } catch (PDOException $e) {
                    $pdo->rollBack();
                    $errorMessage = "Greška pri čuvanju podataka: " . $e->getMessage();
                }
            }
        }
    } elseif ($action === 'update_password') {
        $oldPassword = $_POST['old_password'] ?? '';
        $newPassword = $_POST['new_password'] ?? '';
        $confirmPassword = $_POST['confirm_password'] ?? '';

        if ($oldPassword === '' || $newPassword === '' || $confirmPassword === '') {
            $errorMessage = "Sva polja su obavezna.";
        } elseif ($newPassword !== $confirmPassword) {
            $errorMessage = "Novi password i potvrda se ne poklapaju.";
        } elseif (strlen($newPassword) < 8) {
            $errorMessage = "Novi password mora imati najmanje 8 karaktera.";
        } else {
            $stmt = $pdo->prepare("SELECT password_hash FROM user_account WHERE professor_id = ?");
            $stmt->execute([$professorId]);
            $account = $stmt->fetch(PDO::FETCH_ASSOC);

            if (!$account || !password_verify($oldPassword, $account['password_hash'])) {
                $errorMessage = "Stari password nije tačan.";
            } else {
                $newHash = password_hash($newPassword, PASSWORD_DEFAULT);
                try {
                    $stmt = $pdo->prepare("UPDATE user_account SET password_hash = ? WHERE professor_id = ?");
                    $stmt->execute([$newHash, $professorId]);
                    $successMessage = "Password je uspješno promijenjen.";
                } catch (PDOException $e) {
                    $errorMessage = "Greška pri promjeni passworda: " . $e->getMessage();
                }
            }
        }
    }
}
?>
<!DOCTYPE html>
<html lang="sr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Profil Profesora</title>

    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css">
    <link rel="stylesheet" href="../assets/css/admin.css" />
    <link rel="stylesheet" href="../assets/css/base.css" />
    <link rel="stylesheet" href="../assets/css/fields.css" />
    <link rel="stylesheet" href="../assets/css/colors.css" />
    <link rel="stylesheet" href="../assets/css/stacks.css" />
    <link rel="stylesheet" href="../assets/css/tabs.css" />
    <link rel="stylesheet" href="../assets/css/table.css" />
    <link rel="stylesheet" href="../assets/css/profesor_profile.css">
    <link href="https://cdn.jsdelivr.net/npm/fullcalendar@6.1.11/index.global.min.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/fullcalendar@6.1.11/index.global.min.js"></script>

</head>
<body>
<?php require __DIR__ . '/partials/header.php'; ?>

<div class="container mt-4">
    <h2>Profil Profesora</h2>

    <?php if ($errorMessage): ?>
        <div class="alert alert-danger"><?php echo htmlspecialchars($errorMessage); ?></div>
    <?php endif; ?>
    <?php if ($successMessage): ?>
        <div class="alert alert-success"><?php echo htmlspecialchars($successMessage); ?></div>
    <?php endif; ?>

    <ul class="nav nav-tabs" id="profTab" role="tablist">
        <li class="nav-item" role="presentation">
            <button class="nav-link active" id="tab-profile" data-bs-toggle="tab" data-bs-target="#profileTab" type="button">Profil</button>
        </li>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="tab-events" data-bs-toggle="tab" data-bs-target="#eventsTab" type="button">Događaji</button>
        </li>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="tab-courses" data-bs-toggle="tab" data-bs-target="#coursesTab" type="button">Predmeti & Kolokvijumi</button>
        </li>
        <li class="nav-item" role="presentation">
            <button class="nav-link" id="tab-avail" data-bs-toggle="tab" data-bs-target="#availabilityTab" type="button">Raspoloživost</button>
        </li>
    </ul>

    <div class="tab-content mt-3">
        <!-- PROFILE -->
        <div class="tab-pane fade show active" id="profileTab">
            <div class="edit-controls mb-3">
                <button id="toggleEditBtn" class="btn btn-secondary">Edit profil</button>
            </div>

            <div id="profileView">
                <p><strong>Ime i prezime:</strong> <?php echo htmlspecialchars($currentProfessor['full_name']); ?></p>
                <p><strong>Email:</strong> <?php echo htmlspecialchars($currentProfessor['email']); ?></p>
                <p><strong>Username:</strong> <?php echo htmlspecialchars($currentProfessor['username'] ?? ''); ?></p>
            </div>

            <div id="profileEdit" class="d-none">
                <form method="post">
                    <input type="hidden" name="action" value="update_profile">
                    <div class="mb-3">
                        <label for="full_name" class="form-label">Ime i prezime:</label>
                        <input type="text" id="full_name" name="full_name" class="form-control" value="<?php echo htmlspecialchars($currentProfessor['full_name']); ?>" required>
                    </div>
                    <div class="mb-3">
                        <label for="email" class="form-label">Email:</label>
                        <input type="email" id="email" name="email" class="form-control" value="<?php echo htmlspecialchars($currentProfessor['email']); ?>" readonly>
                    </div>
                    <button type="submit" class="btn btn-primary">Sačuvaj izmjene</button>
                </form>
                <button id="openModalBtn" class="btn btn-warning mt-2">Promijeni password</button>
                <a href="forgot_password.php" class="btn btn-link mt-2">Zaboravili ste password?</a>
            </div>
        </div>

        <!-- EVENTS -->
        <div class="tab-pane fade" id="eventsTab">
            <h3>Sva predavanja i kolokvijumi</h3>
            <table class="table table-bordered">
                <thead>
                <tr><th>ID</th><th>Predmet</th><th>Tip</th><th>Početak</th><th>Kraj</th><th>Sala</th><th>Napomena</th></tr>
                </thead>
                <tbody>
                <?php
                try {
                    $stmt = $pdo->prepare("
                SELECT e.*, c.name as course_name, r.code as room_code
                FROM academic_event e
                LEFT JOIN course c ON e.course_id = c.id
                LEFT JOIN room r ON e.room_id = r.id
                JOIN event_professor ep ON ep.event_id = e.id
                WHERE ep.professor_id = ?
                ORDER BY e.starts_at DESC
            ");
                    $stmt->execute([$professorId]);
                    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
                        $event_type = ($row['type_enum'] === 'EXAM') ? 'Ispit' : (($row['type_enum']==='COLLOQUIUM')?'Kolokvijum':htmlspecialchars($row['type_enum']));
                        echo "<tr>";
                        echo "<td>".htmlspecialchars($row['id'])."</td>";
                        echo "<td>".htmlspecialchars($row['course_name'])."</td>";
                        echo "<td>".$event_type."</td>";
                        echo "<td>".date('d.m.Y H:i', strtotime($row['starts_at']))."</td>";
                        echo "<td>".date('d.m.Y H:i', strtotime($row['ends_at']))."</td>";
                        echo "<td>".($row['is_online']?'Online':htmlspecialchars($row['room_code']))."</td>";
                        echo "<td>".htmlspecialchars($row['notes'])."</td>";
                        echo "</tr>";
                    }
                } catch(PDOException $e) {
                    echo "<tr><td colspan='7'>Greška: ".htmlspecialchars($e->getMessage())."</td></tr>";
                }
                ?>
                </tbody>
            </table>
        </div>

        <!-- COURSES -->
        <div class="tab-pane fade" id="coursesTab">
            <h3>Moji predmeti i izbor nedjelje kolokvijuma</h3>
            <div id="colloquiumMessage" class="alert d-none mb-3"></div>
            <div class="table-responsive">
                <table class="table table-bordered table-hover">
                    <thead class="table-light">
                        <tr>
                            <th>Predmet</th>
                            <th>Semestar</th>
                            <th>Uloga</th>
                            <th>Kolokvijum 1 (sedmica)</th>
                            <th>Kolokvijum 2 (sedmica)</th>
                        </tr>
                    </thead>
                    <tbody id="coursesTableBody">
                    <?php
                    try {
                        $stmt = $pdo->prepare("
                            SELECT c.id, c.name, c.semester, cp.is_assistant, 
                                   c.colloquium_1_week, c.colloquium_2_week
                            FROM course_professor cp
                            JOIN course c ON c.id = cp.course_id
                            WHERE cp.professor_id = ?
                            ORDER BY c.name
                        ");
                        $stmt->execute([$professorId]);
                        $courses = $stmt->fetchAll(PDO::FETCH_ASSOC);
                        
                        if (!$courses) {
                            echo "<tr><td colspan='5'>Nema pridruženih predmeta.</td></tr>";
                        } else {
                            foreach($courses as $c) {
                                $role = $c['is_assistant'] ? 'Asistent' : 'Profesor';
                                $col1Val = $c['colloquium_1_week'];
                                $col2Val = $c['colloquium_2_week'];
                                ?>
                                <tr data-course-id="<?php echo (int)$c['id']; ?>">
                                    <td><?php echo htmlspecialchars($c['name']); ?></td>
                                    <td><?php echo htmlspecialchars($c['semester']); ?></td>
                                    <td><?php echo $role; ?></td>
                                    <td>
                                        <select class="form-select form-select-sm colloquium-1-select" data-course-id="<?php echo (int)$c['id']; ?>">
                                            <option value="">-- Izaberi --</option>
                                            <option value="1" <?php echo ($col1Val == 1) ? 'selected' : ''; ?>>Ne održava se</option>
                                            <?php for($w=5; $w<=13; $w++): ?>
                                                <option value="<?php echo $w; ?>" <?php echo ($col1Val == $w) ? 'selected' : ''; ?>><?php echo $w; ?>. sedmica</option>
                                            <?php endfor; ?>
                                        </select>
                                    </td>
                                    <td>
                                        <select class="form-select form-select-sm colloquium-2-select" data-course-id="<?php echo (int)$c['id']; ?>">
                                            <option value="">-- Izaberi --</option>
                                            <option value="1" <?php echo ($col2Val == 1) ? 'selected' : ''; ?>>Ne održava se</option>
                                            <?php for($w=5; $w<=13; $w++): ?>
                                                <option value="<?php echo $w; ?>" <?php echo ($col2Val == $w) ? 'selected' : ''; ?>><?php echo $w; ?>. sedmica</option>
                                            <?php endfor; ?>
                                        </select>
                                    </td>
                                </tr>
                                <?php
                            }
                        }
                    } catch(PDOException $e) {
                        echo "<tr><td colspan='5'>Greška: ".htmlspecialchars($e->getMessage())."</td></tr>";
                    }
                    ?>
                    </tbody>
                </table>
            </div>
            <?php if (!empty($courses)): ?>
            <button type="button" id="saveColloquiumWeeksBtn" class="btn btn-primary">
                <span class="btn-text">Sačuvaj sedmice kolokvijuma</span>
                <span class="spinner-border spinner-border-sm d-none" role="status"></span>
            </button>
            <?php endif; ?>
        </div>

        <!-- AVAILABILITY -->
        <div class="tab-pane fade" id="availabilityTab">
            <h3>Raspoloživost</h3>

            <!-- VIEW MODE -->
            <div id="availability-view-mode">
                <?php if (count($existingAvailability) > 0): ?>
                    <h5 class="mt-3">Vaši trenutni termini:</h5>
                    <div class="table-responsive mt-2">
                        <table class="table table-bordered table-hover" style="background-color: white;">
                            <thead class="table-light">
                                <tr>
                                    <th style="width: 50%;">Dan</th>
                                    <th style="width: 50%;">Vrijeme</th>
                                </tr>
                            </thead>
                            <tbody>
                                <?php 
                                $daysMap = [1=>'Ponedeljak', 2=>'Utorak', 3=>'Srijeda', 4=>'Četvrtak', 5=>'Petak'];
                                foreach ($existingAvailability as $slot): 
                                    $dName = $daysMap[$slot['weekday']] ?? 'Nepoznato';
                                    $tFrom = date('H:i', strtotime($slot['start_time']));
                                    $tTo   = date('H:i', strtotime($slot['end_time']));
                                ?>
                                <tr>
                                    <td class="fw-bold text-secondary"><?php echo $dName; ?></td>
                                    <td><?php echo $tFrom . ' – ' . $tTo; ?></td>
                                </tr>
                                <?php endforeach; ?>
                            </tbody>
                        </table>
                    </div>
                <?php else: ?>
                    <div class="alert alert-info mt-3">Niste još definisali termine raspoloživosti.</div>
                <?php endif; ?>

                <button id="btn-enable-edit" class="btn btn-primary mt-3">
                    <?php echo (count($existingAvailability) > 0) ? 'Ažuriraj raspoloživost' : 'Dodaj raspoloživost'; ?>
                </button>
            </div>

            <!-- EDIT MODE -->
            <div id="availability-edit-mode" class="d-none">
                <div class="alert alert-warning mt-2">
                    <small>Kliknite i prevucite mišem preko kalendara da označite termine. Kliknite na termin da ga obrišete.</small>
                </div>
                
                <div id="availability-calendar" style="margin-top:10px;"></div>

                <h5 class="mt-4">Novi termini za čuvanje:</h5>
                <ul id="availability-output"></ul>

                <div class="mt-3">
                    <button id="save-availability" class="btn btn-success me-2">Sačuvaj raspoloživost</button>
                    <button id="cancel-edit" class="btn btn-secondary">Odustani</button>
                </div>
            </div>
        </div>
    </div>
</div>

<!-- Password modal -->
<div class="modal fade" id="passwordModal" tabindex="-1">
    <div class="modal-dialog">
        <div class="modal-content">
            <form method="post">
                <input type="hidden" name="action" value="update_password">
                <div class="modal-header"><h5 class="modal-title">Promjena passworda</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                </div>
                <div class="modal-body">
                    <div class="mb-3"><label for="oldPassword" class="form-label">Stari password</label>
                        <input type="password" class="form-control" id="oldPassword" name="old_password" required></div>

                    <div class="mb-3"><label for="newPassword" class="form-label">Novi password</label>
                        <input type="password" class="form-control" id="newPassword" name="new_password" required></div>

                    <div class="mb-3"><label for="confirmPassword" class="form-label">Potvrdi novi password</label>
                        <input type="password" class="form-control" id="confirmPassword" name="confirm_password" required></div>
                </div>
                <div class="modal-footer">
                    <button type="submit" class="btn btn-primary">Sačuvaj</button>
                </div>
            </form>
        </div>
    </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
<!-- Učitavamo postojeće podatke u GLOBALNU JS varijablu kako bi ih skripta mogla koristiti -->
<script>
    window.SERVER_EXISTING_AVAILABILITY = <?php echo json_encode($existingAvailability); ?>;
</script>
<script>
// Colloquium weeks save functionality
document.addEventListener('DOMContentLoaded', function() {
    const saveBtn = document.getElementById('saveColloquiumWeeksBtn');
    if (!saveBtn) return;
    
    const messageDiv = document.getElementById('colloquiumMessage');
    
    function showMessage(type, text) {
        messageDiv.className = 'alert alert-' + type + ' mb-3';
        messageDiv.textContent = text;
        messageDiv.classList.remove('d-none');
        setTimeout(() => messageDiv.classList.add('d-none'), 5000);
    }
    
    function showLoading(show) {
        const textSpan = saveBtn.querySelector('.btn-text');
        const spinner = saveBtn.querySelector('.spinner-border');
        if (show) {
            textSpan.classList.add('d-none');
            spinner.classList.remove('d-none');
            saveBtn.disabled = true;
        } else {
            textSpan.classList.remove('d-none');
            spinner.classList.add('d-none');
            saveBtn.disabled = false;
        }
    }
    
    saveBtn.addEventListener('click', function() {
        const rows = document.querySelectorAll('#coursesTableBody tr[data-course-id]');
        const data = [];
        let hasValidationError = false;
        
        rows.forEach(row => {
            const courseId = row.getAttribute('data-course-id');
            const col1Select = row.querySelector('.colloquium-1-select');
            const col2Select = row.querySelector('.colloquium-2-select');
            
            const col1Value = col1Select.value;
            const col2Value = col2Select.value;
            
            // Validacija: ako su oba odabrana i nisu "ne odrzava se", col2 > col1
            if (col1Value && col2Value && col1Value !== '1' && col2Value !== '1') {
                if (parseInt(col2Value) <= parseInt(col1Value)) {
                    hasValidationError = true;
                    row.classList.add('table-danger');
                    showMessage('danger', 'Greška: Kolokvijum 2 mora biti nakon Kolokvijuma 1');
                } else {
                    row.classList.remove('table-danger');
                }
            } else {
                row.classList.remove('table-danger');
            }
            
            data.push({
                course_id: courseId,
                colloquium_1_week: col1Value || null,
                colloquium_2_week: col2Value || null
            });
        });
        
        if (hasValidationError) return;
        
        showLoading(true);
        
        fetch(window.location.href, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                action: 'save_colloquium_weeks',
                data: data
            })
        })
        .then(response => response.json())
        .then(result => {
            showLoading(false);
            if (result.success) {
                showMessage('success', 'Sedmice kolokvijuma su uspješno sačuvane!');
            } else {
                showMessage('danger', 'Greška: ' + (result.error || 'Nepoznata greška'));
            }
        })
        .catch(error => {
            showLoading(false);
            showMessage('danger', 'Greška pri slanju zahtjeva');
            console.error(error);
        });
    });
});
</script>
<script src="../assets/js/professor_tabs.js"></script>
</body>
</html>