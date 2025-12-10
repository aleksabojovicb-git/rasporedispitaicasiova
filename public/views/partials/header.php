<?php
// Header partial: shows logo and nav based on session state
if (session_status() === PHP_SESSION_NONE) {
    session_start();
}
$role = $_SESSION['role'] ?? null;
$loggedIn = isset($_SESSION['user_id']);
$panelHref = './authorization.php';
if ($loggedIn) {
    $panelHref = './profesor_profile.php';
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
                <li><a href="<?php echo htmlspecialchars($panelHref, ENT_QUOTES, 'UTF-8'); ?>">Panel</a></li>
                <?php if ($loggedIn): ?>
                    <li><a href="logout.php">Odjavi se</a></li>
                <?php else: ?>
                    <li><a href="authorization.php">Prijava</a></li>
                <?php endif; ?>
            </ul>
        </nav>
    </div>
</header>

<style>
    /* Minimal header styles to keep layout consistent */
    .site-header{background:#fff;border-bottom:1px solid #eee;padding:10px 16px}
    .header-inner{max-width:1100px;margin:0 auto;display:flex;align-items:center;justify-content:space-between}
    .logo{display:flex;align-items:center;text-decoration:none;color:inherit}
    #top-logo{height:48px;margin-right:10px}
    .site-title{font-size:18px;font-weight:600}
    .site-header nav ul{list-style:none;margin:0;padding:0;display:flex;gap:12px}
    .site-header nav a{color:#111;text-decoration:none;padding:6px 8px}
    @media (max-width:600px){#top-logo{height:36px}.site-title{display:none}}
</style>
