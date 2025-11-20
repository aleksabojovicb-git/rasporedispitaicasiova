<?php
session_start();
require '../../config/dbconnection.php';

if (isset($_SESSION['user_id'], $_SESSION['professor_id'])) {
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
    if ($fullName === '') {
        return '';
    }

    $parts = explode(' ', $fullName);
    $first = $parts[0];
    $last = $parts[count($parts) - 1] ?: $first;

    return $first . '.' . $last;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $formType = $_POST['form_type'] ?? '';

    if ($formType === 'signin') {
        $signinEmailValue = trim($_POST['email'] ?? '');
        $password = $_POST['password'] ?? '';

        if ($signinEmailValue === '' || $password === '') {
            $signinError = "Unesite email i lozinku.";
        } else {
            $stmt = $pdo->prepare("
                SELECT 
                    ua.id AS user_id,
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
            ");
            $stmt->execute([$signinEmailValue]);
            $user = $stmt->fetch();

            if (
                !$user ||
                !$user['user_active'] ||
                !$user['professor_active'] ||
                !password_verify($password, $user['password_hash'])
            ) {
                $signinError = "Pogrešan email ili lozinka.";
            } else {
                $_SESSION['user_id'] = (int) $user['user_id'];
                $_SESSION['professor_id'] = (int) $user['professor_id'];
                $_SESSION['professor_email'] = $user['email'];
                $_SESSION['professor_name'] = $user['full_name'];
                $_SESSION['role'] = $user['role_enum'];

                header("Location: ./profesor_profile.php");
                exit;
            }
        }
    } elseif ($formType === 'signup') {
        $activeTab = 'signup';
        $signupEmailValue = trim($_POST['email'] ?? '');
        $password = $_POST['password'] ?? '';
        $confirmPassword = $_POST['confirm'] ?? '';

        if ($signupEmailValue === '' || $password === '' || $confirmPassword === '') {
            $signupError = "Sva polja su obavezna.";
        } elseif (!filter_var($signupEmailValue, FILTER_VALIDATE_EMAIL)) {
            $signupError = "Unesite validnu email adresu.";
        } elseif (strlen($password) < 8) {
            $signupError = "Lozinka mora imati najmanje 8 karaktera.";
        } elseif ($password !== $confirmPassword) {
            $signupError = "Lozinke se ne poklapaju.";
        } else {
            $stmt = $pdo->prepare("SELECT id, full_name, is_active FROM professor WHERE email = ?");
            $stmt->execute([$signupEmailValue]);
            $professor = $stmt->fetch();

            if (!$professor || !$professor['is_active']) {
                $signupError = "Nije se moguce registrovati ovom email adresom.";
            } else {
                $stmt = $pdo->prepare("SELECT id FROM user_account WHERE professor_id = ?");
                $stmt->execute([(int) $professor['id']]);
                if ($stmt->fetch()) {
                    $signupError = "Nalog je već registrovan.";
                } else {
                    $username = buildUsernameFromFullName($professor['full_name']);
                    if ($username === '') {
                        $signupError = "Bug ako se unesu 2 prezimena. Podaci profesora nisu validni. Obratite se administratoru.";
                    } else {
                        try {
                            $stmt = $pdo->prepare("
                                INSERT INTO user_account (username, password_hash, role_enum, is_active, professor_id)
                                VALUES (?, ?, 'PROFESSOR', TRUE, ?)
                            ");
                            $stmt->execute([
                                $username,
                                password_hash($password, PASSWORD_DEFAULT),
                                (int) $professor['id']
                            ]);

                            $signupSuccess = "Nalog je uspješno kreiran. Sada se možete prijaviti.";
                            $signupError = null;
                            $activeTab = 'signin';
                        } catch (PDOException $e) {
                            $signupError = "Greška pri kreiranju naloga: " . $e->getMessage();
                        }
                    }
                }
            }
        }
    }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Sign in & Sign up</title>
  <link rel="stylesheet" href="../assets/css/base.css" />
  <link rel="stylesheet" href="../assets/css/fields.css" />
  <link rel="stylesheet" href="../assets/css/colors.css" />
  <link rel="stylesheet" href="../assets/css/stacks.css" />
  <link rel="stylesheet" href="../assets/css/tabs.css" />
</head>
<body>
  <main class="container">
    <section class="card">
      <div class="header-section">
        <h1 class="title">Welcome</h1>
        <p class="subtitle">Sign in or create an account</p>
      </div>

      <!-- Tabs -->
      <div class="tabs" id="tabs" data-active="<?php echo htmlspecialchars($activeTab); ?>" aria-label="Switch forms">
        <div class="tab-indicator" aria-hidden="true"></div>
        <button type="button" id="tab-signin" class="tab-btn" aria-controls="signin" aria-selected="<?php echo $activeTab === 'signin' ? 'true' : 'false'; ?>">Sign in</button>
        <button type="button" id="tab-signup" class="tab-btn" aria-controls="signup" aria-selected="<?php echo $activeTab === 'signup' ? 'true' : 'false'; ?>">Sign up</button>
      </div>

      <!-- Stacks-->
      <div class="stacks" id="stacks" data-active="<?php echo htmlspecialchars($activeTab); ?>">
        <!-- Sign In -->
        <form id="signin" class="stack" autocomplete="on" novalidate method="post">
          <input type="hidden" name="form_type" value="signin" />
          <div class="field">
            <label for="si-email">Email</label>
            <input type="email" id="si-email" name="email" inputmode="email" placeholder="you@example.com" value="<?php echo htmlspecialchars($signinEmailValue); ?>" required />
            <p class="error" data-error-for="si-email"></p>
          </div>
          <div class="field pwd-wrap">
            <label for="si-password">Password</label>
            <input type="password" id="si-password" name="password" placeholder="••••••••" minlength="6" required />
            <button tabindex="-1" type="button" class="toggle-eye" data-toggle="si-password" aria-label="Show/Hide password">👁️</button>
            <p class="error" data-error-for="si-password"></p>
          </div>
          <?php if ($signinError): ?>
          <div class="server-error"><?php echo htmlspecialchars($signinError); ?></div>
          <?php endif; ?>
          <div class="actions">
            <button class="btn" type="submit">Sign in</button>
          </div>
        </form>

        <!-- Sign Up -->
        <form id="signup" class="stack" autocomplete="on" novalidate method="post">
          <input type="hidden" name="form_type" value="signup" />
          <div class="field">
            <label for="su-email">Email</label>
            <input type="email" id="su-email" name="email" inputmode="email" placeholder="you@example.com" value="<?php echo htmlspecialchars($signupEmailValue); ?>" required />
            <p class="error" data-error-for="su-email"></p>
          </div>
          <div class="field pwd-wrap">
            <label for="su-password">Password</label>
            <input type="password" id="su-password" name="password" placeholder="at least 6 characters" minlength="6" required />
            <button tabindex="-1" type="button" class="toggle-eye" data-toggle="su-password" aria-label="Show/Hide password">👁️</button>
            <div class="meter" aria-hidden="true"><i id="pwd-meter"></i></div>
            <p class="help" id="pwd-hint">Use upper/lowercase letters and digits.</p>
            <p class="error" data-error-for="su-password"></p>
          </div>
          <div class="field">
            <label for="su-confirm">Confirm password</label>
            <input type="password" id="su-confirm" name="confirm" placeholder="repeat your password" minlength="6" required />
            <p class="error" data-error-for="su-confirm"></p>
          </div>
          <div class="actions">
            <button class="btn" type="submit">Create account</button>
          </div>
          <?php if ($signupError): ?>
          <div class="server-error"><?php echo htmlspecialchars($signupError); ?></div>
          <?php endif; ?>
          <?php if ($signupSuccess): ?>
          <div class="success-server"><?php echo htmlspecialchars($signupSuccess); ?></div>
          <?php endif; ?>
        </form>
      </div>
    </section>
  </main>

  <script src="../assets/js/auth-form.js"></script>
</body>
</html>