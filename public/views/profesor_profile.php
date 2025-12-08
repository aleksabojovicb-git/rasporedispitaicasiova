<?php
session_start();
require_once __DIR__ . '/../../config/dbconnection.php';

if (isset($_GET['logout'])) {
    session_unset();
    session_destroy();
    header('Location: ./authorization.php');
    exit;
}

if (!isset($_SESSION['user_id']) || !isset($_SESSION['professor_id'])) {
    header('Location: ./authorization.php');
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
            <form id="examWeekForm" method="post">
                <input type="hidden" name="action" value="save_exam_weeks">
                <table class="table table-bordered">
                    <thead><tr><th>Predmet</th><th>Semestar</th><th>Uloga</th><th>Nedjelja kolokvijuma</th></tr></thead>
                    <tbody>
                    <?php
                    try {
                        $stmt = $pdo->prepare("
                SELECT c.id, c.name, c.semester, cp.is_assistant
                FROM course_professor cp
                JOIN course c ON c.id = cp.course_id
                WHERE cp.professor_id = ?
                ORDER BY c.name
            ");
                        $stmt->execute([$professorId]);
                        $courses = $stmt->fetchAll(PDO::FETCH_ASSOC);
                        if (!$courses) echo "<tr><td colspan='4'>Nema pridruženih predmeta.</td></tr>";
                        else {
                            foreach($courses as $c){
                                $role = $c['is_assistant'] ? 'Asistent' : 'Profesor';
                                echo "<tr>";
                                echo "<td>".htmlspecialchars($c['name'])."</td>";
                                echo "<td>".htmlspecialchars($c['semester'])."</td>";
                                echo "<td>".$role."</td>";
                                echo "<td><select name='exam_week[".(int)$c['id']."]'>";
                                echo "<option value=''>-- Izaberi --</option>";
                                for($w=5;$w<=13;$w++) echo "<option value='$w'>$w</option>";
                                echo "</select></td>";
                                echo "</tr>";
                            }
                        }
                    } catch(PDOException $e) {
                        echo "<tr><td colspan='4'>Greška: ".htmlspecialchars($e->getMessage())."</td></tr>";
                    }
                    ?>
                    </tbody>
                </table>
            </form>
        </div>

        <!-- AVAILABILITY -->
        <div class="tab-pane fade" id="availabilityTab">
            <h3>Raspoloživost</h3>
            <!-- Tvoj JS grid kod ide ovdje -->
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
<script>
    // Toggle edit profil
    const toggleEditBtn = document.getElementById('toggleEditBtn');
    const profileEdit = document.getElementById('profileEdit');
    const profileView = document.getElementById('profileView');

    toggleEditBtn.addEventListener('click', () => {
        const hidden = profileEdit.classList.contains('d-none');
        if(hidden){
            profileEdit.classList.remove('d-none');
            profileView.classList.add('d-none');
            toggleEditBtn.textContent = 'Zatvori edit';
        } else {
            profileEdit.classList.add('d-none');
            profileView.classList.remove('d-none');
            toggleEditBtn.textContent = 'Edit profil';
        }
    });

    // Password modal
    const modalBtn = document.getElementById('openModalBtn');
    if(modalBtn){
        modalBtn.addEventListener('click', ()=> new bootstrap.Modal(document.getElementById('passwordModal')).show());
    }
</script>
</body>
</html>
