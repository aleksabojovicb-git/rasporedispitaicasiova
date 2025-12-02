<?php

require __DIR__ . '/../../vendor/autoload.php';

use PHPMailer\PHPMailer\PHPMailer;
use PHPMailer\PHPMailer\SMTP;
use PHPMailer\PHPMailer\Exception;

function generateVerificationCode($length = 6) {
    $characters = '0123456789';
    $code = '';
    for ($i = 0; $i < $length; $i++) {
        $code .= $characters[rand(0, strlen($characters) - 1)];
    }
    return $code;
}

function sendVerificationEmail($emailAddress, $verificationCode) {
    $mail = new PHPMailer(true);

    try {
        $mail->SMTPDebug = SMTP::DEBUG_OFF;
        $mail->isSMTP();
        $mail->Host = 'smtp-relay.brevo.com'; 
        $mail->SMTPAuth = true;
        $mail->Username = '9d2675001@smtp-brevo.com';
        $mail->Password = 'Sa83rFy5AfqcbTHN'; 
        $mail->SMTPSecure = PHPMailer::ENCRYPTION_STARTTLS;
        $mail->Port = 587;

        $mail->setFrom('aleksabojovic1b@gmail.com', 'Aleksa Bojovic');
        $mail->addAddress($emailAddress);

        $mail->isHTML(false);
        $mail->Subject = "Email Verification";
        $mail->Body = "Your email verification code is: $verificationCode";

        $mail->send();
        return true;

    } catch (Exception $e) {
        return false;
    }
}

$email = "bs64188@gmail.com";
$verificationCode = generateVerificationCode();

if(sendVerificationEmail($email, $verificationCode)){
    echo "A verification email has been sent to $email";
} else {
    echo "Failed to send";
}

?>
