<?php
session_start();
require_once __DIR__ . '/../../config/dbconnection.php';

/* ============================
    AUTH & ACCESS PROTECTION
============================ */

if (!isset($_SESSION['user_id']) || ($_SESSION['role'] ?? '') !== 'ADMIN') {
    header('Location: ./authorization.php');
    exit;
}

$successMessage = null;
$errorMessage = null;

/* ============================
    PAGE META
============================ */

$pageTitle = "Administratorski Panel";
include __DIR__ . "/partials/head.php";
include __DIR__ . "/partials/admin_header.php";
?>

<div class="container">
    <h2 class="section-title">Administratorski Panel</h2>

    <?php if ($errorMessage): ?>
        <div class="error"><?= htmlspecialchars($errorMessage); ?></div>
    <?php endif; ?>

    <?php if ($successMessage): ?>
        <div class="success"><?= htmlspecialchars($successMessage); ?></div>
    <?php endif; ?>

    <!-- ==== PARENT TAB WRAPPER ==== -->
    <div class="tabs" data-active="users">

        <div class="tab-list">
            <button class="tab-button active" data-target="usersTab">Korisnici</button>
            <button class="tab-button" data-target="professorsTab">Profesori</button>
            <button class="tab-button" data-target="coursesTab">Predmeti</button>
            <button class="tab-button" data-target="scheduleTab">Raspored</button>
        </div>

        <!-- USERS TAB -->
        <div class="tab-content active" id="usersTab">
            <h3 class="section-title">Pregled korisnika</h3>

            <table class="table">
                <thead>
                <tr><th>ID</th><th>Email</th><th>Username</th><th>Uloga</th></tr>
                </thead>
                <tbody>
                <?php
                try {
                    $stmt = $pdo->query("
                        SELECT ua.id, ua.username, ua.role_enum, p.email
                        FROM user_account ua
                        LEFT JOIN professor p ON ua.professor_id = p.id
                        ORDER BY ua.id DESC
                    ");
                    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
                        echo "<tr>";
                        echo "<td>".htmlspecialchars($row['id'])."</td>";
                        echo "<td>".htmlspecialchars($row['email'])."</td>";
                        echo "<td>".htmlspecialchars($row['username'])."</td>";
                        echo "<td>".htmlspecialchars($row['role_enum'])."</td>";
                        echo "</tr>";
                    }
                } catch(PDOException $e) {
                    echo "<tr><td colspan='4'>Greška: ".htmlspecialchars($e->getMessage())."</td></tr>";
                }
                ?>
                </tbody>
            </table>
        </div>

        <!-- PROFESSORS TAB -->
        <div class="tab-content" id="professorsTab">
            <h3 class="section-title">Lista profesora</h3>

            <table class="table">
                <thead>
                <tr><th>ID</th><th>Ime</th><th>Email</th></tr>
                </thead>
                <tbody>
                <?php
                try {
                    $stmt = $pdo->query("
                        SELECT id, full_name, email
                        FROM professor
                        WHERE is_active = TRUE
                        ORDER BY full_name
                    ");
                    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
                        echo "<tr>";
                        echo "<td>".htmlspecialchars($row['id'])."</td>";
                        echo "<td>".htmlspecialchars($row['full_name'])."</td>";
                        echo "<td>".htmlspecialchars($row['email'])."</td>";
                        echo "</tr>";
                    }
                } catch(PDOException $e) {
                    echo "<tr><td colspan='3'>Greška: ".htmlspecialchars($e->getMessage())."</td></tr>";
                }
                ?>
                </tbody>
            </table>
        </div>

        <!-- COURSES TAB -->
        <div class="tab-content" id="coursesTab">
            <h3 class="section-title">Predmeti</h3>

            <table class="table">
                <thead>
                <tr><th>ID</th><th>Naziv</th><th>Semestar</th></tr>
                </thead>
                <tbody>
                <?php
                try {
                    $stmt = $pdo->query("SELECT id, name, semester FROM course ORDER BY semester, name");
                    while ($row = $stmt->fetch(PDO::FETCH_ASSOC)) {
                        echo "<tr>";
                        echo "<td>".htmlspecialchars($row['id'])."</td>";
                        echo "<td>".htmlspecialchars($row['name'])."</td>";
                        echo "<td>".htmlspecialchars($row['semester'])."</td>";
                        echo "</tr>";
                    }
                } catch(PDOException $e) {
                    echo "<tr><td colspan='3'>Greška: ".htmlspecialchars($e->getMessage())."</td></tr>";
                }
                ?>
                </tbody>
            </table>
        </div>

        <!-- SCHEDULE TAB -->
        <div class="tab-content" id="scheduleTab">
            <h3 class="section-title">Pregled rasporeda</h3>

            <p>Ovde ide UI za upravljanje rasporedom, dodavanje sali itd.</p>
        </div>

    </div>
</div>

<?php include __DIR__ . "/partials/footer.php"; ?>

<script>
    /* BASIC TAB NAVIGATION */
    const tabButtons = document.querySelectorAll('.tab-button');
    const tabContents = document.querySelectorAll('.tab-content');

    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            tabButtons.forEach(b => b.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));

            btn.classList.add('active');
            document.getElementById(btn.dataset.target).classList.add('active');
        });
    });
</script>
