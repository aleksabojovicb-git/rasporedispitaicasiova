<?php
session_start();

$pdo = require '../../config/dbconnection.php';

if (!isset($pdo) || !($pdo instanceof PDO)) {
    throw new RuntimeException('Missing or invalid PDO instance');
}

if (isset($_SESSION['user_id'])) {
    if (isset($_SESSION['role']) && $_SESSION['role'] === 'ADMIN') {
        header('Location: ./admin_panel.php');
        exit;
    }
    header('Location: ./profesor_profile.php');
    exit;
}

$signinError = null;
$signupError = null;
$signupSuccess = null;
$signinEmailValue = '';
$signupEmailValue = '';
$activeTab = 'signin';

function buildUsernameFromFullName(string $fullName): string
{
    $fullName = strtolower(trim(preg_replace('/\s+/', ' ', $fullName)));
    if ($fullName === '') return '';
    $parts = explode(' ', $fullName);
    $first = $parts[0];
    $last = $parts[count($parts) - 1] ?: $first;
    return $first . '.' . $last;
}

function validateSignup(PDO $pdo, string $email, string $password, string $confirm): array
{
    if ($email === '' || $password === '' || $confirm === '') {
        return ['error' => "Sva polja su obavezna."];
    }
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        return ['error' => "Unesite validnu email adresu."];
    }
    if (strlen($password) < 8) {
        return ['error' => "Lozinka mora imati najmanje 8 karaktera."];
    }
    if ($password !== $confirm) {
        return ['error' => "Lozinke se ne poklapaju."];
    }

    $stmt = $pdo->prepare("
        SELECT p.id, p.full_name, p.is_active, ua.id AS user_id
        FROM professor p
        LEFT JOIN user_account ua ON ua.professor_id = p.id
        WHERE p.email = ?
        LIMIT 1
    ");
    $stmt->execute([$email]);
    $row = $stmt->fetch();

    if (!$row || !$row['is_active']) {
        return ['error' => "Nije se moguće registrovati ovom email adresom."];
    }
    if (!empty($row['user_id'])) {
        return ['error' => "Nalog je već registrovan."];
    }

    return ['professor' => $row];
}

function validateSignin(PDO $pdo, string $identifier, string $password): array
{
    $identifier = trim($identifier);
    if ($identifier === '' || $password === '') {
        return ['error' => 'Unesite email i lozinku.'];
    }

    // find by email
    $stmt = $pdo->prepare("
        SELECT 
            ua.id AS user_id,
            ua.username,
            ua.password_hash,
            ua.role_enum,
            ua.is_active AS user_active,
            p.id AS professor_id,
            p.full_name,
            p.email,
            p.is_active AS professor_active
        FROM user_account ua
        JOIN professor p ON ua.professor_id = p.id
        WHERE p.email = ?
        LIMIT 1
    ");
    $stmt->execute([$identifier]);
    $user = $stmt->fetch();

    // find by username (admin only)
    if (!$user) {
        $stmt = $pdo->prepare("
            SELECT 
                ua.id AS user_id,
                ua.username,
                ua.password_hash,
                ua.role_enum,
                ua.is_active AS user_active,
                p.id AS professor_id,
                p.full_name,
                p.email,
                p.is_active AS professor_active
            FROM user_account ua
            LEFT JOIN professor p ON ua.professor_id = p.id
            WHERE ua.username = ? AND ua.role_enum = 'ADMIN'
            LIMIT 1
        ");
        $stmt->execute([$identifier]);
        $user = $stmt->fetch();
    }

    if (!$user) {
        return ['error' => 'Pogrešan email ili lozinka.'];
    }

    if (empty($user['user_active'])) {
        return ['error' => 'Račun nije aktivan.'];
    }

    if ($user['role_enum'] !== 'ADMIN' && !empty($user['professor_id'])) {
        if (empty($user['professor_active'])) {
            return ['error' => 'Pogrešan email ili lozinka.'];
        }
    }

    $hash = $user['password_hash'] ?? '';
    $passwordOk = false;

    if ($hash !== '' && password_verify($password, $hash)) {
        $passwordOk = true;
    } elseif ($hash !== '' && strlen($hash) === 32 && strtolower($hash) === md5($password)) {
        $passwordOk = true;
    } elseif ($hash === $password) {
        $passwordOk = true;
    }

    if (!$passwordOk) {
        return ['error' => 'Pogrešan email ili lozinka.'];
    }

    return ['user' => $user];
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $formType = $_POST['form_type'] ?? '';

    if ($formType === 'signin') {
        $signinEmailValue = trim($_POST['email'] ?? '');
        $password = $_POST['password'] ?? '';
        $res = validateSignin($pdo, $signinEmailValue, $password);

        if (!empty($res['error'])) {
            $signinError = $res['error'];
        } else {
            $user = $res['user'];

            if ($user['role_enum'] === 'ADMIN') {
                $_SESSION['user_id'] = (int)$user['user_id'];
                $_SESSION['role'] = $user['role_enum'];
                header("Location: ./admin_panel.php");
                exit;
            }

            $_SESSION['user_id'] = (int)$user['user_id'];
            $_SESSION['professor_id'] = (int)$user['professor_id'];
            $_SESSION['role'] = $user['role_enum'];
            header("Location: ./profesor_profile.php");
            exit;
        }
    }

    if ($formType === 'signup') {
        $activeTab = 'signup';
        $signupEmailValue = trim($_POST['email'] ?? '');
        $password = $_POST['password'] ?? '';
        $confirmPassword = $_POST['confirm'] ?? '';

        $validation = validateSignup($pdo, $signupEmailValue, $password, $confirmPassword);

        if (!empty($validation['error'])) {
            $signupError = $validation['error'];
        } else {
            $professor = $validation['professor'];
            $username = buildUsernameFromFullName($professor['full_name']);

            $stmt = $pdo->prepare("
                INSERT INTO user_account (username, password_hash, role_enum, is_active, professor_id)
                VALUES (?, ?, 'PROFESSOR', TRUE, ?)
            ");
            $stmt->execute([
                $username,
                password_hash($password, PASSWORD_DEFAULT),
                (int)$professor['id']
            ]);

            $signupSuccess = "Nalog je uspješno kreiran.";
            $signupError = null;
            $activeTab = 'signin';
        }
    }
}

/** ===== PARTIALS ===== */
$pageTitle = "Prijava / Registracija";
include __DIR__ . "/partials/head.php";
?>

<main class="container">
    <section class="card">
        <div class="header-section">
            <h1 class="title">Welcome</h1>
            <p class="subtitle">Sign in or create an account</p>
        </div>

        <!-- TABS -->
        <div class="tabs" id="tabs" data-active="<?= htmlspecialchars($activeTab) ?>">
            <button type="button" class="tab-button" data-target="signin">Sign in</button>
            <button type="button" class="tab-button" data-target="signup">Sign up</button>
        </div>

        <!-- STACKS -->
        <div class="stacks" data-active="<?= htmlspecialchars($activeTab) ?>">

            <!-- SIGN IN -->
            <form id="signin" class="stack" method="post">
                <input type="hidden" name="form_type" value="signin" />

                <div class="field">
                    <label>Email</label>
                    <input name="email" value="<?= htmlspecialchars($signinEmailValue) ?>">
                </div>

                <div class="field">
                    <label>Password</label>
                    <input type="password" name="password">
                </div>

                <?php if ($signinError): ?>
                    <div class="error"><?= htmlspecialchars($signinError) ?></div>
                <?php endif; ?>

                <button class="button button-primary" type="submit">Sign in</button>
            </form>

            <!-- SIGN UP -->
            <form id="signup" class="stack" method="post">
                <input type="hidden" name="form_type" value="signup" />

                <div class="field">
                    <label>Email</label>
                    <input name="email" value="<?= htmlspecialchars($signupEmailValue) ?>">
                </div>

                <div class="field">
                    <label>Password</label>
                    <input type="password" name="password">
                </div>

                <div class="field">
                    <label>Confirm password</label>
                    <input type="password" name="confirm">
                </div>

                <?php if ($signupError): ?>
                    <div class="error"><?= htmlspecialchars($signupError) ?></div>
                <?php endif; ?>

                <?php if ($signupSuccess): ?>
                    <div class="success"><?= htmlspecialchars($signupSuccess) ?></div>
                <?php endif; ?>

                <button class="button button-primary" type="submit">Create account</button>
            </form>
        </div>
    </section>
</main>

<?php include __DIR__ . "/partials/footer.php"; ?>
<script src="/public/assets/js/pages/auth-form.js"></script>
