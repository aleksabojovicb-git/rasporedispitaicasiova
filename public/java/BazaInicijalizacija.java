import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BazaInicijalizacija {
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/tvoja_baza";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = ""; // Tvoja lozinka
    private static Connection konekcija;
    
    // ========================================================================
    // INICIJALIZUJ KONEKCIJU
    // ========================================================================
    
    public static void inicijalizujBazu() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            konekcija = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("✓ Konekcija sa bazom je uspešna");
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("✗ Greška pri konekciji: " + e.getMessage());
        }
    }
    
    // ========================================================================
    // 1. UČITAJ SVE PROFESORE
    // ========================================================================
    
    public static List<EventValidationService.profesor> ucitajSveProfesore() {
        List<EventValidationService.profesor> profesori = new ArrayList<>();
        
        String sql = "SELECT id, ime, mail FROM PROFESORI ORDER BY ime";
        
        try (Statement stmt = konekcija.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String ime = rs.getString("ime");
                String mail = rs.getString("mail");
                
                profesori.add(new EventValidationService().new profesor(id, ime, mail));
            }
            
            System.out.println("✓ Učitano " + profesori.size() + " profesora");
            
        } catch (SQLException e) {
            System.out.println("✗ Greška pri učitavanju profesora: " + e.getMessage());
        }
        
        return profesori;
    }
    
    // ========================================================================
    // 2. UČITAJ SVE UCIONICE
    // ========================================================================
    
    public static List<EventValidationService.ucionica> ucitajSveUcionice() {
        List<EventValidationService.ucionica> ucionice = new ArrayList<>();
        
        String sql = "SELECT id, naziv, kapacitet, imaRacunare FROM UCIONICE ORDER BY naziv";
        
        try (Statement stmt = konekcija.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String naziv = rs.getString("naziv");
                int kapacitet = rs.getInt("kapacitet");
                boolean imaRacunare = rs.getBoolean("imaRacunare");
                
                ucionice.add(new EventValidationService().new ucionica(id, naziv, kapacitet, imaRacunare));
            }
            
            System.out.println("✓ Učitano " + ucionice.size() + " učionica");
            
        } catch (SQLException e) {
            System.out.println("✗ Greška pri učitavanju učionica: " + e.getMessage());
        }
        
        return ucionice;
    }
    
    // ========================================================================
    // 3. UČITAJ SVE PREDMETE
    // ========================================================================
    
    public static List<EventValidationService.predmet> ucitajSvePredmete(
            List<EventValidationService.profesor> profesori) {
        
        List<EventValidationService.predmet> predmeti = new ArrayList<>();
        
        String sql = "SELECT " +
                "p.id, p.naziv, p.kod, p.profesor_id, p.asistent_id, p.godinaStudija, p.aktivan " +
                "FROM PREDMETI p ORDER BY p.naziv";
        
        try (Statement stmt = konekcija.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String naziv = rs.getString("naziv");
                String kod = rs.getString("kod");
                int profesor_id = rs.getInt("profesor_id");
                int asistent_id = rs.getInt("asistent_id");
                int godinaStudija = rs.getInt("godinaStudija");
                boolean aktivan = rs.getBoolean("aktivan");
                
                // Pronađi profesora iz liste
                EventValidationService.profesor profesor = pronađiProfesora(profesor_id, profesori);
                
                // Pronađi asistenta (ako postoji)
                EventValidationService.profesor asistent = null;
                if (asistent_id > 0) {
                    asistent = pronađiProfesora(asistent_id, profesori);
                }
                
                predmeti.add(new EventValidationService().new predmet(
                        id, naziv, kod, profesor, asistent, godinaStudija, aktivan
                ));
            }
            
            System.out.println("✓ Učitano " + predmeti.size() + " predmeta");
            
        } catch (SQLException e) {
            System.out.println("✗ Greška pri učitavanju predmeta: " + e.getMessage());
        }
        
        return predmeti;
    }
    
    // ========================================================================
    // 4. UČITAJ SVE DOGADJAJE
    // ========================================================================
    
    public static List<EventValidationService.dogadjaj> ucitajSveDogadjaje(
            List<EventValidationService.predmet> predmeti,
            List<EventValidationService.profesor> profesori,
            List<EventValidationService.ucionica> ucionice) {
        
        List<EventValidationService.dogadjaj> dogadjaji = new ArrayList<>();
        
        String sql = "SELECT " +
                "d.nedeljaUSemestru, d.predmet_id, d.ucionica_id, " +
                "d.profesor_id, d.asistent_id, d.jeVezba, d.jeTest, d.jeAktivan " +
                "FROM DOGADJAJI d ORDER BY d.nedeljaUSemestru, d.danUNedelji";
        
        try (Statement stmt = konekcija.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                int nedelja = rs.getInt("nedeljaUSemestru");
                int predmet_id = rs.getInt("predmet_id");
                int ucionica_id = rs.getInt("ucionica_id");
                int profesor_id = rs.getInt("profesor_id");
                int asistent_id = rs.getInt("asistent_id");
                boolean vezba = rs.getBoolean("jeVezba");
                boolean test = rs.getBoolean("jeTest");
                boolean aktivan = rs.getBoolean("jeAktivan");
                
                // Pronađi predmet
                EventValidationService.predmet predmet = pronađiPredmet(predmet_id, predmeti);
                
                // Pronađi učionicu
                EventValidationService.ucionica ucionica = pronađiUcionicu(ucionica_id, ucionice);
                
                // Pronađi profesora
                EventValidationService.profesor profesor = pronađiProfesora(profesor_id, profesori);
                
                // Pronađi asistenta
                EventValidationService.profesor asistent = null;
                if (asistent_id > 0) {
                    asistent = pronađiProfesora(asistent_id, profesori);
                }
                
                dogadjaji.add(new EventValidationService().new dogadjaj(
                    nedelja, 0, 8, 10,  // danUNedelji, terminPocetka, terminKraja (dodaj iz baze, ako postoji..)
                    predmet, ucionica, profesor, asistent, vezba, test, aktivan
                ));

            }
            
            System.out.println("✓ Učitano " + dogadjaji.size() + " dogadjaja");
            
        } catch (SQLException e) {
            System.out.println("✗ Greška pri učitavanju dogadjaja: " + e.getMessage());
        }
        
        return dogadjaji;
    }
    
    // ========================================================================
    // POMOĆNE FUNKCIJE ZA PRONALAŽENJE
    // ========================================================================
    
    private static EventValidationService.profesor pronađiProfesora(
            int id, List<EventValidationService.profesor> profesori) {
        for (EventValidationService.profesor p : profesori) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }
    
    private static EventValidationService.predmet pronađiPredmet(
            int id, List<EventValidationService.predmet> predmeti) {
        for (EventValidationService.predmet p : predmeti) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }
    
    private static EventValidationService.ucionica pronađiUcionicu(
            int id, List<EventValidationService.ucionica> ucionice) {
        for (EventValidationService.ucionica u : ucionice) {
            if (u.getId() == id) {
                return u;
            }
        }
        return null;
    }
    
    // ========================================================================
    // ZATVARANJE KONEKCIJE
    // ========================================================================
    
    public static void zatvoriBazu() {
        try {
            if (konekcija != null && !konekcija.isClosed()) {
                konekcija.close();
                System.out.println("✓ Konekcija sa bazom je zatvorena");
            }
        } catch (SQLException e) {
            System.out.println("✗ Greška pri zatvaranju konekcije: " + e.getMessage());
        }
    }

    // ========================================================================
// POPUNI VARIJABLE U EventValidationService
// ========================================================================

public static void popuniEventValidationService(EventValidationService servis) {
    System.out.println("\n=== Popunjavanje EventValidationService iz baze ===");
    
    // 1. Učitaj profesore
    List<EventValidationService.profesor> profesori = ucitajSveProfesore();
    EventValidationService.setProfesori(profesori);
    
    // 2. Učitaj učionice
    List<EventValidationService.ucionica> ucionice = ucitajSveUcionice();
    EventValidationService.setUcionice(ucionice);
    
    // 3. Učitaj predmete
    List<EventValidationService.predmet> predmeti = ucitajSvePredmete(profesori);
    EventValidationService.setPredmeti(predmeti);
    
    // 4. Učitaj dogadjaje
    List<EventValidationService.dogadjaj> dogadjaji = ucitajSveDogadjaje(predmeti, profesori, ucionice);
    EventValidationService.setDogadjaji(dogadjaji);
    
    System.out.println("✓ EventValidationService je popunjen sa podacima iz baze!\n");
}

// Alternativa - Ako želiš da popuniš statičke varijable direktno:
public static void popuniStatickeVarijable() {
    System.out.println("\n=== Popunjavanje statičkih varijabli EventValidationService ===");
    
    // Potrebno da dodaš getter/setter metode u EventValidationService
    EventValidationService.setProfesori(ucitajSveProfesore());
    EventValidationService.setUcionice(ucitajSveUcionice());
    EventValidationService.setPredmeti(ucitajSvePredmete(EventValidationService.getProfesori()));
    EventValidationService.setDogadjaji(ucitajSveDogadjaje(
        EventValidationService.getPredmeti(),
        EventValidationService.getProfesori(),
        EventValidationService.getUcionice()
    ));
    
    System.out.println("✓ Statičke varijable su popunjene!\n");
}

}
