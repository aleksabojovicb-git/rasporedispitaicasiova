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
      <div class="tabs" id="tabs" data-active="signin" aria-label="Switch forms">
        <div class="tab-indicator" aria-hidden="true"></div>
        <button type="button" id="tab-signin" class="tab-btn" aria-controls="signin" aria-selected="true">Sign in</button>
        <button type="button" id="tab-signup" class="tab-btn" aria-controls="signup" aria-selected="false">Sign up</button>
      </div>

      <!-- Stacks-->
      <div class="stacks" id="stacks" data-active="signin">
        <!-- Sign In -->
        <form id="signin" class="stack" autocomplete="on" novalidate>
          <div class="field">
            <label for="si-email">Email</label>
            <input type="email" id="si-email" name="email" inputmode="email" placeholder="you@example.com" required />
            <p class="error" data-error-for="si-email"></p>
          </div>
          <div class="field pwd-wrap">
            <label for="si-password">Password</label>
            <input type="password" id="si-password" name="password" placeholder="••••••••" minlength="6" required />
            <button tabindex="-1" type="button" class="toggle-eye" data-toggle="si-password" aria-label="Show/Hide password">👁️</button>
            <p class="error" data-error-for="si-password"></p>
          </div>
          <div class="actions">
            <button class="btn" type="submit">Sign in</button>
          </div>
        </form>

        <!-- Sign Up -->
        <form id="signup" class="stack" autocomplete="on" novalidate>
          <div class="field">
            <label for="su-first">First name</label>
            <input type="text" id="su-first" name="first_name" placeholder="John" required />
            <p class="error" data-error-for="su-first"></p>
          </div>
          <div class="field">
            <label for="su-last">Last name</label>
            <input type="text" id="su-last" name="last_name" placeholder="Doe" required />
            <p class="error" data-error-for="su-last"></p>
          </div>
          <div class="field">
            <label for="su-email">Email</label>
            <input type="email" id="su-email" name="email" inputmode="email" placeholder="you@example.com" required />
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
          <div class="field">
             <label> <!-- Just field for fun -->
              <input type="checkbox" id="su-terms" name="terms" /> I accept the Terms of Use
            </label>
            <p class="error" data-error-for="su-terms"></p>
          </div>
          <div class="actions">
            <button class="btn" type="submit">Create account</button>
          </div>
        </form>
      </div>

      <p class="hint">Hook these forms up to your backend: <code>fetch('/api/auth/login')</code> and <code>fetch('/api/auth/register')</code>.</p>
    </section>
  </main>

  <script src="../assets/js/auth-form.js"></script>
</body>
</html>

<?php
require '../../config/dbconnection.php';

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['email'], $_POST['first_name'], $_POST['last_name'], $_POST['password'])) {
    $email = $_POST['email'];
    $firstName = $_POST['first_name'];
    $lastName = $_POST['last_name'];
    $password = $_POST['password'];

    $stmt = $pdo->prepare("SELECT email FROM professor WHERE email = ?");
    $stmt->execute([$email]);

    if ($stmt->rowCount() == 0) {
        echo json_encode(['success' => false, 'message' => 'Ne mozete se registrovati sa ovom email adresom. Molimo korisite email adresu univerziteta.']);
    } else {
        try {
            $fullName = $firstName . ' ' . $lastName;
            $hashedPassword = password_hash($password, PASSWORD_DEFAULT);

            $updateStmt = $pdo->prepare("UPDATE professor SET full_name = ?, password = ? WHERE email = ?");
            $updateResult = $updateStmt->execute([$fullName, $hashedPassword, $email]);

            if ($updateResult) {
                echo json_encode(['success' => true, 'message' => 'Registration successful']);
            } else {
                echo json_encode(['success' => false, 'message' => 'Error updating professor data']);
            }
        } catch (PDOException $e) {
            echo json_encode(['success' => false, 'message' => 'Greska pri registraciji: ' . $e->getMessage()]);
        }
    }
    exit;
}