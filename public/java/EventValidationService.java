import java.sql.*;
import java.time.*;
import java.util.*;

public class EventValidationService {
    
    private Connection conn;
    private Map<Integer, Course> predmeti;
    private Map<Integer, Room> sale;
    private Map<Integer, Professor> profesori;
    private List<AcademicEvent> termini;
    private List<Holiday> praznici;

    
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
        String query = "SELECT id_predmet, naziv, semestar, fond_predavanja, fond_vjezbi, vrsta_opreme, broj_studenata FROM predmet";
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            int count = 0;
            while (rs.next()) {
                Course p = new Course();
                p.idCourse = rs.getInt("id_predmet");
                p.naziv = rs.getString("naziv");
                p.semestar = rs.getInt("semestar");
                p.fondPredavanja = rs.getInt("fond_predavanja");
                p.fondVjezbi = rs.getInt("fond_vjezbi");
                p.vrstaOpreme = rs.getString("vrsta_opreme");
                p.brojStudenata = rs.getInt("broj_studenata");
                predmeti.put(p.idCourse, p);
                count++;
            }
            System.out.println("Ucitano predmeta: " + count);
        }
    }
    
    private void ucitajSale() throws SQLException {
        String query = "SELECT * FROM sala";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        
        int count = 0;
        while (rs.next()) {
            Room s = new Room();
            s.idRoom = rs.getInt("id_sala");
            s.naziv = rs.getString("naziv");
            s.kapacitet = rs.getInt("kapacitet");
            s.vrstaOpreme = rs.getString("vrsta_opreme");
            s.tipSale = rs.getString("tip_sale");
            sale.put(s.idRoom, s);
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
            Professor prof = new Professor();
            prof.idProfessor = rs.getInt("id_profesor");
            prof.ime = rs.getString("ime");
            prof.prezime = rs.getString("prezime");
            prof.email = rs.getString("email");
            profesori.put(prof.idProfessor, prof);
            count++;
        }
        
        rs.close();
        stmt.close();
        System.out.println("Ucitano profesora: " + count);
    }
    
    private void ucitajTermine() throws SQLException {
        termini.clear(); // Očisti prije reloada
        String query = "SELECT * FROM termin";
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(query);
        
        int count = 0;
        while (rs.next()) {
            AcademicEvent t = new AcademicEvent();
            t.idAcademicEvent = rs.getInt("id_termin");
            t.idCourse = rs.getInt("id_predmet");
            t.idRoom = rs.getInt("id_sala");
            t.idProfessor = rs.getInt("id_profesor");
            t.dan = rs.getString("dan");
            Time vremeOdSQL = rs.getTime("vreme_od");
            Time vremeDoSQL = rs.getTime("vreme_do");
            t.vremeOd = (vremeOdSQL != null) ? vremeOdSQL.toLocalTime() : null;
            t.vremeDo = (vremeDoSQL != null) ? vremeDoSQL.toLocalTime() : null;
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
            Holiday p = new Holiday();
            p.idHoliday = rs.getInt("id_praznik");
            p.naziv = rs.getString("naziv");
            p.datum = rs.getDate("datum").toLocalDate();
            praznici.add(p);
            count++;
        }
        
        rs.close();
        stmt.close();
        System.out.println("Ucitano praznika: " + count);
    }

    /*
        Zavrsili sa ucitavanjem
    */
    
    /**
     * NOVA HELPER METODA: Provjerava sve konflikte (salu i profesora)
     * dan = null ako se koristi datum, datum = null ako se koristi dan
     */
    private boolean imaKonflikt(String dan, LocalDate datum, int idRoom, int idProfessor,
                            LocalTime pocetak, LocalTime kraj) {
        for (AcademicEvent t : termini) {
            boolean danMatch = (dan != null && t.dan != null && t.dan.equals(dan));
            boolean datumMatch = (datum != null && t.datum != null && t.datum.equals(datum));
            if (danMatch || datumMatch) {
                boolean salaMatch = (t.idRoom == idRoom);
                boolean profesorMatch = (t.idProfessor == idProfessor);
                if (salaMatch || profesorMatch) {
                    // Null check za vremenske objekte
                    if (pocetak != null && kraj != null && t.vremeOd != null && t.vremeDo != null) {
                        // if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) ||
                        //     pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo)))
                        if (pocetak.isBefore(t.vremeDo) && kraj.isAfter(t.vremeOd)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * NOVA HELPER METODA: Generiška metoda za dodavanje predavanja i vježbi
     */
    private String dodajTerminNastave(int idPredmet, int idRoom, int idProfessor, String dan,
                                      String vremeOd, String vremeDo,
                                      String tipTermina, String potrebanTipSale) {
        try {
            Course predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Course ne postoji";
            }
            
            Room sala = sale.get(idRoom);
            if (sala == null) {
                return "GRESKA: Room ne postoji";
            }
            
            Professor profesor = profesori.get(idProfessor);
            if (profesor == null) {
                return "GRESKA: Professor ne postoji";
            }
            
            LocalTime pocetak = LocalTime.parse(vremeOd);
            LocalTime kraj = LocalTime.parse(vremeDo);
            
            if (sala.kapacitet < predmet.brojStudenata) {
                return "GRESKA: Room nema dovoljan kapacitet za broj studenata";
            }
            
            if (predmet.vrstaOpreme != null && !predmet.vrstaOpreme.isEmpty()) {
                if (sala.vrstaOpreme == null || !sala.vrstaOpreme.contains(predmet.vrstaOpreme)) {
                    return "GRESKA: Room nema potrebnu opremu: " + predmet.vrstaOpreme;
                }
            }
            
            if (!sala.tipSale.equals(potrebanTipSale) && !sala.tipSale.equals("sve")) {
                return "GRESKA: Room nije pogodna za " + tipTermina;
            }
            
            // NOVA: Koristi helper metodu umjesto dvostrukih petlji
            if (imaKonflikt(dan, null, idRoom, idProfessor, pocetak, kraj)) {
                // Provjeri ko je u konfliktu
                for (AcademicEvent t : termini) {
                    if (t.dan != null && t.dan.equals(dan)) {
                        // if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) ||
                        //       pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) 
                        if (pocetak.isBefore(t.vremeDo) && kraj.isAfter(t.vremeOd)) {
                            if (t.idRoom == idRoom) {
                                return "GRESKA: Room je zauzeta u tom terminu";
                            }
                            if (t.idProfessor == idProfessor) {
                                return "GRESKA: Professor je zauzet u tom terminu";
                            }
                        }
                    }
                }
            }
            
            String insert = "INSERT INTO termin (id_predmet, id_sala, id_profesor, dan, vreme_od, vreme_do, tip_termina) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(insert);
            pstmt.setInt(1, idPredmet);
            pstmt.setInt(2, idRoom);
            pstmt.setInt(3, idProfessor);
            pstmt.setString(4, dan);
            pstmt.setTime(5, Time.valueOf(pocetak));
            pstmt.setTime(6, Time.valueOf(kraj));
            pstmt.setString(7, tipTermina);
            pstmt.executeUpdate();
            pstmt.close();
            
            // IZBRISANO: ucitajTermine(); 
            // Umjesto toga, ručno dodaj u listu
            AcademicEvent noviTermin = new AcademicEvent();
            noviTermin.idCourse = idPredmet;
            noviTermin.idRoom = idRoom;
            noviTermin.idProfessor = idProfessor;
            noviTermin.dan = dan;
            noviTermin.vremeOd = pocetak;
            noviTermin.vremeDo = kraj;
            noviTermin.tipTermina = tipTermina;
            termini.add(noviTermin);
            
            return "OK: " + tipTermina + " uspjesno dodato";
            
        } catch (Exception e) {
            return "GRESKA: " + e.getMessage();
        }
    }

    /**
     * REFAKTORISANO: Skraćeno jer koristi generičku metodu
     */
    public String dodajPredavanje(int idPredmet, int idRoom, int idProfessor, String dan,
                                  String vremeOd, String vremeDo) {
        return dodajTerminNastave(idPredmet, idRoom, idProfessor, dan, vremeOd, vremeDo,
                                 "predavanje", "predavaliste");
    }

    /**
     * REFAKTORISANO: Skraćeno jer koristi generičku metodu
     */
    public String dodajVjezbe(int idPredmet, int idRoom, int idProfessor, String dan,
                             String vremeOd, String vremeDo) {
        return dodajTerminNastave(idPredmet, idRoom, idProfessor, dan, vremeOd, vremeDo,
                                 "vjezbe", "vjezbe");
    }
    
    public String dodajKolokvijum(int idPredmet, int idRoom, int idProfessor, int idDezurni,
                                  String datum, String vremeOd, String vremeDo) {
        try {
            Course predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Course ne postoji";
            }
            
            Room sala = sale.get(idRoom);
            if (sala == null) {
                return "GRESKA: Room ne postoji";
            }
            
            Professor profesor = profesori.get(idProfessor);
            if (profesor == null) {
                return "GRESKA: Professor ne postoji";
            }
            
            Professor dezurni = profesori.get(idDezurni);
            if (dezurni == null) {
                return "GRESKA: Dezurni profesor ne postoji";
            }
            
            LocalDate datumKolokvijuma = LocalDate.parse(datum);
            LocalTime pocetak = LocalTime.parse(vremeOd);
            LocalTime kraj = LocalTime.parse(vremeDo);
            
            if (datumKolokvijuma.getDayOfWeek() == DayOfWeek.SUNDAY) {
                return "GRESKA: Kolokvijum ne moze biti u nedjelju";
            }
            
            for (Holiday p : praznici) {
                if (p.datum.equals(datumKolokvijuma)) {
                    return "GRESKA: Ne moze se zakazati kolokvijum za praznik: " + p.naziv;
                }
            }
            
            if (!sala.tipSale.equals("vjezbe") && !sala.tipSale.equals("sve")) {
                return "GRESKA: Kolokvijum mora biti u sali za vjezbe";
            }
            
            if (sala.kapacitet < predmet.brojStudenata) {
                return "GRESKA: Room nema dovoljan kapacitet";
            }
            
            // NOVA: Koristi helper metodu
            if (imaKonflikt(null, datumKolokvijuma, idRoom, idProfessor, pocetak, kraj)) {
                for (AcademicEvent t : termini) {
                    if (t.datum != null && t.datum.equals(datumKolokvijuma)) {
                        // if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) ||
                        //       pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) 
                        if (pocetak.isBefore(t.vremeDo) && kraj.isAfter(t.vremeOd)) {
                            if (t.idRoom == idRoom) {
                                return "GRESKA: Room je zauzeta u tom terminu";
                            }
                            if (t.idProfessor == idProfessor) {
                                return "GRESKA: Professor je zauzet u tom terminu";
                            }
                        }
                    }
                }
            }

            int brojKolokvijumaTeNedjelje = 0;
            LocalDate pocetakNedjelje = datumKolokvijuma.with(DayOfWeek.MONDAY);
            LocalDate krajNedjelje = datumKolokvijuma.with(DayOfWeek.SUNDAY);
            
            for (AcademicEvent t : termini) {
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
            for (AcademicEvent t : termini) {
                if (t.tipTermina.contains("kolokvijum") && t.datum != null) {
                    if (!t.datum.isBefore(pocetakNedjelje) && !t.datum.isAfter(krajNedjelje)) {
                        String query = "SELECT id_dezurni FROM kolokvijum WHERE id_termin = ?";
                        PreparedStatement ps = conn.prepareStatement(query);
                        ps.setInt(1, t.idAcademicEvent);
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
            pstmt.setInt(2, idRoom);
            pstmt.setInt(3, idProfessor);
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
            
            // IZBRISANO: ucitajTermine();
            
            return "OK: Kolokvijum uspjesno dodat";
            
        } catch (Exception e) {
            return "GRESKA: " + e.getMessage();
        }
    }
    
    public String dodajIspit(int idPredmet, int idRoom, int idProfessor, String datum,
                            String vremeOd, String vremeDo, String tipIspita) {
        try {
            Course predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Course ne postoji";
            }
            
            Room sala = sale.get(idRoom);
            if (sala == null) {
                return "GRESKA: Room ne postoji";
            }
            
            Professor profesor = profesori.get(idProfessor);
            if (profesor == null) {
                return "GRESKA: Professor ne postoji";
            }
            
            LocalDate datumIspita = LocalDate.parse(datum);
            LocalTime pocetak = LocalTime.parse(vremeOd);
            LocalTime kraj = LocalTime.parse(vremeDo);
            
            if (datumIspita.getDayOfWeek() == DayOfWeek.SUNDAY) {
                return "GRESKA: Ispit ne moze biti u nedjelju";
            }
            
            for (Holiday p : praznici) {
                if (p.datum.equals(datumIspita)) {
                    return "GRESKA: Ne moze se zakazati ispit za praznik: " + p.naziv;
                }
            }
            
            if (sala.kapacitet < predmet.brojStudenata) {
                return "GRESKA: Room nema dovoljan kapacitet";
            }
            
            if (predmet.vrstaOpreme != null && !predmet.vrstaOpreme.isEmpty()) {
                if (sala.vrstaOpreme == null || !sala.vrstaOpreme.contains(predmet.vrstaOpreme)) {
                    return "GRESKA: Room nema potrebnu opremu: " + predmet.vrstaOpreme;
                }
            }
            
            // NOVA: Koristi helper metodu
            if (imaKonflikt(null, datumIspita, idRoom, idProfessor, pocetak, kraj)) {
                for (AcademicEvent t : termini) {
                    if (t.datum != null && t.datum.equals(datumIspita)) {
                        // if (!(kraj.isBefore(t.vremeOd) || kraj.equals(t.vremeOd) ||
                        //       pocetak.isAfter(t.vremeDo) || pocetak.equals(t.vremeDo))) 
                        if (pocetak.isBefore(t.vremeDo) && kraj.isAfter(t.vremeOd)) {
                            if (t.idRoom == idRoom) {
                                return "GRESKA: Room je zauzeta u tom terminu";
                            }
                            if (t.idProfessor == idProfessor) {
                                return "GRESKA: Professor je zauzet u tom terminu";
                            }
                        }
                    }
                }
            }
            
            String insertTermin = "INSERT INTO termin (id_predmet, id_sala, id_profesor, datum, vreme_od, vreme_do, tip_termina) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(insertTermin, Statement.RETURN_GENERATED_KEYS);
            pstmt.setInt(1, idPredmet);
            pstmt.setInt(2, idRoom);
            pstmt.setInt(3, idProfessor);
            pstmt.setDate(4, java.sql.Date.valueOf(datumIspita));
            pstmt.setTime(5, Time.valueOf(pocetak));
            pstmt.setTime(6, Time.valueOf(kraj));
            pstmt.setString(7, tipIspita);
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            rs.close();
            pstmt.close();
            
            // IZBRISANO: ucitajTermine();
            
            return "OK: Ispit uspjesno dodat";
            
        } catch (Exception e) {
            return "GRESKA: " + e.getMessage();
        }
    }

    /**
     * Generiše kompletan raspored za SVE predmete (predavanja + vježbe)
     * Prioritizuje predmete na osnovu fleksibilnosti profesora
     */
    public String generisiKompletniRaspored() {
        try {
            System.out.println("=== POKRETANJE AUTOMATSKOG GENERISANJA KOMPLETNOG RASPOREDA ===\n");
            
            // 1. Analiza fleksibilnosti profesora
            Map<Integer, Double> fleksibilnostProfesora = analizirajFleksibilnostProfesora();
            
            // 2. Određivanje prioriteta predmeta
            List<PredmetPrioritet> prioriteti = odrediPrioritete(fleksibilnostProfesora);
            
            System.out.println("--- PRIORITETI PREDMETA ---");
            for (PredmetPrioritet pp : prioriteti) {
                System.out.printf("Predmet: %-30s | Prioritet: %.2f | Prof.Pred: %-20s (flex: %.2f) | Prof.Vjez: %-20s (flex: %.2f)\n",
                    pp.predmet.naziv,
                    pp.prioritet,
                    pp.profesorPredavanja != null ? pp.profesorPredavanja.ime + " " + pp.profesorPredavanja.prezime : "NEMA",
                    pp.fleksibilnostPredavanja,
                    pp.profesorVjezbi != null ? pp.profesorVjezbi.ime + " " + pp.profesorVjezbi.prezime : "NEMA",
                    pp.fleksibilnostVjezbi
                );
            }
            
            // 3. Generisanje rasporeda po prioritetu
            int uspjesnoPredavanja = 0;
            int djelimicnoPredavanja = 0;
            int neuspjesnoPredavanja = 0;
            
            int uspjesnoVjezbi = 0;
            int djelimicnoVjezbi = 0;
            int neuspjesnoVjezbi = 0;
            
            StringBuilder detalji = new StringBuilder();
            detalji.append("\n\n=== DETALJI GENERISANJA ===\n");
            
            for (PredmetPrioritet pp : prioriteti) {
                Course predmet = pp.predmet;
                detalji.append("\n--- ").append(predmet.naziv).append(" (ID: ").append(predmet.idCourse).append(") ---\n");
                
                // Generiši predavanja ako ima fond i profesora
                if (predmet.fondPredavanja > 0 && pp.profesorPredavanja != null) {
                    String rezultatPredavanja = generisiRasporedPredavanja(predmet.idCourse);
                    detalji.append("  [PREDAVANJA] ").append(rezultatPredavanja).append("\n");
                    
                    if (rezultatPredavanja.startsWith("OK")) {
                        uspjesnoPredavanja++;
                    } else if (rezultatPredavanja.startsWith("UPOZORENJE")) {
                        djelimicnoPredavanja++;
                    } else {
                        neuspjesnoPredavanja++;
                    }
                } else if (predmet.fondPredavanja > 0) {
                    detalji.append("  [PREDAVANJA] GRESKA: Nema dodijeljenog profesora\n");
                    neuspjesnoPredavanja++;
                } else {
                    detalji.append("  [PREDAVANJA] Nema fonda - preskočeno\n");
                }
                
                // Generiši vježbe ako ima fond i asistenta
                if (predmet.fondVjezbi > 0 && pp.profesorVjezbi != null) {
                    String rezultatVjezbi = generisiRasporedVjezbi(predmet.idCourse);
                    detalji.append("  [VJEZBE] ").append(rezultatVjezbi).append("\n");
                    
                    if (rezultatVjezbi.startsWith("OK")) {
                        uspjesnoVjezbi++;
                    } else if (rezultatVjezbi.startsWith("UPOZORENJE")) {
                        djelimicnoVjezbi++;
                    } else {
                        neuspjesnoVjezbi++;
                    }
                } else if (predmet.fondVjezbi > 0) {
                    detalji.append("  [VJEZBE] GRESKA: Nema dodijeljenog asistenta\n");
                    neuspjesnoVjezbi++;
                } else {
                    detalji.append("  [VJEZBE] Nema fonda - preskočeno\n");
                }
            }
            
            // 4. Ispis detalja
            System.out.println(detalji.toString());
            
            // 5. Sažetak rezultata
            String sazetak = String.format(
                "\n=== ZAVRŠENO GENERISANJE KOMPLETNOG RASPOREDA ===\n" +
                "PREDAVANJA:\n" +
                "  ✓ Potpuno uspješno:  %d\n" +
                "  ⚠ Djelimično:        %d\n" +
                "  ✗ Neuspješno:        %d\n" +
                "\n" +
                "VJEŽBE:\n" +
                "  ✓ Potpuno uspješno:  %d\n" +
                "  ⚠ Djelimično:        %d\n" +
                "  ✗ Neuspješno:        %d\n" +
                "\n" +
                "UKUPNO PREDMETA OBRAĐENO: %d\n" +
                "================================================",
                uspjesnoPredavanja, djelimicnoPredavanja, neuspjesnoPredavanja,
                uspjesnoVjezbi, djelimicnoVjezbi, neuspjesnoVjezbi,
                prioriteti.size()
            );
            
            System.out.println(sazetak);
            
            // NOVA: Učitaj sve termine jednom na kraju
            ucitajTermine();
            
            if (neuspjesnoPredavanja > 0 || neuspjesnoVjezbi > 0) {
                return "UPOZORENJE: Raspored djelimično generisan. Provjerite detalje iznad.";
            }
            
            if (djelimicnoPredavanja > 0 || djelimicnoVjezbi > 0) {
                return "UPOZORENJE: Neki predmeti nisu dobili sve potrebne termine. Provjerite detalje iznad.";
            }
            
            return "OK: Kompletan raspored uspješno generisan za sve predmete!";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "GRESKA: " + e.getMessage();
        }
    }

    /**
     * Analizira fleksibilnost svakog profesora
     * Fleksibilnost = 0.0 (nefleksibilan) do 1.0 (veoma fleksibilan)
     */
    private Map<Integer, Double> analizirajFleksibilnostProfesora() {
        Map<Integer, Double> fleksibilnost = new HashMap<>();
        
        for (Professor prof : profesori.values()) {
            double score = 1.0; // Početni maksimalni score
            
            try {
                // FAKTOR 1: Broj preferiranih dana
                String queryPref = "SELECT COUNT(DISTINCT dan) as broj_dana FROM profesor_preferencije WHERE id_profesor = ?";
                PreparedStatement ps = conn.prepareStatement(queryPref);
                ps.setInt(1, prof.idProfessor);
                ResultSet rs = ps.executeQuery();
                
                int brojPreferiranih = 0;
                if (rs.next()) {
                    brojPreferiranih = rs.getInt("broj_dana");
                }
                rs.close();
                ps.close();
                
                // Manje dana = manje fleksibilnosti
                if (brojPreferiranih == 0) {
                    score *= 0.8; // Defaultni dani, srednja fleksibilnost
                } else if (brojPreferiranih == 1) {
                    score *= 0.3; // Samo 1 dan = VEOMA nefleksibilan!
                } else if (brojPreferiranih == 2) {
                    score *= 0.5; // 2 dana = malo fleksibilan
                } else if (brojPreferiranih == 3) {
                    score *= 0.7; // 3 dana = srednje fleksibilan
                } else {
                    score *= 1.0; // 4+ dana = veoma fleksibilan
                }
                
                // FAKTOR 2: Koliko je već zauzet
                int zauzetiTermini = 0;
                for (AcademicEvent termin : termini) {
                    if (termin.idProfessor == prof.idProfessor) {
                        zauzetiTermini++;
                    }
                }
                
                // Više zauzetih termina = manje fleksibilnosti
                if (zauzetiTermini == 0) {
                    score *= 1.0; // Potpuno slobodan
                } else if (zauzetiTermini <= 4) {
                    score *= 0.9; // Malo zauzet
                } else if (zauzetiTermini <= 9) {
                    score *= 0.7; // Osrednje zauzet
                } else if (zauzetiTermini <= 14) {
                    score *= 0.5; // Dosta zauzet
                } else if (zauzetiTermini <= 19) {
                    score *= 0.3; // Veoma zauzet
                } else {
                    score *= 0.1; // Preopterećen
                }
                
            } catch (SQLException e) {
                System.err.println("Greška pri analizi profesora " + prof.idProfessor + ": " + e.getMessage());
                score = 0.5; // Default srednja fleksibilnost ako je greška
            }
            
            fleksibilnost.put(prof.idProfessor, score);
        }
        
        return fleksibilnost;
    }

    /**
     * Određuje prioritete predmeta na osnovu fleksibilnosti profesora
     * Veći prioritet (viši broj) = obrađuje se PRIJE
     */
    private List<PredmetPrioritet> odrediPrioritete(Map<Integer, Double> fleksibilnostProfesora) {
        List<PredmetPrioritet> prioriteti = new ArrayList<>();
        
        for (Course predmet : predmeti.values()) {
            try {
                PredmetPrioritet pp = new PredmetPrioritet();
                pp.predmet = predmet;
                
                // Pronađi profesora za predavanja
                if (predmet.fondPredavanja > 0) {
                    String queryPred = "SELECT id_profesor FROM predmet_profesor WHERE id_predmet = ? AND tip = 'predavanje'";
                    PreparedStatement ps1 = conn.prepareStatement(queryPred);
                    ps1.setInt(1, predmet.idCourse);
                    ResultSet rs1 = ps1.executeQuery();
                    
                    if (rs1.next()) {
                        int idProf = rs1.getInt("id_profesor");
                        pp.profesorPredavanja = profesori.get(idProf);
                        pp.fleksibilnostPredavanja = fleksibilnostProfesora.getOrDefault(idProf, 0.5);
                    }
                    rs1.close();
                    ps1.close();
                }
                
                // Pronađi profesora za vježbe
                if (predmet.fondVjezbi > 0) {
                    String queryVjez = "SELECT id_profesor FROM predmet_profesor WHERE id_predmet = ? AND tip = 'vjezbe'";
                    PreparedStatement ps2 = conn.prepareStatement(queryVjez);
                    ps2.setInt(1, predmet.idCourse);
                    ResultSet rs2 = ps2.executeQuery();
                    
                    if (rs2.next()) {
                        int idProf = rs2.getInt("id_profesor");
                        pp.profesorVjezbi = profesori.get(idProf);
                        pp.fleksibilnostVjezbi = fleksibilnostProfesora.getOrDefault(idProf, 0.5);
                    }
                    rs2.close();
                    ps2.close();
                }
                
                // PRIORITET = 1.0 - prosječna fleksibilnost (inverzija)
                // Manja fleksibilnost = viši prioritet (bliže 1.0 nakon inverzije)
                double prosjecnaFleksibilnost = (pp.fleksibilnostPredavanja + pp.fleksibilnostVjezbi) / 2.0;
                pp.prioritet = 1.0 - prosjecnaFleksibilnost;
                
                // Dodatni boost prioriteta ako nema profesora (problem!)
                if (pp.profesorPredavanja == null && predmet.fondPredavanja > 0) {
                    pp.prioritet += 0.5; // Označi kao problem
                }
                if (pp.profesorVjezbi == null && predmet.fondVjezbi > 0) {
                    pp.prioritet += 0.5; // Označi kao problem
                }
                
                prioriteti.add(pp);
                
            } catch (SQLException e) {
                System.err.println("Greška pri određivanju prioriteta za predmet " + predmet.idCourse + ": " + e.getMessage());
            }
        }
        
        // Sortiraj od NAJVIŠEG ka NAJNIŽEM prioritetu (veći broj = viši prioritet)
        prioriteti.sort((a, b) -> Double.compare(b.prioritet, a.prioritet));
        
        return prioriteti;
    }

    public String generisiRasporedPredavanja(int idPredmet) {
        try {
            Course predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Course ne postoji";
            }
            
            if (predmet.fondPredavanja <= 0) {
                return "GRESKA: Course nema fond predavanja";
            }
            
            String queryProf = "SELECT id_profesor FROM predmet_profesor WHERE id_predmet = ? AND tip = 'predavanje'";
            PreparedStatement ps = conn.prepareStatement(queryProf);
            ps.setInt(1, idPredmet);
            ResultSet rs = ps.executeQuery();
            
            int idProfessor = 0;
            if (rs.next()) {
                idProfessor = rs.getInt("id_profesor");
            } else {
                rs.close();
                ps.close();
                return "GRESKA: Course nema dodijeljenog profesora za predavanja";
            }
            rs.close();
            ps.close();
            
            String queryPref = "SELECT * FROM profesor_preferencije WHERE id_profesor = ?";
            PreparedStatement ps2 = conn.prepareStatement(queryPref);
            ps2.setInt(1, idProfessor);
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
            
            List<Room> pogodneSale = new ArrayList<>();
            for (Room s : sale.values()) {
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
                
                for (Room sala : pogodneSale) {
                    if (dodanoTermina >= potrebnoTermina) {
                        break;
                    }
                    
                    String vremeOd = prefVremena.getOrDefault(dan, "09:00:00");
                    LocalTime pocetak = LocalTime.parse(vremeOd);
                    LocalTime kraj = pocetak.plusHours(1);
                    
                    // NOVA: Koristi helper metodu umjesto dvostrukih petlji
                    if (!imaKonflikt(dan, null, sala.idRoom, idProfessor, pocetak, kraj)) {
                        String insert = "INSERT INTO termin (id_predmet, id_sala, id_profesor, dan, vreme_od, vreme_do, tip_termina) " +
                                      "VALUES (?, ?, ?, ?, ?, ?, 'predavanje')";
                        PreparedStatement pstmt = conn.prepareStatement(insert);
                        pstmt.setInt(1, idPredmet);
                        pstmt.setInt(2, sala.idRoom);
                        pstmt.setInt(3, idProfessor);
                        pstmt.setString(4, dan);
                        pstmt.setTime(5, Time.valueOf(pocetak));
                        pstmt.setTime(6, Time.valueOf(kraj));
                        pstmt.executeUpdate();
                        pstmt.close();
                        
                        // NOVA: Ručno dodaj u listu umjesto reloada
                        AcademicEvent noviTermin = new AcademicEvent();
                        noviTermin.idCourse = idPredmet;
                        noviTermin.idRoom = sala.idRoom;
                        noviTermin.idProfessor = idProfessor;
                        noviTermin.dan = dan;
                        noviTermin.vremeOd = pocetak;
                        noviTermin.vremeDo = kraj;
                        noviTermin.tipTermina = "predavanje";
                        termini.add(noviTermin);
                        
                        dodanoTermina++;
                    }
                }
            }
            
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
            Course predmet = predmeti.get(idPredmet);
            if (predmet == null) {
                return "GRESKA: Course ne postoji";
            }
            
            if (predmet.fondVjezbi <= 0) {
                return "GRESKA: Course nema fond vjezbi";
            }
            
            String queryProf = "SELECT id_profesor FROM predmet_profesor WHERE id_predmet = ? AND tip = 'vjezbe'";
            PreparedStatement ps = conn.prepareStatement(queryProf);
            ps.setInt(1, idPredmet);
            ResultSet rs = ps.executeQuery();
            
            int idProfessor = 0;
            if (rs.next()) {
                idProfessor = rs.getInt("id_profesor");
            } else {
                rs.close();
                ps.close();
                return "GRESKA: Course nema dodijeljenog asistenta za vjezbe";
            }
            rs.close();
            ps.close();
            
            String queryPref = "SELECT * FROM profesor_preferencije WHERE id_profesor = ?";
            PreparedStatement ps2 = conn.prepareStatement(queryPref);
            ps2.setInt(1, idProfessor);
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
            
            List<Room> pogodneSale = new ArrayList<>();
            for (Room s : sale.values()) {
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
                
                for (Room sala : pogodneSale) {
                    if (dodanoTermina >= potrebnoTermina) {
                        break;
                    }
                    
                    String vremeOd = prefVremena.getOrDefault(dan, "10:00:00");
                    LocalTime pocetak = LocalTime.parse(vremeOd);
                    LocalTime kraj = pocetak.plusHours(1);
                    
                    // NOVA: Koristi helper metodu umjesto dvostrukih petlji
                    if (!imaKonflikt(dan, null, sala.idRoom, idProfessor, pocetak, kraj)) {
                        String insert = "INSERT INTO termin (id_predmet, id_sala, id_profesor, dan, vreme_od, vreme_do, tip_termina) " +
                                      "VALUES (?, ?, ?, ?, ?, ?, 'vjezbe')";
                        PreparedStatement pstmt = conn.prepareStatement(insert);
                        pstmt.setInt(1, idPredmet);
                        pstmt.setInt(2, sala.idRoom);
                        pstmt.setInt(3, idProfessor);
                        pstmt.setString(4, dan);
                        pstmt.setTime(5, Time.valueOf(pocetak));
                        pstmt.setTime(6, Time.valueOf(kraj));
                        pstmt.executeUpdate();
                        pstmt.close();
                        
                        // NOVA: Ručno dodaj u listu umjesto reloada
                        AcademicEvent noviTermin = new AcademicEvent();
                        noviTermin.idCourse = idPredmet;
                        noviTermin.idRoom = sala.idRoom;
                        noviTermin.idProfessor = idProfessor;
                        noviTermin.dan = dan;
                        noviTermin.vremeOd = pocetak;
                        noviTermin.vremeDo = kraj;
                        noviTermin.tipTermina = "vjezbe";
                        termini.add(noviTermin);
                        
                        dodanoTermina++;
                    }
                }
            }
            
            if (dodanoTermina < potrebnoTermina) {
                return "UPOZORENJE: Generisano " + dodanoTermina + " od " + potrebnoTermina + " potrebnih termina";
            }
            
            return "OK: Automatski generisan raspored vjezbi (" + dodanoTermina + " termina)";
            
        } catch (Exception e) {
            return "GRESKA: " + e.getMessage();
        }
    }
    public List<AcademicEvent> getEventsBySchedule(int scheduleIdFilter) throws SQLException {
    List<AcademicEvent> result = new ArrayList<>();

    String sql = "SELECT id, course_id, created_by_professor, type_enum, " +
                 "starts_at, ends_at, is_online, room_id, notes, schedule_id " +
                 "FROM academic_event " +
                 "WHERE schedule_id = ? " +
                 "ORDER BY starts_at";

    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setInt(1, scheduleIdFilter);

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                AcademicEvent ev = new AcademicEvent();

                ev.idAcademicEvent = rs.getInt("id");
                ev.idCourse        = rs.getInt("course_id");
                ev.idRoom          = rs.getInt("room_id");
                ev.idProfessor     = (int) rs.getLong("created_by_professor");
                ev.tipTermina      = rs.getString("type_enum");

                java.sql.Timestamp tsStart = rs.getTimestamp("starts_at");
                java.sql.Timestamp tsEnd   = rs.getTimestamp("ends_at");

                if (tsStart != null) {
                    LocalDateTime ldtStart = tsStart.toLocalDateTime();
                    ev.datum   = ldtStart.toLocalDate();
                    ev.vremeOd = ldtStart.toLocalTime();
                    ev.dan     = ev.datum.getDayOfWeek().toString().toLowerCase();
                }

                if (tsEnd != null) {
                    LocalDateTime ldtEnd = tsEnd.toLocalDateTime();
                    ev.vremeDo = ldtEnd.toLocalTime();
                }

                ev.jeOnline   = rs.getBoolean("is_online");
                ev.teze      = rs.getString("notes");
                ev.rasporedID = rs.getInt("schedule_id");

                result.add(ev);
            }
        }
    }

    return result;
}

}

// ============ MODEL KLASE ============

class Course {
    public int idCourse;
    public String naziv;
    public int semestar;
    public int fondPredavanja;
    public int fondVjezbi;
    public String vrstaOpreme;
    public int brojStudenata;
}

class Professor {
    public int idProfessor;
    public String ime;
    public String prezime;
    public String email;
}

class Room {
    public int idRoom;
    public String naziv;
    public int kapacitet;
    public String vrstaOpreme;
    public String tipSale;
}

class AcademicEvent {
    public int idAcademicEvent;
    public int idCourse;
    public int idRoom;
    public int idProfessor;
    public String dan;
    public LocalTime vremeOd;
    public LocalTime vremeDo;
    public String tipTermina;
    public LocalDate datum;

    public boolean jeOnline;
    public String teze;
    public int rasporedID;
}



class Holiday {
    public int idHoliday;
    public String naziv;
    public LocalDate datum;
}

class PredmetPrioritet {
    public Course predmet;
    public Professor profesorPredavanja;
    public Professor profesorVjezbi;
    public double fleksibilnostPredavanja = 0.5;
    public double fleksibilnostVjezbi = 0.5;
    public double prioritet = 0.5;
}
