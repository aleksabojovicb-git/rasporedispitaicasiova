<?php
$servername="localhost";
$username="";
$password="";
$database="";

$conn = new mysqli($servername,$username,$password,$database);
if($conn->connect_error){
    die("Conection failed: " . $conn->connect_error);
}

?>