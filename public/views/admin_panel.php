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

/** ===== PARTIALS ===== */
$pageTitle = "Profil Profesora";
$pageCss = null; // page-specific CSS nije potreban
include __DIR__ . "/partials/head.php";
include __DIR__ . "/partials/admin_header.php";
?>

<div class="container">
    <h2 class="section-title">Profil Profesora</h2>

    <?php if ($errorMessage): ?>
        <div class="error"><?= htmlspecialchars($errorMessage); ?></div>
    <?php endif; ?>

    <?php if ($successMessage): ?>
        <div class="success"><?= htmlspecialchars($successMessage); ?></div>
    <?php endif; ?>

    <!-- ==== TABS ==== -->
    <div class="tabs" data-active="profile">
        <div class="tab-list">
            <button class="tab-button active" data-target="profileTab">Profil</button>
            <button class="tab-button" data-target="eventsTab">Događaji</button>
            <button class="tab-button" data-target="coursesTab">Predmeti & Kolokvijumi</button>
            <button class="tab-button" data-target="availabilityTab">Raspoloživost</button>
        </div>

        <div class="tab-content active" id="profileTab">
            <div class="edit-controls">
                <button id="toggleEditBtn" class="button button-secondary">Edit profil</button>
            </div>

            <div id="profileView">
                <p><strong>Ime i prezime:</strong> <?= htmlspecialchars($currentProfessor['full_name']); ?></p>
                <p><strong>Email:</strong> <?= htmlspecialchars($currentProfessor['email']); ?></p>
                <p><strong>Username:</strong> <?= htmlspecialchars($currentProfessor['username'] ?? ''); ?></p>
            </div>

            <div id="profileEdit" class="hidden">
                <form method="post" class="form-container">
                    <input type="hidden" name="action" value="update_profile">

                    <label for="full_name">Ime i prezime:</label>
                    <input type="text" id="full_name" name="full_name" value="<?= htmlspecialchars($currentProfessor['full_name']); ?>" required>

                    <label for="email">Email:</label>
                    <input type="email" id="email" name="email" value="<?= htmlspecialchars($currentProfessor['email']); ?>" readonly>

                    <button type="submit" class="button button-primary">Sačuvaj</button>
                </form>

                <button id="openModalBtn" class="button button-secondary" style="margin-top: 12px;">Promijeni password</button>
            </div>
        </div>

        <!-- EVENTS -->
        <div class="tab-content" id="eventsTab">
            <h3 class="section-title">Sva predavanja i kolokvijumi</h3>

            <table class="table">
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
                        $event_type = ($row['type_enum'] === 'EXAM') ? 'Ispit' :
                            (($row['type_enum'] === 'COLLOQUIUM') ? 'Kolokvijum' : htmlspecialchars($row['type_enum']));
                        echo "<tr>";
                        echo "<td>".htmlspecialchars($row['id'])."</td>";
                        echo "<td>".htmlspecialchars($row['course_name'])."</td>";
                        echo "<td>".$event_type."</td>";
                        echo "<td>".date('d.m.Y H:i', strtotime($row['starts_at']))."</td>";
                        echo "<td>".date('d.m.Y H:i', strtotime($row['ends_at']))."</td>";
                        echo "<td>".($row['is_online'] ? 'Online' : htmlspecialchars($row['room_code']))."</td>";
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
        <div class="tab-content" id="coursesTab">
            <h3 class="section-title">Moji predmeti i izbor nedjelje kolokvijuma</h3>

            <form method="post">
                <input type="hidden" name="action" value="save_exam_weeks">

                <table class="table">
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

                        if (!$courses) {
                            echo "<tr><td colspan='4'>Nema pridruženih predmeta.</td></tr>";
                        } else {
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
        <div class="tab-content" id="availabilityTab">
            <h3 class="section-title">Raspoloživost</h3>
            <!-- još nedefinisano -->
        </div>
    </div>
</div>

<?php include __DIR__ . "/partials/footer.php"; ?>

<script>
    /** SIMPLE TAB UI (NO BOOTSTRAP) **/
    const tabButtons = document.querySelectorAll('.tab-button');
    const tabContents = document.querySelectorAll('.tab-content');

    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            tabButtons.forEach(b => b.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));

            btn.classList.add('active');
            const target = document.getElementById(btn.dataset.target);
            target.classList.add('active');
        });
    });

    /** EDIT PROFILE TOGGLE **/
    const toggleEditBtn = document.getElementById('toggleEditBtn');
    const profileEdit = document.getElementById('profileEdit');
    const profileView = document.getElementById('profileView');

    toggleEditBtn.addEventListener('click', () => {
        const isHidden = profileEdit.classList.contains('hidden');
        if(isHidden){
            profileEdit.classList.remove('hidden');
            profileView.classList.add('hidden');
            toggleEditBtn.textContent = 'Zatvori edit';
        } else {
            profileEdit.classList.add('hidden');
            profileView.classList.remove('hidden');
            toggleEditBtn.textContent = 'Edit profil';
        }
    });

    /** MODAL (FOR PASSWORD) — you will style as modal component **/
    const modalBtn = document.getElementById('openModalBtn');
    if(modalBtn){
        alert("TODO: Implement your modal component UI here");
    }
</script>
