<?php
if (session_status() === PHP_SESSION_NONE) {
    session_start();
}

$role = $_SESSION['role'] ?? null;
$loggedIn = isset($_SESSION['user_id']);

$panelHref = './authorization.php';

if ($loggedIn) {
    $panelHref = './professor_panel.php';
    if ($role === 'ADMIN') {
        $panelHref = './admin_panel.php';
    }
}
?>

<header class="site-header">
    <div class="header-inner">
        <a class="logo" href="index.php">
            <img src="../../img/fit-logo.png" alt="FTN Logo" id="top-logo">
            <span class="site-title">FTN Sistem</span>
        </a>

        <nav>
            <ul>
                <li><a href="index.php">Početna</a></li>
                <li><a href="<?= htmlspecialchars($panelHref, ENT_QUOTES, 'UTF-8'); ?>">Panel</a></li>

                <?php if ($loggedIn): ?>
                    <li><a href="logout.php">Odjavi se</a></li>
                <?php else: ?>
                    <li><a href="authorization.php">Prijava</a></li>
                <?php endif; ?>
            </ul>
        </nav>
    </div>
</header>
