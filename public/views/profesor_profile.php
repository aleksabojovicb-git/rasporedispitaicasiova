<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Profil Profesora</title>
    <link rel="stylesheet" href="./../assets/css/profesor_profile.css">
    <link rel="stylesheet" href="../assets/css/admin.css">
    <link rel="stylesheet" href="../assets/css/style.css">

</head>
<body>
    <header>
        <nav>
            <ul>
                <li><a href="?page=profesori">Profesori</a></li>
                <li><a href="?page=predmeti">Predmeti</a></li>
                <li><a href="?page=dogadjaji">Događaji</a></li>
                <li><a href="?page=sale">Sale</a></li>
                <li><a href="?logout=true">Rasporedi</a></li>
            </ul>
        </nav>
    </header>

    <div class="container">
        <div class="profile-form-container">
            <h2>Profil Profesora</h2>

            <form class="profile-form">
                <label for="name">Ime i prezime:</label>
                <input type="text" id="name" name="name" value="Tijana Marković" required>

                <label for="email">Email:</label>
                <input type="email" id="email" name="email" value="tijana.vujicic@unimediteran.net" required>

                <button class="action-button edit-button" type="submit">Sačuvaj izmjene</button>
            </form>

            <button id="openModalBtn">Promijeni password</button>
        </div>
    </div>

    <div id="passwordModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeModal">&times;</span>
            <div class="modal-header">Promjena passworda</div>

            <form>
                <label for="oldPassword">Stari password:</label>
                <input type="password" id="oldPassword" required>
                <a href="#">Zaboravili ste password?</a>

                <label for="newPassword">Novi password:</label>
                <input type="password" id="newPassword" required>

                <label for="confirmPassword">Potvrdi novi password:</label>
                <input type="password" id="confirmPassword" required>

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