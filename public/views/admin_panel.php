<?php
session_start();


?>

    <!DOCTYPE html>
    <html lang="sr">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Admin Panel - Raspored Ispita</title>
        <link rel="stylesheet" href="assets/css/style.css">
    </head>
    <body>

    <header>
        <h1>Admin Panel</h1>
        <nav>
            <ul>
                <li><a href="?page=profesori">Profesori</a></li>
                <li><a href="?page=predmeti">Predmeti</a></li>
                <li><a href="?page=dogadjaji">Događaji</a></li>
                <li><a href="?page=sale">Sale</a></li>
                <li><a href="?logout=true">Odjava</a></li>
            </ul>
        </nav>
    </header>

    <main>
        <?php
        $page = isset($_GET['page']) ? $_GET['page'] : 'pocetna';

        switch ($page) {
            case 'profesori':
                echo "<h2>Upravljanje Profesorima</h2>";
                echo "<button>+ Dodaj Profesora</button>";
                echo "<table border='1' cellpadding='5'>
                    <tr><th>ID</th><th>Ime</th><th>Prezime</th><th>Akcije</th></tr>
                    <tr><td>1</td><td>Marko</td><td>Marković</td><td><button>Uredi</button> <button>Obriši</button></td></tr>
                  </table>";
                break;

            case 'predmeti':
                echo "<h2>Upravljanje Predmetima</h2>";
                echo "<button>+ Dodaj Predmet</button>";
                echo "<table border='1' cellpadding='5'>
                    <tr><th>ID</th><th>Naziv</th><th>Profesor</th><th>Akcije</th></tr>
                    <tr><td>1</td><td>Matematika</td><td>Marko Marković</td><td><button>Uredi</button> <button>Obriši</button></td></tr>
                  </table>";
                break;

            case 'dogadjaji':
                echo "<h2>Upravljanje Događajima</h2>";
                echo "<button>+ Dodaj Događaj</button>";
                echo "<table border='1' cellpadding='5'>
                    <tr><th>ID</th><th>Naziv</th><th>Datum</th><th>Sala</th><th>Akcije</th></tr>
                    <tr><td>1</td><td>Ispit iz Matematike</td><td>2025-06-20</td><td>101</td><td><button>Uredi</button> <button>Obriši</button></td></tr>
                  </table>";
                break;

            case 'sale':
                echo "<h2>Upravljanje Salama</h2>";
                echo "<button>+ Dodaj Salu</button>";
                echo "<table border='1' cellpadding='5'>
                    <tr><th>ID</th><th>Naziv</th><th>Kapacitet</th><th>Akcije</th></tr>
                    <tr><td>1</td><td>Aula 1</td><td>50</td><td><button>Uredi</button> <button>Obriši</button></td></tr>
                  </table>";
                break;

            default:
                echo "<h2>Dobrodošli u Admin Panel</h2>";
                echo "<p>Odaberite sekciju iz menija iznad.</p>";
        }
        ?>
    </main>

    <footer>
        <p>© <?php echo date('Y'); ?> Raspored Ispita | Admin Panel</p>
    </footer>

    </body>
    </html>
<?php
