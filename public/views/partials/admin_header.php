<?php
if (session_status() === PHP_SESSION_NONE) {
    session_start();
}

$role = $_SESSION['role'] ?? null;
$loggedIn = isset($_SESSION['user_id']);

if (!$loggedIn || $role !== 'ADMIN') {
    header("Location: ./authorization.php");
    exit;
}
?>

<header class="admin-header">
    <div class="admin-header-inner">
        <a class="admin-logo" href="admin_panel.php">
            <img src="../../img/fit-logo.png" alt="FTN Logo" id="admin-logo">
            <span class="admin-title">FTN Admin Panel</span>
        </a>

        <nav class="admin-nav">
            <ul>
                <li><a href="admin_panel.php">Početna</a></li>
                <li><a href="schedule_admin.php">Rasporedi</a></li>
                <li><a href="subjects_admin.php">Predmeti</a></li>
                <li><a href="professors_admin.php">Profesori</a></li>
                <li><a href="logout.php">Odjavi se</a></li>
            </ul>
        </nav>
    </div>
</header>
