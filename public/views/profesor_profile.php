<?php
session_start();
require_once __DIR__ . '/../../config/dbconnection.php';
// require_once './src/includes/cache_handler.php';
// aktivirajJavaCheSaProverom(10);

if (isset($_GET['logout'])) {
    session_unset();
    session_destroy();
    header('Location: ./authorization.php');
    exit;
}

if (!isset($_SESSION['user_id'])) {
    header('Location: ./authorization.php');
    exit;
}

$successMessage = null;
$errorMessage = null;

$professorId = (int) $_SESSION['professor_id'];

$stmt = $pdo->prepare("
    SELECT p.id, p.full_name, p.email, u.username
    FROM professor p
    JOIN user_account u ON u.professor_id = p.id
    WHERE p.id = ? AND p.is_active = TRUE AND u.is_active = TRUE
");
$stmt->execute([$professorId]);
$currentProfessor = $stmt->fetch();

if (!$currentProfessor) {
    session_unset();
    session_destroy();
    header('Location: ./authorization.php');
    exit;
}

function buildUsernameFromFullName(string $fullName): string
{
    $fullName = strtolower(trim(preg_replace('/\s+/', ' ', $fullName)));
    if ($fullName === '') {
        return '';
    }

    $parts = explode(' ', $fullName);
    $first = $parts[0];
    $last = $parts[count($parts) - 1];

    if ($last === '') {
        $last = $first;
    }

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
            $account = $stmt->fetch();

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
    <link rel="stylesheet" href="../assets/css/profesor_profile.css">
    <link rel="stylesheet" href="../assets/css/admin.css" />
    <link rel="stylesheet" href="../assets/css/base.css" />
    <link rel="stylesheet" href="../assets/css/fields.css" />
    <link rel="stylesheet" href="../assets/css/colors.css" />
    <link rel="stylesheet" href="../assets/css/stacks.css" />
    <link rel="stylesheet" href="../assets/css/tabs.css" />
    <link rel="stylesheet" href="../assets/css/table.css" />
</head>
<body>
    <header>
        <nav>
            <ul>
            <li><a href="index.php">
                <img src="../../img/fit-logo.png" alt="logo" id="logo">
            </a></li>
            <li><a href="index.php">Pocetna stranica</a></li>
            <li><a href="logout.php">Odjavi se</a></li>
            </ul>
        </nav>
    </header>

    <div class="container">
        <div class="profile-form-container">
            <h2>Profil Profesora</h2>

            <?php if ($errorMessage): ?>
                <div class="error"><?php echo htmlspecialchars($errorMessage); ?></div>
            <?php endif; ?>

            <?php if ($successMessage): ?>
                <div class="success"><?php echo htmlspecialchars($successMessage); ?></div>
            <?php endif; ?>

            <form class="profile-form" method="post">
                <input type="hidden" name="action" value="update_profile">

                <label for="full_name">Ime i prezime:</label>
                <input
                    type="text"
                    id="full_name"
                    name="full_name"
                    value="<?php echo htmlspecialchars($currentProfessor['full_name']); ?>"
                    required
                >

                <label for="email">Email:</label>
                <input
                    type="email"
                    id="email"
                    name="email"
                    value="<?php echo htmlspecialchars($currentProfessor['email']); ?>"
                    readonly
                >

                <button class="action-button edit-button" type="submit">Sačuvaj izmjene</button>
            </form>

            <button id="openModalBtn" type="button">Promijeni password</button>
        </div>
    </div>

    <div id="passwordModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeModal">&times;</span>
            <div class="modal-header">Promjena passworda</div>

            <form method="post">
                <input type="hidden" name="action" value="update_password">

                <label for="oldPassword">Stari password:</label>
                <input type="password" id="oldPassword" name="old_password" required>
                <a href="#">Zaboravili ste password?</a>

                <label for="newPassword">Novi password:</label>
                <input type="password" id="newPassword" name="new_password" required>

                <label for="confirmPassword">Potvrdi novi password:</label>
                <input type="password" id="confirmPassword" name="confirm_password" required>

                <button class="action-button edit-button" type="submit">Sačuvaj</button>
            </form>
        </div>
    </div>

    <script>
        const modal = document.getElementById('passwordModal');
        const openBtn = document.getElementById('openModalBtn');
        const closeBtn = document.getElementById('closeModal');

        openBtn.onclick = () => modal.style.display = 'block';
        closeBtn.onclick = () => modal.style.display = 'none';
        window.onclick = (event) => {
            if (event.target === modal) modal.style.display = 'none';
        }
    </script>
</body>
</html>