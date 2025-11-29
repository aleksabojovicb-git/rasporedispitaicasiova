import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/*
 * SERVIS ZA VALIDACIJU DOGAĐAJA
 * 
 * Funkcionalnosti:
 * - Validacija osnovnih parametara (DONE)
 * - Provera dostupnosti dana (DONE)
 * - Provera trajanja događaja (DONE)
 * - Preuzimanje/kreiranje profesora (DONE)
 * - Preuzimanje/kreiranje asistenta (DONE)
 * - Preuzimanje/kreiranje predmeta (DONE)
 * - Provera duplikatnih vežbi (DONE)
 * - Pronalaženje/validacija učionice (DONE)
 * - Validacija specijalnih tipova događaja (NOVO)
 */

public class EventValidationService {

	private static List<dogadjaj> dogadjaji = new ArrayList<>();
	private static List<profesor> profesori = new ArrayList<>();
	private static List<ucionica> ucionice = new ArrayList<>();
	private static List<predmet> predmeti = new ArrayList<>();
	
	private static final String PHP_API_BASE = "http://localhost/api/";
	private static final int CONNECTION_TIMEOUT = 5000;
	private static final int READ_TIMEOUT = 5000;
	
	// Definiši prazničke dane - nedelje i dani (0 = ponedeljak, 6 = nedelja)
	private static int[][] praznici = {
		{2, 4},  // Treća nedelja, petak
		{5, 0},  // Šesta nedelja, ponedeljak
		{10, 2}  // Jedanaesta nedelja, sreda
	};
	
	public EventValidationService() {
		dogadjaji = new ArrayList<>();
		profesori = new ArrayList<>();
		ucionice = new ArrayList<>();
		predmeti = new ArrayList<>();
	}
	
	// ========================================================================
	// GLAVNA VALIDACIJSKA FUNKCIJA
	// ========================================================================
	
	public ResultValidacije mogucnostDodavanjaDogadjaja(ZahtevZaKreiranje zahtev) {
		
		// Korak 1: Validacija osnovnih parametara
		ResultValidacije rezultatBasic = validirajOsnovneParametre(zahtev);
		if (!rezultatBasic.daLiJeValidno())
			return rezultatBasic;
		
		// Korak 2: Provera dostupnosti dana
		ResultValidacije rezultatDan = validirajDostupnostDana(zahtev);
		if (!rezultatDan.daLiJeValidno())
			return rezultatDan;
		
		// Korak 3: Provera trajanja događaja
		ResultValidacije rezultatTrajanje = validirajTrajanjeDogadjaja(zahtev);
		if (!rezultatTrajanje.daLiJeValidno())
			return rezultatTrajanje;
		
		// Korak 4: Preuzimanje ili kreiranje profesora
		ResultValidacije rezultatProfesor = preuzmiBiKreiraProfesora(zahtev);
		if (!rezultatProfesor.daLiJeValidno())
			return rezultatProfesor;
		
		// Korak 5: Preuzimanje ili kreiranje asistenta
		ResultValidacije rezultatAsistent = preuzmiBiKreiraAsistenta(zahtev);
		if (!rezultatAsistent.daLiJeValidno())
			return rezultatAsistent;
		
		// Korak 6: Preuzimanje ili kreiranje predmeta
		ResultValidacije rezultatPredmet = preuzmiBiKreiriaPredmet(zahtev);
		if (!rezultatPredmet.daLiJeValidno())
			return rezultatPredmet;
		
		// Korak 7: Provera duplikatnih vežbi
		ResultValidacije rezultatDuplika = validirajDuplikatVezbe(zahtev);
		if (!rezultatDuplika.daLiJeValidno())
			return rezultatDuplika;
		
		// Korak 8: Pronalaženje i validacija učionice
		ResultValidacije rezultatUcionica = preuzmiBiKreiraUcionicu(zahtev);
		if (!rezultatUcionica.daLiJeValidno())
			return rezultatUcionica;
		
		// Korak 9: Validacija specijalnih tipova događaja
		ResultValidacije rezultatSpecijalni = validirajSpecijalneTipove(zahtev);
		if (!rezultatSpecijalni.daLiJeValidno())
			return rezultatSpecijalni;
		
		System.out.println("✓ Sve validacije su prošle!");
		return new ResultValidacije(true, "Sve validacije su uspešne");
	}
	
	// ========================================================================
	// SEKCIJA 1: VALIDACIJA OSNOVNIH PARAMETARA
	// ========================================================================
	
	private ResultValidacije validirajOsnovneParametre(ZahtevZaKreiranje zahtev) {
		List<String> greske = new ArrayList<>();
		
		if (zahtev.getNazivPredmeta() == null || zahtev.getNazivPredmeta().trim().isEmpty())
			greske.add("Naziv predmeta je obavezna polja");
		
		if (zahtev.getImeProfesora() == null || zahtev.getImeProfesora().trim().isEmpty())
			greske.add("Ime profesora je obavezno polje");
		
		if (zahtev.getKodPredmeta() == null || zahtev.getKodPredmeta().trim().isEmpty())
			greske.add("Kod predmeta je obavezno polje");
		
		if (zahtev.getTrazeniKapacitet() < 0)
			greske.add("Kapacitet ne može biti negativan");
		
		if (zahtev.getGodinaStudija() < 0 || zahtev.getGodinaStudija() > 4)
			greske.add("Godina studija mora biti između 0 i 4");
		
		if (zahtev.getDanUNedelji() > 6 || zahtev.getDanUNedelji() < 0)
			greske.add("Dan nedelje mora biti između 0 i 6");
		
		if (zahtev.getTerminPocetka() < 0 || zahtev.getTerminPocetka() > 23)
			greske.add("Termin početka mora biti između 0 i 23");
		
		if (zahtev.getTerminKraja() < zahtev.getTerminPocetka() || zahtev.getTerminKraja() > 23)
			greske.add("Termin kraja mora biti posle početka");
		
		if (zahtev.getNedeljaUSemestru() < 0 || zahtev.getNedeljaUSemestru() > 15)
			greske.add("Nedelja semestra mora biti između 0 i 15");
		
		if (!greske.isEmpty()) {
			String poruka = String.join(", ", greske);
			System.out.println("✗ Greške: " + poruka);
			return new ResultValidacije(false, poruka);
		}
		
		System.out.println("✓ Osnovni parametri su validni");
		return new ResultValidacije(true, "Osnovni parametri su u redu");
	}
	
	// ========================================================================
	// SEKCIJA 2: VALIDACIJA DOSTUPNOSTI DANA
	// ========================================================================
	
	private ResultValidacije validirajDostupnostDana(ZahtevZaKreiranje zahtev) {
		if (jeLiDanSlobodan(zahtev.getNedeljaUSemestru(), zahtev.getDanUNedelji())) {
			System.out.println("✗ Taj dan je praznik ili je zatvoren!");
			return new ResultValidacije(false, "Ne može se zakazati događaj na slobodan dan");
		}
		
		System.out.println("✓ Dan je dostupan");
		return new ResultValidacije(true, "Dan je slobodan");
	}
	
	private boolean jeLiDanSlobodan(int nedelja, int dan) {
		for (int[] praznik : praznici) {
			if (praznik[0] == nedelja && praznik[1] == dan) {
				return true;
			}
		}
		return false;
	}
	
	// ========================================================================
	// SEKCIJA 3: VALIDACIJA TRAJANJA DOGAĐAJA
	// ========================================================================
	
	private ResultValidacije validirajTrajanjeDogadjaja(ZahtevZaKreiranje zahtev) {
		int trajanje = zahtev.getTerminKraja() - zahtev.getTerminPocetka();
		
		if (trajanje > 12) {
			System.out.println("✗ Događaj je duži od 12 sati!");
			return new ResultValidacije(false, "Maksimalno trajanje je 12 sati");
		}
		
		System.out.println("✓ Trajanje je validno (" + trajanje + " sati)");
		return new ResultValidacije(true, "Trajanje je u redu");
	}
	
	// ========================================================================
	// SEKCIJA 4: PREUZIMANJE ILI KREIRANJE PROFESORA
	// ========================================================================
	
	private ResultValidacije preuzmiBiKreiraProfesora(ZahtevZaKreiranje zahtev) {
		try {
			// Prvo proveravamo da li profesor postoji u lokalnoj listi
			profesor existingProfesor = null;
			for (profesor p : profesori) {
				if (p.getIme().equals(zahtev.getImeProfesora())) {
					existingProfesor = p;
					break;
				}
			}
			
			if (existingProfesor != null) {
				zahtev.setProfesor(existingProfesor);
				System.out.println("✓ Profesor pronađen u keš memoriji");
				return new ResultValidacije(true, "Profesor pronađen");
			}
			
			// Ako nema u lokalnoj bazi, proveravamo PHP backend
			String parametri = "ime=" + URLEncoder.encode(zahtev.getImeProfesora(), "UTF-8");
			String odgovor = upitajPhp("profesor/provera", parametri);
			
			if (odgovor.startsWith("postoji|da")) {
				String[] delovi = odgovor.split("\\|");
				if (delovi.length >= 4) {
					profesor p = new profesor(
						Integer.parseInt(delovi[2]),
						delovi[3],
						delovi.length > 4 ? delovi[4] : ""
					);
					profesori.add(p);
					zahtev.setProfesor(p);
					System.out.println("✓ Profesor preuzet iz baze");
					return new ResultValidacije(true, "Profesor preuzet");
				}
			}
			
			// Ako profesor ne postoji, kreiraj novog
			profesor noviProfesor = new profesor(0, zahtev.getImeProfesora(), zahtev.getMailProfesora());
			zahtev.setProfesor(noviProfesor);
			System.out.println("✓ Novi profesor je spreman za kreiranje");
			return new ResultValidacije(true, "Profesor je spreman");
			
		} catch (Exception e) {
			System.out.println("✗ Greška pri validaciji profesora: " + e.getMessage());
			return new ResultValidacije(false, "Greška pri validaciji profesora: " + e.getMessage());
		}
	}
	
	// ========================================================================
	// SEKCIJA 5: PREUZIMANJE ILI KREIRANJE ASISTENTA
	// ========================================================================
	
	private ResultValidacije preuzmiBiKreiraAsistenta(ZahtevZaKreiranje zahtev) {
		// Ako nema asistenta, to je ok
		if (zahtev.getImeAsistenta() == null || zahtev.getImeAsistenta().trim().isEmpty()) {
			System.out.println("✓ Asistent nije obavezna polja");
			return new ResultValidacije(true, "Nema asistenta");
		}
		
		try {
			// Proveravamo lokalnu listu
			profesor existingAsistent = null;
			for (profesor p : profesori) {
				if (p.getIme().equals(zahtev.getImeAsistenta())) {
					existingAsistent = p;
					break;
				}
			}
			
			if (existingAsistent != null) {
				zahtev.setAsistent(existingAsistent);
				System.out.println("✓ Asistent pronađen u keš memoriji");
				return new ResultValidacije(true, "Asistent pronađen");
			}
			
			// Upitaj PHP backend
			String parametri = "ime=" + URLEncoder.encode(zahtev.getImeAsistenta(), "UTF-8");
			String odgovor = upitajPhp("asistent/provera", parametri);
			
			if (odgovor.startsWith("postoji|da")) {
				String[] delovi = odgovor.split("\\|");
				if (delovi.length >= 4) {
					profesor a = new profesor(
						Integer.parseInt(delovi[2]),
						delovi[3],
						delovi.length > 4 ? delovi[4] : ""
					);
					profesori.add(a);
					zahtev.setAsistent(a);
					System.out.println("✓ Asistent preuzet iz baze");
					return new ResultValidacije(true, "Asistent preuzet");
				}
			}
			
			// Kreiraj novog asistenta
			profesor noviAsistent = new profesor(0, zahtev.getImeAsistenta(), zahtev.getMailAsistenta());
			zahtev.setAsistent(noviAsistent);
			System.out.println("✓ Novi asistent je spreman za kreiranje");
			return new ResultValidacije(true, "Asistent je spreman");
			
		} catch (Exception e) {
			System.out.println("✗ Greška pri validaciji asistenta: " + e.getMessage());
			return new ResultValidacije(false, "Greška pri validaciji asistenta: " + e.getMessage());
		}
	}
	
	// ========================================================================
	// SEKCIJA 6: PREUZIMANJE ILI KREIRANJE PREDMETA
	// ========================================================================
	
	private ResultValidacije preuzmiBiKreiriaPredmet(ZahtevZaKreiranje zahtev) {
		try {
			// Proveravamo lokalnu listu
			predmet existingPredmet = null;
			for (predmet p : predmeti) {
				if (p.getNaziv().equals(zahtev.getNazivPredmeta())) {
					existingPredmet = p;
					break;
				}
			}
			
			if (existingPredmet != null) {
				zahtev.setPredmet(existingPredmet);
				System.out.println("✓ Predmet pronađen u keš memoriji");
				return new ResultValidacije(true, "Predmet pronađen");
			}
			
			// Upitaj PHP backend
			String parametri = "naziv=" + URLEncoder.encode(zahtev.getNazivPredmeta(), "UTF-8") +
							   "&kod=" + URLEncoder.encode(zahtev.getKodPredmeta(), "UTF-8");
			String odgovor = upitajPhp("predmet/provera", parametri);
			
			if (odgovor.startsWith("postoji|da")) {
				String[] delovi = odgovor.split("\\|");
				if (delovi.length >= 7) {
					predmet p = new predmet(
						Integer.parseInt(delovi[2]),
						delovi[3],
						delovi[4],
						zahtev.getProfesor(),
						zahtev.getAsistent(),
						Integer.parseInt(delovi[5]),
						"1".equals(delovi[6])
					);
					predmeti.add(p);
					zahtev.setPredmet(p);
					System.out.println("✓ Predmet preuzet iz baze");
					return new ResultValidacije(true, "Predmet preuzet");
				}
			}
			
			// Kreiraj novi predmet
			predmet noviPredmet = new predmet(
				0,
				zahtev.getNazivPredmeta(),
				zahtev.getKodPredmeta(),
				zahtev.getProfesor(),
				zahtev.getAsistent(),
				zahtev.getGodinaStudija(),
				zahtev.isAktivan()
			);
			zahtev.setPredmet(noviPredmet);
			System.out.println("✓ Novi predmet je spreman za kreiranje");
			return new ResultValidacije(true, "Predmet je spreman");
			
		} catch (Exception e) {
			System.out.println("✗ Greška pri validaciji predmeta: " + e.getMessage());
			return new ResultValidacije(false, "Greška pri validaciji predmeta: " + e.getMessage());
		}
	}
	
	// ========================================================================
	// SEKCIJA 7: VALIDACIJA DUPLIKATNIH VEŽBI
	// ========================================================================
	
	private ResultValidacije validirajDuplikatVezbe(ZahtevZaKreiranje zahtev) {
		if (!zahtev.isVezba()) {
			return new ResultValidacije(true, "Nije vežba");
		}
		
		// Proveravamo da li već postoje vežbe za ovaj predmet
		for (dogadjaj d : dogadjaji) {
			if (d.getPredmet() != null && d.getPredmet().getNaziv().equals(zahtev.getNazivPredmeta()) && d.getIsVezba()) {
				System.out.println("✗ Ovaj predmet već ima zakazane vežbe!");
				return new ResultValidacije(false, "Predmet već ima vežbe");
			}
		}
		
		System.out.println("✓ Nema duplikatnih vežbi");
		return new ResultValidacije(true, "Nema duplikata");
	}
	
	// ========================================================================
	// SEKCIJA 8: PRONALAŽENJE I VALIDACIJA UČIONICE
	// ========================================================================
	
	private ResultValidacije preuzmiBiKreiraUcionicu(ZahtevZaKreiranje zahtev) {
		try {
			ucionica odabranaUcionica = null;
			
			// Ako korisnik eksplicitno odabira učionicu
			if (!zahtev.isSistemskiRaspored() && zahtev.getNazivUcionice() != null && 
				!zahtev.getNazivUcionice().trim().isEmpty()) {
				
				// Prvo proveravamo lokalnu listu
                if(zahtev.getNazivUcionice().equals("")){
                    // pronadji
                    odabranaUcionica = odrediSalu(zahtev.trazeniKapacitet, zahtev.imaRacunara);
                } else {
                    for (ucionica u : ucionice) {
                        if (u.getNaziv().equals(zahtev.getNazivUcionice())) {
                            odabranaUcionica = u;
                            break;
                        }
                    }
                }

				
				// Ako nije u lokalnoj listi, upitaj PHP
				if (odabranaUcionica == null) {
					String parametri = "naziv=" + URLEncoder.encode(zahtev.getNazivUcionice(), "UTF-8");
					String odgovor = upitajPhp("ucionica/provera", parametri);
					
					if (odgovor.startsWith("postoji|da")) {
						String[] delovi = odgovor.split("\\|");
						if (delovi.length >= 6) {
							odabranaUcionica = new ucionica(
								Integer.parseInt(delovi[2]),
								delovi[3],
								Integer.parseInt(delovi[4]),
								"1".equals(delovi[5])
							);
							ucionice.add(odabranaUcionica);
						}
					}
				}
				
				if (odabranaUcionica == null) {
					System.out.println("✗ Učionica nije pronađena!");
					return new ResultValidacije(false, "Učionica nije pronađena");
				}
				
				// Proveravamo kapacitet i računare
				if (odabranaUcionica.getKapacitet() < zahtev.getTrazeniKapacitet()) {
					System.out.println("✗ Kapacitet učionice je mali!");
					return new ResultValidacije(false, "Kapacitet je nedovoljan");
				}
				
				if (zahtev.isImaRacunara() && !odabranaUcionica.getImaRacunare()) {
					System.out.println("✗ Učionica nema računare!");
					return new ResultValidacije(false, "Učionica nema računara");
				}
				
				zahtev.setUcionica(odabranaUcionica);
				System.out.println("✓ Učionica je validna");
				return new ResultValidacije(true, "Učionica je pronađena");
				
			} else {
				// Sistem automatski traži učionicu
				odabranaUcionica = pronađiSlobodnuUcionicu(zahtev);
				if (odabranaUcionica == null) {
					System.out.println("✗ Nema dostupne učionice!");
					return new ResultValidacije(false, "Nema dostupne učionice");
				}
				zahtev.setUcionica(odabranaUcionica);
				System.out.println("✓ Učionica je pronađena: " + odabranaUcionica.getNaziv());
				return new ResultValidacije(true, "Učionica je pronađena");
			}
			
		} catch (Exception e) {
			System.out.println("✗ Greška pri pronalaženju učionice: " + e.getMessage());
			return new ResultValidacije(false, "Greška pri pronalaženju učionice: " + e.getMessage());
		}
	}

    public static ucionica odrediSalu(int trazeniKapacitet, Boolean imaRacunara) {
		ucionica sala = null;
		
		List<ucionica> moguceSale = new ArrayList<>();
		for(ucionica u : ucionice) {
			if(u.getKapacitet() >= trazeniKapacitet && u.getImaRacunare() == imaRacunara) {
				moguceSale.add(u);
			}
		}
		moguceSale.sort((u1, u2) -> Integer.compare(u1.getKapacitet(), u2.getKapacitet()));
		
		if(moguceSale.isEmpty()) {
			System.out.println("Nije moguce naci salu koja odgovara kriterijumima.");
			return null;
		}
		
		sala = moguceSale.get(0);
		return sala;
	}
	
	private ucionica pronađiSlobodnuUcionicu(ZahtevZaKreiranje zahtev) {
		try {
			// Prvo tražimo u lokalnoj listi
			for (ucionica u : ucionice) {
				if (u.getKapacitet() >= zahtev.getTrazeniKapacitet()) {
					if (!zahtev.isImaRacunara() || u.getImaRacunare()) {
						return u;
					}
				}
			}
			
			// Ako nije u lokalnoj listi, upitaj PHP backend
			String parametri = "kapacitet=" + zahtev.getTrazeniKapacitet() +
							   "&racunari=" + (zahtev.isImaRacunara() ? "1" : "0");
			String odgovor = upitajPhp("ucionica/dostupna", parametri);
			
			if (!odgovor.isEmpty() && !odgovor.startsWith("postoji|ne")) {
				String[] zapisi = odgovor.split("~");
				if (zapisi.length > 0) {
					String[] delovi = zapisi[0].split("\\|");
					if (delovi.length >= 4) {
						ucionica u = new ucionica(
							Integer.parseInt(delovi[0]),
							delovi[1],
							Integer.parseInt(delovi[2]),
							"1".equals(delovi[3])
						);
						ucionice.add(u);
						return u;
					}
				}
			}
			
			return null;
			
		} catch (Exception e) {
			System.out.println("Greška pri pronalaženju učionice: " + e.getMessage());
			return null;
		}
	}
	
	// ========================================================================
	// SEKCIJA 9: VALIDACIJA SPECIJALNIH TIPOVA DOGAĐAJA
	// ========================================================================
	
	private ResultValidacije validirajSpecijalneTipove(ZahtevZaKreiranje zahtev) {
		// Ako nije test/kolokvijum, sve je ok
		if (!zahtev.isJePrviKolokvijum() && !zahtev.isJeDrugiKolokvijum() && 
			!zahtev.isJeZavrsni() && !zahtev.isJeVanredni() && !zahtev.isJePopravni()) {
			return new ResultValidacije(true, "Obični događaj");
		}
		
		// Odredi nedelju za specijalni događaj
		int nedeljaSpecijalna = odrediNedeljaZaSpecijalni(zahtev);
		if (nedeljaSpecijalna == 0) {
			System.out.println("✗ Ne mogu odrediti nedelju za specijalni događaj!");
			return new ResultValidacije(false, "Greška pri određivanju nedelje");
		}
		
		// Proveravamo broj testova u toj nedelji
		int brojTestova = 0;
		for (dogadjaj d : dogadjaji) {
			if (d.getNedeljaUSemestru() == nedeljaSpecijalna && d.getIsTest()) {
				brojTestova++;
			}
		}
		
		if (brojTestova >= 2) {
			System.out.println("✗ Već ima 2 testa/kolokvijuma ove nedelje!");
			return new ResultValidacije(false, "Maksimalno 2 testa po nedelji");
		}
		
		zahtev.setSpecijalnaNedalja(nedeljaSpecijalna);
		System.out.println("✓ Specijalni tip događaja je validna");
		return new ResultValidacije(true, "Specijalni tip je validna");
	}
	
	private int odrediNedeljaZaSpecijalni(ZahtevZaKreiranje zahtev) {
		if (zahtev.isJePrviKolokvijum()) return 4;
		if (zahtev.isJeDrugiKolokvijum()) return 8;
		if (zahtev.isJeZavrsni()) return 11;
		if (zahtev.isJeVanredni()) return 14;
		if (zahtev.isJePopravni()) return 10;
		return 0;
	}
	
	// ========================================================================
	// PHP BACKEND KOMUNIKACIJA
	// ========================================================================
	
	private String upitajPhp(String endpoint, String parametri) {
		try {
			String url = PHP_API_BASE + endpoint;
			HttpURLConnection veza = (HttpURLConnection) new URL(url).openConnection();
			veza.setRequestMethod("POST");
			veza.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			veza.setConnectTimeout(CONNECTION_TIMEOUT);
			veza.setReadTimeout(READ_TIMEOUT);
			veza.setDoOutput(true);
			
			try (OutputStream os = veza.getOutputStream()) {
				byte[] ulaz = parametri.getBytes("utf-8");
				os.write(ulaz, 0, ulaz.length);
			}
			
			if (veza.getResponseCode() != HttpURLConnection.HTTP_OK)
				return "postoji|ne";
			
			StringBuilder odgovor = new StringBuilder();
			try (BufferedReader citac = new BufferedReader(new InputStreamReader(veza.getInputStream()))) {
				String linija;
				for (linija = citac.readLine(); linija != null; linija = citac.readLine())
					odgovor.append(linija);
			}
			
			return odgovor.toString();
			
		} catch (Exception e) {
			System.out.println("Greška pri upitu PHP-u: " + e.getMessage());
			return "postoji|ne";
		}
	}
	
	// ========================================================================
	// PRIPREMA ZA UNOS U BAZU
	// ========================================================================
	
	public PaketZaUnosUBazu pripremiZaUnosUBazu(ZahtevZaKreiranje zahtev) {
		PaketZaUnosUBazu paket = new PaketZaUnosUBazu();
		
		paket.setProfesor(zahtev.getProfesor());
		paket.setAsistent(zahtev.getAsistent());
		paket.setPredmet(zahtev.getPredmet());
		paket.setUcionica(zahtev.getUcionica());
		paket.setTerminPocetka(zahtev.getTerminPocetka());
		paket.setTerminKraja(zahtev.getTerminKraja());
		paket.setDanUNedelji(zahtev.getDanUNedelji());
		paket.setNedeljaUSemestru(zahtev.getNedeljaUSemestru());
		paket.setVezba(zahtev.isVezba());
		paket.setTest(zahtev.isJePrviKolokvijum() || zahtev.isJeDrugiKolokvijum() || zahtev.isJeZavrsni());
		paket.setVreme(System.currentTimeMillis());
		
		// Pripremi podatke za ispis
		StringBuilder sb = new StringBuilder();
		sb.append("profesor_id|").append(paket.getProfesor().getId()).append("\n");
		sb.append("profesor_ime|").append(paket.getProfesor().getIme()).append("\n");
		sb.append("profesor_mail|").append(paket.getProfesor().getMail()).append("\n");
		
		if (paket.getAsistent() != null) {
			sb.append("asistent_id|").append(paket.getAsistent().getId()).append("\n");
			sb.append("asistent_ime|").append(paket.getAsistent().getIme()).append("\n");
		}
		
		sb.append("predmet_id|").append(paket.getPredmet().getId()).append("\n");
		sb.append("predmet_naziv|").append(paket.getPredmet().getNaziv()).append("\n");
		sb.append("predmet_kod|").append(paket.getPredmet().getKod()).append("\n");
		sb.append("ucionica_id|").append(paket.getUcionica().getId()).append("\n");
		sb.append("ucionica_naziv|").append(paket.getUcionica().getNaziv()).append("\n");
		sb.append("termin_pocetka|").append(paket.getTerminPocetka()).append("\n");
		sb.append("termin_kraja|").append(paket.getTerminKraja()).append("\n");
		sb.append("dan_u_nedelji|").append(paket.getDanUNedelji()).append("\n");
		sb.append("nedelja_u_semestru|").append(paket.getNedeljaUSemestru()).append("\n");
		sb.append("je_vezba|").append(paket.isVezba() ? "1" : "0").append("\n");
		sb.append("je_test|").append(paket.isTest() ? "1" : "0").append("\n");
		sb.append("vreme|").append(paket.getVreme());
		
		paket.setPripremljenoUPobliPodataka(sb.toString());
		
		return paket;
	}
	
	// ========================================================================
	// POMOĆNE KLASE
	// ========================================================================
	
	class ResultValidacije {
		private boolean validno;
		private String poruka;
		
		public ResultValidacije(boolean validno, String poruka) {
			this.validno = validno;
			this.poruka = poruka;
		}
		
		public boolean daLiJeValidno() { return validno; }
		public String getPoruka() { return poruka; }
	}
	
	class ZahtevZaKreiranje {
		private String nazivPredmeta, kodPredmeta, imeProfesora, mailProfesora;
		private String imeAsistenta, mailAsistenta, nazivUcionice;
		private int trazeniKapacitet, godinaStudija, danUNedelji, terminPocetka, terminKraja;
		private int nedeljaUSemestru, specialnaNedalja;
		private boolean imaRacunara, vezba, aktivan, sistemskiRaspored;
		private boolean jePrviKolokvijum, jeDrugiKolokvijum, jeZavrsni, jeVanredni, jePopravni;
		private profesor profesor, asistent;
		private predmet predmet;
		private ucionica ucionica;
		
		public String getNazivPredmeta() { return nazivPredmeta; }
		public void setNazivPredmeta(String nazivPredmeta) { this.nazivPredmeta = nazivPredmeta; }
		
		public String getKodPredmeta() { return kodPredmeta; }
		public void setKodPredmeta(String kodPredmeta) { this.kodPredmeta = kodPredmeta; }
		
		public String getImeProfesora() { return imeProfesora; }
		public void setImeProfesora(String imeProfesora) { this.imeProfesora = imeProfesora; }
		
		public String getMailProfesora() { return mailProfesora; }
		public void setMailProfesora(String mailProfesora) { this.mailProfesora = mailProfesora; }
		
		public String getImeAsistenta() { return imeAsistenta; }
		public void setImeAsistenta(String imeAsistenta) { this.imeAsistenta = imeAsistenta; }
		
		public String getMailAsistenta() { return mailAsistenta; }
		public void setMailAsistenta(String mailAsistenta) { this.mailAsistenta = mailAsistenta; }
		
		public String getNazivUcionice() { return nazivUcionice; }
		public void setNazivUcionice(String nazivUcionice) { this.nazivUcionice = nazivUcionice; }
		
		public int getTrazeniKapacitet() { return trazeniKapacitet; }
		public void setTrazeniKapacitet(int trazeniKapacitet) { this.trazeniKapacitet = trazeniKapacitet; }
		
		public int getGodinaStudija() { return godinaStudija; }
		public void setGodinaStudija(int godinaStudija) { this.godinaStudija = godinaStudija; }
		
		public int getDanUNedelji() { return danUNedelji; }
		public void setDanUNedelji(int danUNedelji) { this.danUNedelji = danUNedelji; }
		
		public int getTerminPocetka() { return terminPocetka; }
		public void setTerminPocetka(int terminPocetka) { this.terminPocetka = terminPocetka; }
		
		public int getTerminKraja() { return terminKraja; }
		public void setTerminKraja(int terminKraja) { this.terminKraja = terminKraja; }
		
		public int getNedeljaUSemestru() { return nedeljaUSemestru; }
		public void setNedeljaUSemestru(int nedeljaUSemestru) { this.nedeljaUSemestru = nedeljaUSemestru; }
		
		public int getSpecijalnaNedalja() { return specialnaNedalja; }
		public void setSpecijalnaNedalja(int specialnaNedalja) { this.specialnaNedalja = specialnaNedalja; }
		
		public boolean isImaRacunara() { return imaRacunara; }
		public void setImaRacunara(boolean imaRacunara) { this.imaRacunara = imaRacunara; }
		
		public boolean isVezba() { return vezba; }
		public void setVezba(boolean vezba) { this.vezba = vezba; }
		
		public boolean isAktivan() { return aktivan; }
		public void setAktivan(boolean aktivan) { this.aktivan = aktivan; }
		
		public boolean isSistemskiRaspored() { return sistemskiRaspored; }
		public void setSistemskiRaspored(boolean sistemskiRaspored) { this.sistemskiRaspored = sistemskiRaspored; }
		
		public boolean isJePrviKolokvijum() { return jePrviKolokvijum; }
		public void setJePrviKolokvijum(boolean jePrviKolokvijum) { this.jePrviKolokvijum = jePrviKolokvijum; }
		
		public boolean isJeDrugiKolokvijum() { return jeDrugiKolokvijum; }
		public void setJeDrugiKolokvijum(boolean jeDrugiKolokvijum) { this.jeDrugiKolokvijum = jeDrugiKolokvijum; }
		
		public boolean isJeZavrsni() { return jeZavrsni; }
		public void setJeZavrsni(boolean jeZavrsni) { this.jeZavrsni = jeZavrsni; }
		
		public boolean isJeVanredni() { return jeVanredni; }
		public void setJeVanredni(boolean jeVanredni) { this.jeVanredni = jeVanredni; }
		
		public boolean isJePopravni() { return jePopravni; }
		public void setJePopravni(boolean jePopravni) { this.jePopravni = jePopravni; }
		
		public profesor getProfesor() { return profesor; }
		public void setProfesor(profesor profesor) { this.profesor = profesor; }
		
		public profesor getAsistent() { return asistent; }
		public void setAsistent(profesor asistent) { this.asistent = asistent; }
		
		public predmet getPredmet() { return predmet; }
		public void setPredmet(predmet predmet) { this.predmet = predmet; }
		
		public ucionica getUcionica() { return ucionica; }
		public void setUcionica(ucionica ucionica) { this.ucionica = ucionica; }
	}
	
	class profesor {
		private int id;
		private String ime, mail;
		
		public profesor(int id, String ime, String mail) {
			this.id = id;
			this.ime = ime;
			this.mail = mail;
		}
		
		public int getId() { return id; }
		public String getIme() { return ime; }
		public String getMail() { return mail; }
	}
	
	class predmet {
		private int id, godinaStudija;
		private String naziv, kod;
		private profesor profesor, asistent;
		private boolean aktivan;
		
		public predmet(int id, String naziv, String kod, profesor profesor, profesor asistent, int godinaStudija, boolean aktivan) {
			this.id = id;
			this.naziv = naziv;
			this.kod = kod;
			this.profesor = profesor;
			this.asistent = asistent;
			this.godinaStudija = godinaStudija;
			this.aktivan = aktivan;
		}
		
		public int getId() { return id; }
		public String getNaziv() { return naziv; }
		public String getKod() { return kod; }
		public profesor getProfesor() { return profesor; }
		public profesor getAsistent() { return asistent; }
		public int getGodinaStudija() { return godinaStudija; }
		public boolean isAktivan() { return aktivan; }
	}
	
	class ucionica {
		private int id, kapacitet;
		private String naziv;
		private boolean imaRacunare;
		
		public ucionica(int id, String naziv, int kapacitet, boolean imaRacunare) {
			this.id = id;
			this.naziv = naziv;
			this.kapacitet = kapacitet;
			this.imaRacunare = imaRacunare;
		}
		
		public int getId() { return id; }
		public String getNaziv() { return naziv; }
		public int getKapacitet() { return kapacitet; }
		public boolean getImaRacunare() { return imaRacunare; }
	}
	
	class dogadjaj {
		private int nedeljaUSemestru;
		private predmet predmet;
		private ucionica ucionica;
		private profesor profesor, asistent;
		private boolean vezba, test, aktivan;
		
		public dogadjaj(int nedeljaUSemestru, predmet predmet, ucionica ucionica, profesor profesor, profesor asistent, boolean vezba, boolean test, boolean aktivan) {
			this.nedeljaUSemestru = nedeljaUSemestru;
			this.predmet = predmet;
			this.ucionica = ucionica;
			this.profesor = profesor;
			this.asistent = asistent;
			this.vezba = vezba;
			this.test = test;
			this.aktivan = aktivan;
		}
		
		public int getNedeljaUSemestru() { return nedeljaUSemestru; }
		public predmet getPredmet() { return predmet; }
		public ucionica getUcionica() { return ucionica; }
		public profesor getProfesor() { return profesor; }
		public profesor getAsistent() { return asistent; }
		public boolean getIsVezba() { return vezba; }
		public boolean getIsTest() { return test; }
		public boolean isAktivan() { return aktivan; }
	}
	
	class PaketZaUnosUBazu {
		private profesor profesor, asistent;
		private predmet predmet;
		private ucionica ucionica;
		private int terminPocetka, terminKraja, danUNedelji, nedeljaUSemestru;
		private boolean vezba, test;
		private long vreme;
		private String pripremljenoUPobliPodataka;
		
		public profesor getProfesor() { return profesor; }
		public void setProfesor(profesor profesor) { this.profesor = profesor; }
		
		public profesor getAsistent() { return asistent; }
		public void setAsistent(profesor asistent) { this.asistent = asistent; }
		
		public predmet getPredmet() { return predmet; }
		public void setPredmet(predmet predmet) { this.predmet = predmet; }
		
		public ucionica getUcionica() { return ucionica; }
		public void setUcionica(ucionica ucionica) { this.ucionica = ucionica; }
		
		public int getTerminPocetka() { return terminPocetka; }
		public void setTerminPocetka(int terminPocetka) { this.terminPocetka = terminPocetka; }
		
		public int getTerminKraja() { return terminKraja; }
		public void setTerminKraja(int terminKraja) { this.terminKraja = terminKraja; }
		
		public int getDanUNedelji() { return danUNedelji; }
		public void setDanUNedelji(int danUNedelji) { this.danUNedelji = danUNedelji; }
		
		public int getNedeljaUSemestru() { return nedeljaUSemestru; }
		public void setNedeljaUSemestru(int nedeljaUSemestru) { this.nedeljaUSemestru = nedeljaUSemestru; }
		
		public boolean isVezba() { return vezba; }
		public void setVezba(boolean vezba) { this.vezba = vezba; }
		
		public boolean isTest() { return test; }
		public void setTest(boolean test) { this.test = test; }
		
		public long getVreme() { return vreme; }
		public void setVreme(long vreme) { this.vreme = vreme; }
		
		public String getPripremljenoUPobliPodataka() { return pripremljenoUPobliPodataka; }
		public void setPripremljenoUPobliPodataka(String pripremljenoUPobliPodataka) { this.pripremljenoUPobliPodataka = pripremljenoUPobliPodataka; }
	}
}
