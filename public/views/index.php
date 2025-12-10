<?php
session_start();

$professorPanelHref = './authorization.php';

if (isset($_SESSION['user_id'])) {
    $professorPanelHref = './professor_panel.php';
}

if (isset($_SESSION['role']) && $_SESSION['role'] === 'ADMIN') {
    $professorPanelHref = './admin_panel.php';
}

/** Page title for <head> */
$pageTitle = "FTN Sistem — Početna";

/** Include common head (all CSS + meta) */
include __DIR__ . "/partials/head.php";
?>

<body>

<?php include __DIR__ . "/partials/header.php"; ?>

<!-- HERO SECTION -->
<section class="hero">
    <img id="hero-logo" src="../../img/fit-logo.png" alt="FTN Logo">
    <h1>FTN Sistem</h1>
    <p>
        Moderna digitalna platforma za profesore, studente i administraciju.
        Jednostavan pristup rasporedima, predmetima i internim informacijama fakulteta.
    </p>

    <div class="hero-btns">
        <a href="<?= htmlspecialchars($professorPanelHref, ENT_QUOTES, 'UTF-8'); ?>"
           class="button button-primary">
            Profesor Panel
        </a>
    </div>
</section>

<!-- FEATURES -->
<section class="features">
    <div class="feature-card">
        <div class="feature-icon">📅</div>
        <div class="feature-title">Pametan Raspored</div>
        <div class="feature-text">
            Automatski organizovani pregledi predavanja, termina i učionica.
        </div>
    </div>

    <div class="feature-card">
        <div class="feature-icon">📚</div>
        <div class="feature-title">Upravljanje Rasporedom</div>
        <div class="feature-text">
            Profesori mogu jednostavno uređivati termine i učionice predavanja.
        </div>
    </div>

    <div class="feature-card">
        <div class="feature-icon">🔐</div>
        <div class="feature-title">Siguran Pristup</div>
        <div class="feature-text">
            Napredan sistem autentikacije sa ulogama za korisnike.
        </div>
    </div>
</section>

<?php include __DIR__ . "/partials/footer.php"; ?>

<script src="/rasporedispitaicasiova//public/assets/js/pages/index.js"></script>
</body>
</html>
