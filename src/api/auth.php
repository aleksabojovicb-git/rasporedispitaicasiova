<?php
//Sve nazive kolona treba provjeriti
include "../../config/dbconnection.php"; // ovde trebaš imati $pdo = new PDO(...);
session_start();

$data = json_decode(file_get_contents('php://input'), true);
$action = $data['action'];

if ($action === "register") {
    $username = trim($data['username']);
    $email = trim($data['email']);
    $passwordHash = password_hash($data['password'], PASSWORD_DEFAULT);

    // Da li postoji korisnik sa istim mejlom
    $emailCheckStmt = $pdo->prepare("SELECT id FROM users WHERE email = :email");
    $emailCheckStmt->execute([':email' => $email]);
    $existingUser = $emailCheckStmt->fetch(PDO::FETCH_ASSOC);

    if ($existingUser) {
        echo json_encode(["status" => "error", "message" => "Email već postoji"]);
        exit;
    }

    // Registracija korisnika
    $registerStmt = $pdo->prepare("INSERT INTO users (username, email, password) VALUES (:username, :email, :password)");
    $success = $registerStmt->execute([
        ':username' => $username,
        ':email' => $email,
        ':password' => $passwordHash
    ]);

    if ($success) {
        echo json_encode(["status" => "success", "message" => "Uspješna registracija"]);
    } else {
        echo json_encode(["status" => "error", "message" => "Greška prilikom registracije"]);
    }

} elseif ($action === "login") {
    $email = trim($data['email']);
    $password = trim($data['password']);

    
    $userStmt = $pdo->prepare("SELECT id, username, password, user_type FROM users WHERE email = :email");
    $userStmt->execute([':email' => $email]);
    $userData = $userStmt->fetch(PDO::FETCH_ASSOC);

    if (!$userData) {
        echo json_encode(["status" => "error", "message" => "Neispravan mejl ili lozinka"]);
        exit;
    }

    // Verifikacija sifre
    if (password_verify($password, $userData['password'])) {

        
        $userTypeStmt = $pdo->prepare("SELECT type FROM users_type WHERE id = :id");
        $userTypeStmt->execute([':id' => $userData['user_type']]);
        $userTypeData = $userTypeStmt->fetch(PDO::FETCH_ASSOC);

        if (!$userTypeData) {
            echo json_encode(["status" => "error", "message" => "Tip korisnika nije pronađen"]);
            exit;
        }

        
        $_SESSION['user_type'] = $userTypeData['type'];
        $_SESSION['user_id'] = $userData['id'];
        $_SESSION['username'] = $userData['username'];

        echo json_encode(["status" => "success", "message" => "Uspješna prijava"]);
    } else {
        echo json_encode(["status" => "error", "message" => "Neispravan mejl ili lozinka"]);
    }
}
?>
