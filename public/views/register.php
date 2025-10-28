<!DOCTYPE html>
<html lang="en">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Registracija</title>
</head>

<body>
<div id="register-div">
    <form action="/register" method="post">
        <label for="new-username">Username:</label>
        <input type="text" id="new-username" name="username" required>
        <br>
        <label for="email">Email:</label>
        <input type="email" id="email" name="email" required>
        <br>
        <label for="new-password">Password:</label>
        <input type="password" id="new-password" name="password" required>
        <br>
        <label for="confirm-password">Confirm Password:</label>
        <input type="password" id="confirm-password" name="confirm-password" required>
        <br>
        <button type="submit">Register</button>
    </form>
</div>

</body>

</html>