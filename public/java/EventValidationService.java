import java.sql.*;
import java.time.*;
import java.util.*;

public class EventValidationService {
    
    private Connection conn;
    private Map<Integer, Predmet> predmeti;
    private Map<Integer, Sala> sale;
    private Map<Integer, Profesor> profesori;
    private List<Termin> termini;
    private List<Praznik> praznici;
    
    public EventValidationService(Connection connection) {
        this.conn = connection;
        this.predmeti = new HashMap<>();
        this.sale = new HashMap<>();
        this.profesori = new HashMap<>();
        this.termini = new ArrayList<>();
        this.praznici = new ArrayList<>();
        ucitajPodatkeIzBaze();
    }
    
    private void ucitajPodatkeIzBaze() {
        try {
            ucitajPredmete();
            ucitajSale();
            ucitajProfesore();
            ucitajTermine();
            ucitajPraznike();
        } catch (SQLException e) {
            System.err.println("Greska pri ucitavanju podataka: " + e.getMessage());
        }
    }
    
    private void ucitajPredmete() throws SQLException {
        String query = "SELECT * FROM predmet";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        
        int count = 0;
        while (rs.next()) {
            Predmet p = new Predmet();
            p.id = rs.getInt("id_predmet");
            p.naziv = rs.getString("naziv");
            p.semestar = rs.getInt("semestar");
            p.fondPredavanja = rs.getInt("fond_predavanja");
            p.fondVjezbi = rs.getInt("fond_vjezbi");
            p.vrstaOpreme = rs.getString("vrsta_opreme");
            p.brojStudenata = rs.getInt("broj_studenata");
            predmeti.put(p.id, p);
            count++;
        }
        
        rs.close();
        stmt.close();
        System.out.println("Ucitano predmeta: " + count);
    }
    
    private void ucitajSale() throws SQLException {
        String query = "SELECT * FROM sala";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        
        int count = 0;
        while (rs.next()) {
            Sala s = new Sala();
            s.id = rs.getInt("id_sala");
            s.naziv = rs.getString("naziv");
            s.kapacitet = rs.getInt("kapacitet");
            s.vrstaOpreme = rs.getString("vrsta_opreme");
            s.tipSale = rs.getString("tip_sale");
            sale.put(s.id, s);
            count++;
        }
        
        rs.close();
        stmt.close();
        System.out.println("Ucitano sala: " + count);
    }
    
    private void ucitajProfesore() throws SQLException {
        String query = "SELECT * FROM profesor";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        
        int count = 0;
        while (rs.next()) {
            Profesor prof = new Profesor();
            prof.id = rs.getInt("id_profesor");
            prof.ime = rs.getString("ime");
            prof.prezime = rs.getString("prezime");
            prof.email = rs.getString("email");
            profesori.put(prof.id, prof);
            count++;
        }
        
        rs.close();
        stmt.close();
        System.out.println("Ucitano profesora: " + count);
    }
    
    private void ucitajTermine() throws SQLException {
        String query = "SELECT * FROM termin";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        
        int count = 0;
        while (rs.next()) {
            Termin t = new Termin();
            t.id = rs.getInt("id_termin");
            t.idPredmet = rs.getInt("id_predmet");
            t.idSala = rs.getInt("id_sala");
            t.idProfesor = rs.getInt("id_profesor");
            t.dan = rs.getString("dan");
            t.vremeOd = rs.getTime("vreme_od").toLocalTime();
            t.vremeDo = rs.getTime("vreme_do").toLocalTime();
            t.tipTermina = rs.getString("tip_termina");
            t.datum = rs.getDate("datum") != null ? rs.getDate("datum").toLocalDate() : null;
            termini.add(t);
            count++;
        }
        
        rs.close();
        stmt.close();
        System.out.println("Ucitano termina: " + count);
    }
    
    private void ucitajPraznike() throws SQLException {
        String query = "SELECT * FROM praznik";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        
        int count = 0;
        while (rs.next()) {
            Praznik p = new Praznik();
            p.id = rs.getInt("id_praznik");
            p.naziv = rs.getString("naziv");
            p.datum = rs.getDate("datum").toLocalDate();
            praznici.add(p);
            count++;
        }
        
        rs.close();
        stmt.close();
        System.out.println("Ucitano praznika: " + count);
    }
    
    public String dodajPredavanje(int idPredmet, int idSala, int idProfesor, String dan, 
                                  String vremeOd, String vremeDo) {
        try {
            Predmet predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Predmet ne postoji";
            }
            
            Sala sala = sale.get(idSala);
            if (sala == null) {
                return "GRESKA: Sala ne postoji";
            }
            
            Profesor profesor = profesori.get(idProfesor);
            if (profesor == null) {
                return "GRESKA: Profesor ne postoji";
            }
            
            LocalTime pocetak = LocalTime.parse(vremeOd);
            LocalTime kraj = LocalTime.parse(vremeDo);
            
            if (sala.kapacitet < predmet.brojStudenata) {
                return "GRESKA: Sala nema dovoljan kapacitet za broj studenata";
            }
            
            if (predmet.vrstaOpreme != null && !predmet.vrstaOpreme.isEmpty()) {
                if (sala.vrstaOpreme == null || !sala.vrstaOpreme.contains(predmet.vrstaOpreme)) {
                    return "GRESKA: Sala nema potrebnu opremu: " + predmet.vrstaOpreme;
                }
            }
            
            if (!sala.tipSale.equals("predavaliste") && !sala.tipSale.equals("sve")) {
                return "GRESKA: Sala nije pogodna za predavanja";
            }
            
            for (Termin t : termini) {
                if (t.dan.equals(dan) && t.idSala == idSala) {
                    if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                          pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                        return "GRESKA: Sala je zauzeta u tom terminu";
                    }
                }
            }
            
            for (Termin t : termini) {
                if (t.dan.equals(dan) && t.idProfesor == idProfesor) {
                    if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                          pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                        return "GRESKA: Profesor je zauzet u tom terminu";
                    }
                }
            }
            
            String insert = "INSERT INTO termin (id_predmet, id_sala, id_profesor, dan, vreme_od, vreme_do, tip_termina) " +
                          "VALUES (?, ?, ?, ?, ?, ?, 'predavanje')";
            PreparedStatement pstmt = conn.prepareStatement(insert);
            pstmt.setInt(1, idPredmet);
            pstmt.setInt(2, idSala);
            pstmt.setInt(3, idProfesor);
            pstmt.setString(4, dan);
            pstmt.setTime(5, Time.valueOf(pocetak));
            pstmt.setTime(6, Time.valueOf(kraj));
            pstmt.executeUpdate();
            pstmt.close();
            
            ucitajTermine();
            
            return "OK: Predavanje uspjesno dodato";
            
        } catch (Exception e) {
            return "GRESKA: " + e.getMessage();
        }
    }
    
    public String dodajVjezbe(int idPredmet, int idSala, int idProfesor, String dan, 
                             String vremeOd, String vremeDo) {
        try {
            Predmet predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Predmet ne postoji";
            }
            
            Sala sala = sale.get(idSala);
            if (sala == null) {
                return "GRESKA: Sala ne postoji";
            }
            
            Profesor profesor = profesori.get(idProfesor);
            if (profesor == null) {
                return "GRESKA: Profesor ne postoji";
            }
            
            LocalTime pocetak = LocalTime.parse(vremeOd);
            LocalTime kraj = LocalTime.parse(vremeDo);
            
            if (sala.kapacitet < predmet.brojStudenata) {
                return "GRESKA: Sala nema dovoljan kapacitet za broj studenata";
            }
            
            if (predmet.vrstaOpreme != null && !predmet.vrstaOpreme.isEmpty()) {
                if (sala.vrstaOpreme == null || !sala.vrstaOpreme.contains(predmet.vrstaOpreme)) {
                    return "GRESKA: Sala nema potrebnu opremu: " + predmet.vrstaOpreme;
                }
            }
            
            if (!sala.tipSale.equals("vjezbe") && !sala.tipSale.equals("sve")) {
                return "GRESKA: Sala nije pogodna za vjezbe";
            }
            
            for (Termin t : termini) {
                if (t.dan.equals(dan) && t.idSala == idSala) {
                    if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                          pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                        return "GRESKA: Sala je zauzeta u tom terminu";
                    }
                }
            }
            
            for (Termin t : termini) {
                if (t.dan.equals(dan) && t.idProfesor == idProfesor) {
                    if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                          pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                        return "GRESKA: Profesor je zauzet u tom terminu";
                    }
                }
            }
            
            String insert = "INSERT INTO termin (id_predmet, id_sala, id_profesor, dan, vreme_od, vreme_do, tip_termina) " +
                          "VALUES (?, ?, ?, ?, ?, ?, 'vjezbe')";
            PreparedStatement pstmt = conn.prepareStatement(insert);
            pstmt.setInt(1, idPredmet);
            pstmt.setInt(2, idSala);
            pstmt.setInt(3, idProfesor);
            pstmt.setString(4, dan);
            pstmt.setTime(5, Time.valueOf(pocetak));
            pstmt.setTime(6, Time.valueOf(kraj));
            pstmt.executeUpdate();
            pstmt.close();
            
            ucitajTermine();
            
            return "OK: Vjezbe uspjesno dodate";
            
        } catch (Exception e) {
            return "GRESKA: " + e.getMessage();
        }
    }
    
    public String dodajKolokvijum(int idPredmet, int idSala, int idProfesor, int idDezurni,
                                  String datum, String vremeOd, String vremeDo) {
        try {
            Predmet predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Predmet ne postoji";
            }
            
            Sala sala = sale.get(idSala);
            if (sala == null) {
                return "GRESKA: Sala ne postoji";
            }
            
            Profesor profesor = profesori.get(idProfesor);
            if (profesor == null) {
                return "GRESKA: Profesor ne postoji";
            }
            
            Profesor dezurni = profesori.get(idDezurni);
            if (dezurni == null) {
                return "GRESKA: Dezurni profesor ne postoji";
            }
            
            LocalDate datumKolokvijuma = LocalDate.parse(datum);
            LocalTime pocetak = LocalTime.parse(vremeOd);
            LocalTime kraj = LocalTime.parse(vremeDo);
            
            if (datumKolokvijuma.getDayOfWeek() == DayOfWeek.SUNDAY) {
                return "GRESKA: Kolokvijum ne moze biti u nedjelju";
            }
            
            for (Praznik p : praznici) {
                if (p.datum.equals(datumKolokvijuma)) {
                    return "GRESKA: Ne moze se zakazati kolokvijum za praznik: " + p.naziv;
                }
            }
            
            if (!sala.tipSale.equals("vjezbe") && !sala.tipSale.equals("sve")) {
                return "GRESKA: Kolokvijum mora biti u sali za vjezbe";
            }
            
            if (sala.kapacitet < predmet.brojStudenata) {
                return "GRESKA: Sala nema dovoljan kapacitet";
            }
            
            for (Termin t : termini) {
                if (t.datum != null && t.datum.equals(datumKolokvijuma) && t.idSala == idSala) {
                    if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                          pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                        return "GRESKA: Sala je zauzeta u tom terminu";
                    }
                }
            }
            
            for (Termin t : termini) {
                if (t.datum != null && t.datum.equals(datumKolokvijuma) && t.idProfesor == idProfesor) {
                    if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                          pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                        return "GRESKA: Profesor je zauzet u tom terminu";
                    }
                }
            }
            
            int brojKolokvijumaTeNedjelje = 0;
            LocalDate pocetakNedjelje = datumKolokvijuma.with(DayOfWeek.MONDAY);
            LocalDate krajNedjelje = datumKolokvijuma.with(DayOfWeek.SUNDAY);
            
            for (Termin t : termini) {
                if (t.tipTermina.equals("kolokvijum") && t.datum != null) {
                    if (!t.datum.isBefore(pocetakNedjelje) && !t.datum.isAfter(krajNedjelje)) {
                        brojKolokvijumaTeNedjelje++;
                    }
                }
            }
            
            if (brojKolokvijumaTeNedjelje >= 2) {
                return "GRESKA: U jednoj nedjelji mogu biti maksimalno 2 kolokvijuma";
            }
            
            int brojDezurstava = 0;
            for (Termin t : termini) {
                if (t.tipTermina.contains("kolokvijum") && t.datum != null) {
                    if (!t.datum.isBefore(pocetakNedjelje) && !t.datum.isAfter(krajNedjelje)) {
                        String query = "SELECT id_dezurni FROM kolokvijum WHERE id_termin = ?";
                        PreparedStatement ps = conn.prepareStatement(query);
                        ps.setInt(1, t.id);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next() && rs.getInt("id_dezurni") == idDezurni) {
                            brojDezurstava++;
                        }
                        rs.close();
                        ps.close();
                    }
                }
            }
            
            if (brojDezurstava >= 2) {
                return "GRESKA: Dezurni profesor vec ima 2 dezurstva te nedjelje";
            }
            
            String insertTermin = "INSERT INTO termin (id_predmet, id_sala, id_profesor, datum, vreme_od, vreme_do, tip_termina) " +
                                "VALUES (?, ?, ?, ?, ?, ?, 'kolokvijum')";
            PreparedStatement pstmt = conn.prepareStatement(insertTermin, Statement.RETURN_GENERATED_KEYS);
            pstmt.setInt(1, idPredmet);
            pstmt.setInt(2, idSala);
            pstmt.setInt(3, idProfesor);
            pstmt.setDate(4, java.sql.Date.valueOf(datumKolokvijuma));
            pstmt.setTime(5, Time.valueOf(pocetak));
            pstmt.setTime(6, Time.valueOf(kraj));
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            int idTermin = 0;
            if (rs.next()) {
                idTermin = rs.getInt(1);
            }
            rs.close();
            pstmt.close();
            
            String insertKolokvijum = "INSERT INTO kolokvijum (id_termin, id_dezurni) VALUES (?, ?)";
            PreparedStatement pstmt2 = conn.prepareStatement(insertKolokvijum);
            pstmt2.setInt(1, idTermin);
            pstmt2.setInt(2, idDezurni);
            pstmt2.executeUpdate();
            pstmt2.close();
            
            ucitajTermine();
            
            return "OK: Kolokvijum uspjesno dodat";
            
        } catch (Exception e) {
            return "GRESKA: " + e.getMessage();
        }
    }
    
    public String dodajIspit(int idPredmet, int idSala, int idProfesor, String datum, 
                            String vremeOd, String vremeDo, String tipIspita) {
        try {
            Predmet predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Predmet ne postoji";
            }
            
            Sala sala = sale.get(idSala);
            if (sala == null) {
                return "GRESKA: Sala ne postoji";
            }
            
            Profesor profesor = profesori.get(idProfesor);
            if (profesor == null) {
                return "GRESKA: Profesor ne postoji";
            }
            
            LocalDate datumIspita = LocalDate.parse(datum);
            LocalTime pocetak = LocalTime.parse(vremeOd);
            LocalTime kraj = LocalTime.parse(vremeDo);
            
            if (datumIspita.getDayOfWeek() == DayOfWeek.SUNDAY) {
                return "GRESKA: Ispit ne moze biti u nedjelju";
            }
            
            for (Praznik p : praznici) {
                if (p.datum.equals(datumIspita)) {
                    return "GRESKA: Ne moze se zakazati ispit za praznik: " + p.naziv;
                }
            }
            
            if (sala.kapacitet < predmet.brojStudenata) {
                return "GRESKA: Sala nema dovoljan kapacitet";
            }
            
            if (predmet.vrstaOpreme != null && !predmet.vrstaOpreme.isEmpty()) {
                if (sala.vrstaOpreme == null || !sala.vrstaOpreme.contains(predmet.vrstaOpreme)) {
                    return "GRESKA: Sala nema potrebnu opremu: " + predmet.vrstaOpreme;
                }
            }
            
            for (Termin t : termini) {
                if (t.datum != null && t.datum.equals(datumIspita) && t.idSala == idSala) {
                    if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                          pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                        return "GRESKA: Sala je zauzeta u tom terminu";
                    }
                }
            }
            
            for (Termin t : termini) {
                if (t.datum != null && t.datum.equals(datumIspita) && t.idProfesor == idProfesor) {
                    if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                          pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                        return "GRESKA: Profesor je zauzet u tom terminu";
                    }
                }
            }
            
            String insertTermin = "INSERT INTO termin (id_predmet, id_sala, id_profesor, datum, vreme_od, vreme_do, tip_termina) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(insertTermin, Statement.RETURN_GENERATED_KEYS);
            pstmt.setInt(1, idPredmet);
            pstmt.setInt(2, idSala);
            pstmt.setInt(3, idProfesor);
            pstmt.setDate(4, java.sql.Date.valueOf(datumIspita));
            pstmt.setTime(5, Time.valueOf(pocetak));
            pstmt.setTime(6, Time.valueOf(kraj));
            pstmt.setString(7, tipIspita);
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            int idTermin = 0;
            if (rs.next()) {
                idTermin = rs.getInt(1);
            }
            rs.close();
            pstmt.close();
            
            ucitajTermine();
            
            return "OK: Ispit uspjesno dodat";
            
        } catch (Exception e) {
            return "GRESKA: " + e.getMessage();
        }
    }
    
    public String generisiRasporedPredavanja(int idPredmet) {
        try {
            Predmet predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Predmet ne postoji";
            }
            
            if (predmet.fondPredavanja <= 0) {
                return "GRESKA: Predmet nema fond predavanja";
            }
            
            String queryProf = "SELECT id_profesor FROM predmet_profesor WHERE id_predmet = ? AND tip = 'predavanje'";
            PreparedStatement ps = conn.prepareStatement(queryProf);
            ps.setInt(1, idPredmet);
            ResultSet rs = ps.executeQuery();
            
            int idProfesor = 0;
            if (rs.next()) {
                idProfesor = rs.getInt("id_profesor");
            } else {
                rs.close();
                ps.close();
                return "GRESKA: Predmet nema dodijeljenog profesora za predavanja";
            }
            rs.close();
            ps.close();
            
            String queryPref = "SELECT * FROM profesor_preferencije WHERE id_profesor = ?";
            PreparedStatement ps2 = conn.prepareStatement(queryPref);
            ps2.setInt(1, idProfesor);
            ResultSet rs2 = ps2.executeQuery();
            
            List<String> prefDani = new ArrayList<>();
            Map<String, String> prefVremena = new HashMap<>();
            
            while (rs2.next()) {
                String dan = rs2.getString("dan");
                prefDani.add(dan);
                prefVremena.put(dan, rs2.getTime("vreme_od").toString());
            }
            rs2.close();
            ps2.close();
            
            if (prefDani.isEmpty()) {
                prefDani.add("ponedeljak");
                prefDani.add("utorak");
                prefDani.add("srijeda");
            }
            
            List<Sala> pogodneSale = new ArrayList<>();
            for (Sala s : sale.values()) {
                if (s.tipSale.equals("predavaliste") || s.tipSale.equals("sve")) {
                    if (s.kapacitet >= predmet.brojStudenata) {
                        if (predmet.vrstaOpreme == null || predmet.vrstaOpreme.isEmpty() ||
                            (s.vrstaOpreme != null && s.vrstaOpreme.contains(predmet.vrstaOpreme))) {
                            pogodneSale.add(s);
                        }
                    }
                }
            }
            
            if (pogodneSale.isEmpty()) {
                return "GRESKA: Nema pogodnih sala za predavanja";
            }
            
            int dodanoTermina = 0;
            int potrebnoTermina = predmet.fondPredavanja;
            
            for (String dan : prefDani) {
                if (dodanoTermina >= potrebnoTermina) {
                    break;
                }
                
                for (Sala sala : pogodneSale) {
                    if (dodanoTermina >= potrebnoTermina) {
                        break;
                    }
                    
                    String vremeOd = prefVremena.getOrDefault(dan, "09:00:00");
                    LocalTime pocetak = LocalTime.parse(vremeOd);
                    LocalTime kraj = pocetak.plusHours(1);
                    
                    boolean slobodan = true;
                    for (Termin t : termini) {
                        if (t.dan.equals(dan) && t.idSala == sala.id) {
                            if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                                  pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                                slobodan = false;
                                break;
                            }
                        }
                    }
                    
                    if (!slobodan) {
                        continue;
                    }
                    
                    for (Termin t : termini) {
                        if (t.dan.equals(dan) && t.idProfesor == idProfesor) {
                            if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                                  pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                                slobodan = false;
                                break;
                            }
                        }
                    }
                    
                    if (slobodan) {
                        String insert = "INSERT INTO termin (id_predmet, id_sala, id_profesor, dan, vreme_od, vreme_do, tip_termina) " +
                                      "VALUES (?, ?, ?, ?, ?, ?, 'predavanje')";
                        PreparedStatement pstmt = conn.prepareStatement(insert);
                        pstmt.setInt(1, idPredmet);
                        pstmt.setInt(2, sala.id);
                        pstmt.setInt(3, idProfesor);
                        pstmt.setString(4, dan);
                        pstmt.setTime(5, Time.valueOf(pocetak));
                        pstmt.setTime(6, Time.valueOf(kraj));
                        pstmt.executeUpdate();
                        pstmt.close();
                        
                        dodanoTermina++;
                    }
                }
            }
            
            ucitajTermine();
            
            if (dodanoTermina < potrebnoTermina) {
                return "UPOZORENJE: Generisano " + dodanoTermina + " od " + potrebnoTermina + " potrebnih termina";
            }
            
            return "OK: Automatski generisan raspored predavanja (" + dodanoTermina + " termina)";
            
        } catch (Exception e) {
            return "GRESKA: " + e.getMessage();
        }
    }
    
    public String generisiRasporedVjezbi(int idPredmet) {
        try {
            Predmet predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Predmet ne postoji";
            }
            
            if (predmet.fondVjezbi <= 0) {
                return "GRESKA: Predmet nema fond vjezbi";
            }
            
            String queryProf = "SELECT id_profesor FROM predmet_profesor WHERE id_predmet = ? AND tip = 'vjezbe'";
            PreparedStatement ps = conn.prepareStatement(queryProf);
            ps.setInt(1, idPredmet);
            ResultSet rs = ps.executeQuery();
            
            int idProfesor = 0;
            if (rs.next()) {
                idProfesor = rs.getInt("id_profesor");
            } else {
                rs.close();
                ps.close();
                return "GRESKA: Predmet nema dodijeljenog asistenta za vjezbe";
            }
            rs.close();
            ps.close();
            
            String queryPref = "SELECT * FROM profesor_preferencije WHERE id_profesor = ?";
            PreparedStatement ps2 = conn.prepareStatement(queryPref);
            ps2.setInt(1, idProfesor);
            ResultSet rs2 = ps2.executeQuery();
            
            List<String> prefDani = new ArrayList<>();
            Map<String, String> prefVremena = new HashMap<>();
            
            while (rs2.next()) {
                String dan = rs2.getString("dan");
                prefDani.add(dan);
                prefVremena.put(dan, rs2.getTime("vreme_od").toString());
            }
            rs2.close();
            ps2.close();
            
            if (prefDani.isEmpty()) {
                prefDani.add("utorak");
                prefDani.add("cetvrtak");
            }
            
            List<Sala> pogodneSale = new ArrayList<>();
            for (Sala s : sale.values()) {
                if (s.tipSale.equals("vjezbe") || s.tipSale.equals("sve")) {
                    if (s.kapacitet >= predmet.brojStudenata) {
                        if (predmet.vrstaOpreme == null || predmet.vrstaOpreme.isEmpty() ||
                            (s.vrstaOpreme != null && s.vrstaOpreme.contains(predmet.vrstaOpreme))) {
                            pogodneSale.add(s);
                        }
                    }
                }
            }
            
            if (pogodneSale.isEmpty()) {
                return "GRESKA: Nema pogodnih sala za vjezbe";
            }
            
            int dodanoTermina = 0;
            int potrebnoTermina = predmet.fondVjezbi;
            
            for (String dan : prefDani) {
                if (dodanoTermina >= potrebnoTermina) {
                    break;
                }
                
                for (Sala sala : pogodneSale) {
                    if (dodanoTermina >= potrebnoTermina) {
                        break;
                    }
                    
                    String vremeOd = prefVremena.getOrDefault(dan, "10:00:00");
                    LocalTime pocetak = LocalTime.parse(vremeOd);
                    LocalTime kraj = pocetak.plusHours(1);
                    
                    boolean slobodan = true;
                    for (Termin t : termini) {
                        if (t.dan.equals(dan) && t.idSala == sala.id) {
                            if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                                  pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                                slobodan = false;
                                break;
                            }
                        }
                    }
                    
                    if (!slobodan) {
                        continue;
                    }
                    
                    for (Termin t : termini) {
                        if (t.dan.equals(dan) && t.idProfesor == idProfesor) {
                            if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) || 
                                  pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) {
                                slobodan = false;
                                break;
                            }
                        }
                    }
                    
                    if (slobodan) {
                        String insert = "INSERT INTO termin (id_predmet, id_sala, id_profesor, dan, vreme_od, vreme_do, tip_termina) " +
                                      "VALUES (?, ?, ?, ?, ?, ?, 'vjezbe')";
                        PreparedStatement pstmt = conn.prepareStatement(insert);
                        pstmt.setInt(1, idPredmet);
                        pstmt.setInt(2, sala.id);
                        pstmt.setInt(3, idProfesor);
                        pstmt.setString(4, dan);
                        pstmt.setTime(5, Time.valueOf(pocetak));
                        pstmt.setTime(6, Time.valueOf(kraj));
                        pstmt.executeUpdate();
                        pstmt.close();
                        
                        dodanoTermina++;
                    }
                }
            }
            
            ucitajTermine();
            
            if (dodanoTermina < potrebnoTermina) {
                return "UPOZORENJE: Generisano " + dodanoTermina + " od " + potrebnoTermina + " potrebnih termina";
            }
            
            return "OK: Automatski generisan raspored vjezbi (" + dodanoTermina + " termina)";
            
        } catch (Exception e) {
            return "GRESKA: " + e.getMessage();
        }
    }
    
    class Predmet {
        int id;
        String naziv;
        int semestar;
        int fondPredavanja;
        int fondVjezbi;
        String vrstaOpreme;
        int brojStudenata;
    }
    
    class Sala {
        int id;
        String naziv;
        int kapacitet;
        String vrstaOpreme;
        String tipSale;
    }
    
    class Profesor {
        int id;
        String ime;
        String prezime;
        String email;
    }
    
    class Termin {
        int id;
        int idPredmet;
        int idSala;
        int idProfesor;
        String dan;
        LocalTime vremeOd;
        LocalTime vremeDo;
        String tipTermina;
        LocalDate datum;
    }
    
    class Praznik {
        int id;
        String naziv;
        LocalDate datum;
    }
}
