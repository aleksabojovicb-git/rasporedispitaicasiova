<?php
// short English comment: load .env locally if present, otherwise fall back to real env vars (Render, etc.)
$envFile = __DIR__ . '/../.env';
$fileEnv = is_readable($envFile) ? parse_ini_file($envFile) : false;
if ($fileEnv === false) {
    $fileEnv = [];
}

// short English comment: getenv()/$_ENV take priority only when the .env file didn't provide a key
function env_val($fileEnv, $key) {
    if (array_key_exists($key, $fileEnv) && $fileEnv[$key] !== '') {
        return $fileEnv[$key];
    }
    $v = getenv($key);
    return ($v !== false) ? $v : ($_ENV[$key] ?? null);
}

$DB_HOST = env_val($fileEnv, 'DB_HOST');
$DB_PORT = env_val($fileEnv, 'DB_PORT');
$DB_NAME = env_val($fileEnv, 'DB_NAME');
$DB_USER = env_val($fileEnv, 'DB_USER');
$DB_PASS = env_val($fileEnv, 'DB_PASS');
$DB_SSLMODE = env_val($fileEnv, 'DB_SSLMODE');
$DB_SSLROOTCERT = env_val($fileEnv, 'DB_SSLROOTCERT');

if (!$DB_HOST || !$DB_PORT || !$DB_NAME || !$DB_USER) {
    echo "Greska pri konekciji: nedostaju DB podesavanja (proveri .env ili environment varijable na hostingu).";
    exit;
}

$dsn = sprintf(
    'pgsql:host=%s;port=%s;dbname=%s;%s',
    $DB_HOST,
    $DB_PORT,
    $DB_NAME,
    ($DB_SSLMODE === 'require')
        ? 'sslmode=require'
        : 'sslmode=verify-ca;sslrootcert=' . $DB_SSLROOTCERT
);

try {
    $pdo = new PDO($dsn, $DB_USER, $DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    ]);
} catch (PDOException $e) {
    // short English comment: show connection error and stop
    echo "Greska pri konekciji: " . $e->getMessage();
    exit;
}

// short English comment: return PDO to caller
return $pdo;
