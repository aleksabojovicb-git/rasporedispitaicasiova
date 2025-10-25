<?php
$env = parse_ini_file(__DIR__ . '/../.env');

$dsn = sprintf(
    'pgsql:host=%s;port=%s;dbname=%s;%s',
    $env['DB_HOST'],
    $env['DB_PORT'],
    $env['DB_NAME'],
    ($env['DB_SSLMODE'] === 'require')
        ? 'sslmode=require'
        : 'sslmode=verify-ca;sslrootcert=' . $env['DB_SSLROOTCERT']
);

try {
    $pdo = new PDO($dsn, $env['DB_USER'], $env['DB_PASS'], [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
} catch (PDOException $e) {
    echo "Greska pri konekciji: " . $e->getMessage();
    exit;
}

$sql = "SELECT * FROM faculty";
$stmt = $pdo->query($sql);

$rows = $stmt->fetchAll();

foreach ($rows as $row) {
    print_r($row);
    echo "<br>";
}
