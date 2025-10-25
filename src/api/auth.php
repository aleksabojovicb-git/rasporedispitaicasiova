<?php

include "../../config/dbconnection.php";
session_start();

$data = json_decode(file_get_contents('php://input'), true);

$action = $data['action'];

if ($action === "register") {
    $username = trim($data['username']);
    $email = trim($data['email']);
    $passwordHash = password_hash($data['password'], PASSWORD_DEFAULT);

    // Da li postoji korisnik sa istim mejlom
    $emailCheckStmt = $conn->prepare("SELECT id FROM users WHERE email = ?");
    $emailCheckStmt->bind_param("s", $email);
    $emailCheckStmt->execute();
    $emailCheckResult = $emailCheckStmt->get_result();

    if ($emailCheckResult->num_rows > 0) {
        echo json_encode(["status" => "error", "message" => "Email vec postoji"]);
        exit;
    }
    
    $registerStmt = $conn->prepare("INSERT INTO users (username,email,password) VALUES (?, ?, ?)");
    $registerStmt->bind_param("sss", $username, $email, $passwordHash);

    if ($registerStmt->execute()) {
        echo json_encode(["status" => "success", "message" => "Uspjesna registracija"]);
    } else {
        echo json_encode(["status" => "error", "message" => "Greska prilikom registracije"]);
    }

} elseif ($action === "login") {
    $email = trim($data['email']);
    $password = trim($data['password']);

    // nazive ovih kolona treba provjeriti kako su nazvani u bazi
    $userStmt = $conn->prepare("SELECT id,username,password,user_type FROM users WHERE email = ?");
    $userStmt->bind_param("s", $email);
    $userStmt->execute();
    $userResult = $userStmt->get_result();

    if ($userResult->num_rows === 0) {
        echo json_encode(["status" => "error", "message" => "Neispravan mejl ili lozinka"]);
        exit;
    }

    $userData = $userResult->fetch_assoc();

    // Verifikacija sifre
    if (password_verify($password, $userData['password'])) {

        // Provjera tipa korisnika
        $userTypeStmt = $conn->prepare("SELECT type FROM users_type WHERE id=?");
        $userTypeStmt->bind_param("i", $userData['user_type']);
        $userTypeStmt->execute();
        $userTypeResult = $userTypeStmt->get_result();

        if ($userTypeResult->num_rows === 0) {
            echo json_encode(["status" => "error", "message" => "Tip korisnika nije pronađen"]);
            exit;
        }

        $userTypeData = $userTypeResult->fetch_assoc();
        
        $_SESSION['user_type'] = $userTypeData['type'];
        $_SESSION['user_id'] = $userData['id'];
        $_SESSION['username'] = $userData['username'];

        echo json_encode(["status" => "success", "message" => "Uspjesna prijava"]);
    } else {
        echo json_encode(["status" => "error", "message" => "Neispravan mejl ili lozinka"]);
    }
}

?>
