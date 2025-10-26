package main;

import java.util.ArrayList;
import java.util.List;



/*
 * 
 * Ovdje zapisujem sve fukcionalnosti koje treba da implementiram, kao i da li su zavrsene ili ne.:
 * - Kreiranje predmeta i profesora (DONE) -> Prebaciti u implementaciju istih (NOT DONE)
 * - Kreiranje sala (DONE) -> Prebaciti u implementaciju iste (NOT DONE)
 * - Kreiranje dogadjaja (DONE)
 * - Provera zauzetosti profesora u datom terminu (DONE)
 * - Provera zauzetosti sale u datom terminu (DONE)
 * - Provera da li sala ima racunare za laboratorijske vezbe (DONE)
 * - Dodavanje dogadjaja u raspored (DONE)
 * - Provera zauzetosti termina u rasporedu (DONE)
 * - Provera zauzetosti profesora u svim rasporedima (DONE)
 * - Provera zauzetosti termina za dogadjaj koji traje duze od jednog sata (DONE)
 * - Provera zauzetosti profesora za dogadjaj koji traje duze od jednog sata (DONE)
 * - Uklanjanje dogadjaja iz rasporeda (DONE)	
 * - Ispis rasporeda (DONE) -> Al treba bolje formatirati ispis (NOT DONE)
 * - Automatsko pronalaženje slobodne sale i optimalnog termina za profesora (DONE)
 * 
 * 
 */



/*
 * Uputstvo za upotrebu:
 *  - kreirajPremetIProfesora(..) je obavezan i on vrace true ili false u zavisnosti od uspesnosti kreiranja
 *  - Ukoliko vrati true, zahtev je moguc. Nastaviti sa dodavanjem dogadjaja u bazu.
 *  - Ukoliko vrati false, zahtev nije moguc. Ne dodavati dogadjaj u bazu.
 *    Dakle u PHP-u uraditi ovo: 
	 *  if(java.main.kreirajPremetIProfesora(..., sistemskiRaspored = true) == true)) {
	 * 		// Uploaduj dogadjaj u bazu
	 *  }
 *  - U koliko profesor cekira sistemskiRaspored, sistem ce automatski pronaci najbolji moguci termin i salu za njega
 *  - u koliko kreirajPremetIProfesora vrati true, moguce je dodati dogadjaj u raspored
 *  - Nakon toga je potrebno pozvati getNoviDogadjaj() da se dobije referenca na novokreirani dogadjaj
 *  
 *  Dakle u PHP-u uraditi ovo: 
	 *  if(java.main.kreirajPremetIProfesora(..., sistemskiRaspored = true) == true)) {
	 * 		this.data = java.main.getNoviDogadjaj();
	 *      this.data ce imati referencu za salu i dogadjaj za datu godinu u stilu: raspored[dan][termin] dje je termin sat u danu.
	 * 		// Uploaduj dogadjaj u bazu sa dogadjajem this.data
	 *  }
 *  
 */
public class main {

	private static List<dogadjaj> dogadjaji = new ArrayList<>();
	private static List<profesor> profesori = new ArrayList<>();
	private static List<ucionica> ucionice = new ArrayList<>();
	private static List<predmet> predmeti = new ArrayList<>();
	private static raspored[] rasporedi = new raspored[3];
	private static dogadjaj noviDogadjaj;
	
	public static void main(String[] args) {
        for (int godina = 0; godina < 3; godina++) {
        	raspored r = new raspored();
            rasporedi[godina] = r;
        }
        
        for(int i = 0; i < rasporedi.length; i++) {
			raspored r = rasporedi[i];
			r.ispisiRaspored();
		}
	}
	
	
	// Dodaj sve sale
	public static void kreirajRaspored() {
		for (int godina = 0; godina < 3; godina++) {
			raspored r = new raspored();
			rasporedi[godina] = r;
		}
	}
	
	// Kreira predmet i profesora ako ne postoje, zatim kreira dogadjaj i dodaje ga u raspored
	// Vraca true ako je uspesno kreirano, false ako nije
	
	// Kako ga koristiti:
	// Zatraziti od funkcije da proveri mogucnost unosa novog dogadjaja sa svim potrebnim podacima
	// U slucaju da su svi kriterijumi ispunjeni, funkcija kreira predmet, profesora i dogadjaj i dodaje ga na lokalnu listu
	// U slucaju true, pozivni sistem treba da doda dogadjaj u odgovarajuci raspored pozivajuci metodu dodajDogadjaj iz klase raspored
	@SuppressWarnings("unused")
	public static Boolean kreirajPremetIProfesora(String naziv, String profesorIme,
			String profesorMail, String asistentIme, String asistentMail, String imeSale, int trazeniKapacitet, Boolean imaRacunara,
			String kod, int godinaStudija, Boolean isActive, int danUNedelji, int termin, int zadnjiTermin, Boolean sistemskiRaspored) 
	{
		if(naziv == null || profesorIme == null || profesorMail == null || asistentIme == null || 
				asistentMail == null || imeSale == null || trazeniKapacitet < 0 || kod == null || 
				godinaStudija < 1 || godinaStudija > 3 || isActive == null || 
				danUNedelji > 6 || danUNedelji < 0 || termin < 0 || termin > 12 ||
				zadnjiTermin < termin || zadnjiTermin > 12) {
			System.out.println("Neispravni podaci za kreiranje predmeta i profesora.");
			return false;
		}
		if(zadnjiTermin - termin > 12) {
			System.out.println("Maksimalno trajanje jednog dogadjaja je 12 sati.");
			return false;
		}
		
		
		profesor profesor = new profesor(profesorIme, profesorMail, null);
		try {
			
			for(profesor p : profesori) {
				if(p.getIme().equals(profesorIme)) {
					profesor = p;
					break;
				}
			}
			profesori.add(profesor);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		profesor asistent = new profesor(asistentIme, asistentMail, null);
		try {
			
			for(profesor p : profesori) {
				if(p.getIme().equals(profesorIme)) {
					asistent = p;
					break;
				}
			}
			profesori.add(asistent);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(asistent.getIme().equals("")) {
			asistent = null;
		}
		
		predmet predmet = new predmet(naziv, profesor, asistent, kod, godinaStudija, isActive);
		try {		
			for(predmet p : predmeti) {
				if(p.getNaziv().equals(naziv)) {
					predmet = p;
					break;
				}
			}
			predmeti.add(predmet);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		ucionica sala = null;
		int[] vremePocetka = new int[2];
		vremePocetka[0] = danUNedelji;
		vremePocetka[1] = termin;
		int[] vremeKraja = new int[2];
		vremeKraja[0] = danUNedelji;
		vremeKraja[1] = termin;
		
		if(!sistemskiRaspored)
		{
			for(ucionica u : ucionice) {
				if(u.getNaziv().equals(imeSale)) {
					sala = u;
					break;
				} else {
					System.out.println("Ucionica sa datim imenom ne postoji.");
					return false;
				}
			}
			if(sala.getDogadjaj() != null) { // Gleda za sve godine
				System.out.println("Ucionica je zauzeta u datom terminu.");
				return false;
			}
			if(sala.getImaRacunara() == false && imaRacunara == true) {
				System.out.println("Ucionica nema racunara, nije moguce kreirati laboratorijske vezbe u njoj.");
				return false;
			}
			
			for(dogadjaj d : dogadjaji) { // provjerava da li je data sala slobodna u datom terminu
				if(d.getVremePocetka() == vremePocetka && d.getUcionica().getNaziv().equals(imeSale)) {
					System.out.println("Dati termin sale je zauzet za neki drugi dogadjaj.");
					return false;
				}
			}
			
			// Za dogadjaje koji traju duze od jednog sata!
			
			if(zadnjiTermin != 0)
			{
				for(raspored r : rasporedi) { // provjerava dogadjaje za sve rasporede za datu salu
					for(int t = termin; t <= zadnjiTermin; t++) {
						if(r.pretraziDogadjaj(danUNedelji, t) != null && r.pretraziDogadjaj(danUNedelji, t).getUcionica().getNaziv().equals(imeSale)) {
							System.out.println("Dati termin je zauzet za neki drugi duzi dogadjaj.");
							return false;
						}
					}
				}
			} else {
				vremeKraja = null;
			}
		} else {
			// U slucaju da profesor zeli da mu sistem automatski nadje salu i najbolji raspored
			// koji ce ga rasteretiti
			// Sistem gleda raspored profesora i optimalno trazi dan kada profesor ne radi
			// Ako ne postoji takav dan, trazi najmanje opterecen dan
			
			// Prvo trazimo sve dogadjaje koje profesor ima
		    List<dogadjaj> profesoriDogadjaji = new ArrayList<>();
		    for(dogadjaj d : dogadjaji) {
		    	if(d.getProfesor().getIme().equals(profesor.getIme())) {
		    		profesoriDogadjaji.add(d);
		    	}
		    }
		    
		    // zatim ih rasporedjuje i inkrementira zauzete dane 
		    int[] najboljiDani = new int[5];
		    if(profesoriDogadjaji.size() == 0) {
		    	// profesor nema dogadjaja, moze izabrati bilo koji dan
		    	najboljiDani = new int[] {0, 0, 0, 0, 0};
		    } else {
		    	for(raspored r : rasporedi) {
			    	 for(int i = 0; i < 5; i++) {
				    	int brojac = 0;
				    	// Sad ce proci kroz sve termine u danu i brojati koliko je zauzetih termina
				    	for(int j = 0; j < 13; j++) {
				    		for(dogadjaj d : profesoriDogadjaji) {
				    			if(d.getVremePocetka() == r.getTermin(i, j).getVremePocetka()) {
				    				brojac++;
				    			}
				    		}
				    	}
				    	najboljiDani[i] = brojac;
				    }
		    	 }
		    }
		    
		    // Prvo cemo da izbacimo sale koje nam ne odgovaraju
		    List<ucionica> moguceSale = new ArrayList<>();
		    for(ucionica u : ucionice) {
		    	if(u.getKapacitet() >= trazeniKapacitet && u.getImaRacunara() == imaRacunara) {
		    		moguceSale.add(u);
		    	}
		    }
		    // sad cemo ih sortirati po kapacitetu
		    moguceSale.sort((u1, u2) -> Integer.compare(u1.getKapacitet(), u2.getKapacitet()));
		    
		    // sad cemo proci kroz najbolje dane i traziti slobodnu salu
		    int prosliNajboljiDan = najboljiDani[0];
		    if(zadnjiTermin != 0) {
		    	// U ovom slucaju provjeriti da li je sala slobodna za sve trazene termine svih godina
		    	for(raspored ras : rasporedi) {
				    
			    	for(int dan : najboljiDani) {
				    	for(ucionica u : moguceSale) {
				    		Boolean salaSlobodna = true;
				    		// provjeri da li je sala slobodna u datom terminu
				    		for(int t = termin; t <= zadnjiTermin; t++) {
				    			if(ras.pretraziDogadjaj(dan, t) != null && ras.pretraziDogadjaj(dan, t).getUcionica().getNaziv().equals(u.getNaziv())) {
				    				salaSlobodna = false;
				    				break;
				    			}
				    		}
				    		if(salaSlobodna && prosliNajboljiDan >= dan) {
				    			sala = u;
				    			//  U ovom slucaju smo nasli najbolju slobodnu salu, mozemo izaci iz petlje
				    			break;
				    		}
				    	}
				    	prosliNajboljiDan = dan;
				    }
		    	}
		    } else {
		        // U ovom slucaju provjeriti da li je sala slobodna za dati termin date
		    	for(raspored ras : rasporedi) {
			    	for(int dan : najboljiDani) {
					    for(ucionica u : moguceSale) {
				    		Boolean salaSlobodna = true;
				    		// provjeriti da li je sala slobodna za sve date termine
				    		for(int t = termin; t <= zadnjiTermin; t++) {
				    			// provjeri da li je sala slobodna u datom termik
					    		if(ras.pretraziDogadjaj(dan, t) != null && ras.pretraziDogadjaj(dan, t).getUcionica().getNaziv().equals(u.getNaziv())) {
				    				salaSlobodna = false;
				    			}
				    		}
				    		if(salaSlobodna && prosliNajboljiDan >= dan) {
				    			sala = u;
				    			break;
				    		}
				    	}
				    	if(sala != null) {
				    		break;
				    	}
				    	prosliNajboljiDan = dan;
			    }
		    	}
		    }
			    
			// 	ako nije nasao nijednu salu, vrati false
			if(sala == null) {
				System.out.println("Nije moguce naci slobodnu salu u trazenom terminu.");
				return false;
			}
			
		}
			
		if(sala == null) {
			System.out.println("Nije moguce naci slobodnu salu u trazenom terminu.");
			return false;
		}
		
		// if statement za slobodan dan check
		
		predmeti.add(predmet);
		profesor.dodajPredmet(predmet);
		asistent.dodajPredmet(predmet);
		dogadjaj ovajDogadjaj = new dogadjaj(predmet, sala, vremePocetka, vremeKraja, profesor, false);
		dogadjaji.add(ovajDogadjaj);
		noviDogadjaj = ovajDogadjaj;
		return true;
	}

	public static void kreirajSale() {
        
        // Amfiteatri
		ucionice.add(new ucionica("Amfiteatar A", 150, false));
		ucionice.add(new ucionica("Amfiteatar B", 120, false));
		ucionice.add(new ucionica("Amfiteatar C", 100, false));
		ucionice.add(new ucionica("Amfiteatar D", 120, false));
		ucionice.add(new ucionica("Amfiteatar E", 140, false));
		ucionice.add(new ucionica("Amfiteatar F", 100, false));


        // Učionice sa računarima
		ucionice.add(new ucionica("Lab 1", 30, true));
		ucionice.add(new ucionica("Lab 2", 30, true));
		ucionice.add(new ucionica("Lab 3", 25, true));
        ucionice.add(new ucionica("Lab 4", 25, true));
        ucionice.add(new ucionica("Lab 5", 25, true));
        
        // Obične učionice
        ucionice.add(new ucionica("Učionica 101", 40, false));
        ucionice.add(new ucionica("Učionica 102", 40, false));
        ucionice.add(new ucionica("Učionica 201", 35, false));
        ucionice.add(new ucionica("Učionica 202", 35, false));
        ucionice.add(new ucionica("Učionica 203", 30, false));
        ucionice.add(new ucionica("Učionica 301", 30, false));
        ucionice.add(new ucionica("Učionica 302", 25, false));
        ucionice.add(new ucionica("Seminarska sala", 20, false));
        
		for (ucionica u : ucionice) {
				System.out.println("Kreirana učionica: " + u.getNaziv() + ", Kapacitet: " + u.getKapacitet() + ", Ima računara: " + u.getImaRacunara());
		}
	}

	public static raspored[] getRasporedi() {
		return rasporedi;
	}
	
	public static dogadjaj getDogadjaj() {
		
		return noviDogadjaj;
	}
}

class ucionica {
	
	private String naziv;
	private int kapacitet;
	private Boolean imaRacunara;
	private dogadjaj dogadjaj;
	
	public ucionica(String naziv, int kapacitet, Boolean imaRacunara) {
		this.naziv = naziv;
		this.kapacitet = kapacitet;
		this.imaRacunara = imaRacunara;
	}
	
	public void dodajDogadjaj(dogadjaj dogadjaj) {
		this.dogadjaj = dogadjaj;
	}
	public void ukloniDogadjaj() {
		this.dogadjaj = null;
	}
	
	public ucionica dajNajoptimalnijuUcionicu(int kapacitet, List<ucionica> ucionice) {
		
		ucionica najboljaSala = null;
		int najboljiKapacitet = Integer.MAX_VALUE;
		int mojKapacitet = kapacitet;
		for(ucionica u : ucionice) {
			// Prvo provjeri da li je sala okupirana
			if(u.getKapacitet() >= mojKapacitet && u.getKapacitet() < najboljiKapacitet) {
				
				najboljiKapacitet = u.getKapacitet();
			}
		}
		return najboljaSala;
	}
	public dogadjaj getDogadjaj() {return dogadjaj;}
	public String getNaziv() {return naziv;}
	public int getKapacitet() {return kapacitet;}
	public Boolean getImaRacunara() {return imaRacunara;}
	
}

class predmet {
	 private String naziv, kod;
	 private profesor profesor, asistent;
	 private int godinaStudija;
	 private Boolean isActive;
	 public predmet(String naziv, profesor profesor, profesor asistent, String kod, int godinaStudija, Boolean isActive) {
		 this.naziv = naziv;
		 this.profesor = profesor;
		 this.asistent = asistent;
		 this.godinaStudija = godinaStudija;
		 this.kod = kod;
		 this.isActive = isActive;
	 }
	 
	 public String getNaziv() {return naziv;}
	 public String getKod() {return kod;}
	 public profesor getProfesor() {return profesor;}
	 public profesor getAsistent() {return asistent;}
	 public int getGodinaStudija() {return godinaStudija;}
	 public Boolean getIsActive() {return isActive;}
	 
}

class profesor {
	private String ime, mail;
	private List<predmet> predmeti = new ArrayList<>();

	public profesor(String ime, String mail, predmet predmet) {
		this.ime = ime;
		this.mail = mail;
		
		if(predmet != null)
			predmeti.add(predmet);
	}
	
	public void dodajPredmet(predmet predmet) {
		predmeti.add(predmet);
	}
	
	public String getIme() {return ime;}
	public String getMail() {return mail;}
	public List<predmet> getPredmeti() {return predmeti;}
	
}

class dogadjaj {
	private predmet predmet;
	private ucionica ucionica;
	private int[] vremePocetka; // dan, termin
	private int[] vremeKraja = null; // dan, termin
	private profesor profesor;
	private Boolean slobodanDan;
	private Boolean isOnline;
	private Boolean isCanceled;
	
	public dogadjaj(predmet predmet, ucionica ucionica, int[] vremePocetka, int[] vremeKraja, profesor profesor, Boolean slobodanDan) {
		this.predmet = predmet;
		this.ucionica = ucionica;
		this.vremePocetka = vremePocetka;
		this.vremeKraja = vremeKraja; // ako je null, onda je trajanje 1 sat
		this.profesor = profesor;
		this.slobodanDan = slobodanDan;
	}
	
	public String toString() {
		return "Predmet: " + predmet + ", Ucionica: " + ucionica + ", Profesor: " + profesor;
	}
	
	public predmet getPredmet() {return predmet;}
	public ucionica getUcionica() {return ucionica;}
	public int[] getVremePocetka() {return vremePocetka;}
	public int[] getVremeKraja() {return vremeKraja;}
	public profesor getProfesor() {return profesor;}
	public Boolean getSlobodanDan() {return slobodanDan;}
	public Boolean getIsOnline() {return isOnline;}
	public Boolean getIsCanceled() {return isCanceled;}
	
	public void ukloniDogadjaj() {
		this.isCanceled = true;
		if(vremeKraja != null)
		{
			
			for(int i = vremePocetka[1]; i <= vremeKraja[1]; i++) { // za sve termine izmedju pocetka i kraja
				if(vremePocetka[0] == vremeKraja[0]) // ako je isti dan u pitanju
				{
					ucionica.ukloniDogadjaj();
				}
				ucionica.ukloniDogadjaj();
			}
		}
		else
		{
			ucionica.ukloniDogadjaj();
		}
			
	}
	
}

class raspored {
	private dogadjaj[][] nedelja = new dogadjaj[7][13];
    // za [x][y] x - dan u nedelji, y - redni broj termina u danu
    // y - 0 (08:00-09:00), 1 (09:00-10:00), 2 (10:00-11:00), 3 (11:00-12:00), 4 (12:00-13:00),
    // 5 (13:00-14:00), 6 (14:00-15:00), 7 (15:00-16:00), 8 (16:00-17:00), 9 (17:00-18:00),
    // 10 (18:00-19:00), 11 (19:00-20:00), 12 (20:00-21:00)
    
    public Boolean dodajDogadjaj(dogadjaj noviDogadjaj, int dan, int termin, int zadnjiTermin) {
    	if(nedelja[dan][termin] != null) {
			System.out.println("Termin je zauzet, izaberite drugi termin.");
			return false;
		}
    	
    	// sad proveri da li je profesor slobodan u tom terminu iz svih godina
    	raspored[] rasporedi = main.getRasporedi();
    	for(int k = 0; k < rasporedi.length; k++) {
    		raspored r = rasporedi[k];
				if(r.getTermin(dan, termin) != null) {
					if(r.getTermin(dan, termin).getProfesor().getIme().equals(noviDogadjaj.getProfesor().getIme())) {
						System.out.println("Profesor nije slobodan u datom terminu, izaberite drugi termin.");
						return false;
				}
			}
		}
    	// U slucaju da dogadjaj traje duze od jednog sata, proveri da li su svi termini izmedju slobodni
    	if(zadnjiTermin > 0) {
			for(int t = termin; t <= zadnjiTermin; t++) {
				if(nedelja[dan][t] != null) {
					System.out.println("Neki od termina su zauzeti, izaberite druge termine.");
					return false;
				}
			}
			// sad proverite da li je profesor zauzet u terminima izmedju
	    	rasporedi = main.getRasporedi();
	    	for(int i = 0; i < rasporedi.length; i++) {
	    		raspored r = rasporedi[i];
	    		for(int t = termin; t <= zadnjiTermin; t++) {
					if(r.getTermin(dan, t) != null) {
						if(r.getTermin(dan, termin).getProfesor().getIme().equals(noviDogadjaj.getProfesor().getIme())) {
							System.out.println("Profesor nije slobodan u nekom od datih termina iz drugih godina,"
									+ "izaberite drugi termin.");
							return false;
						}
					}
				}
    		}
    	}

		nedelja[dan][termin] = noviDogadjaj;
		if(zadnjiTermin > 0)
		{
			for(int t = termin; t <= zadnjiTermin; t++) {
				nedelja[dan][t] = noviDogadjaj;
			}
		}
		
		return true;
	}
    public dogadjaj pretraziDogadjaj(int dan, int termin) {
		return nedelja[dan][termin];
    }
    
    public void ukloniDogadjaj(int dan, int termin) {
		nedelja[dan][termin] = null;
    }
    
    
    public void ispisiRaspored() {
		for(int i = 0; i < nedelja.length; i++) {
			System.out.println("Dan " + (i+1) + ":");
			for(int j = 0; j < nedelja[i].length; j++) {
				if(nedelja[i][j] != null) {
					System.out.println("  Termin " + (j+1) + ": " + nedelja[i][j].toString());
				} else if (nedelja[i][j].getSlobodanDan() == true) {
					System.out.println("  Slobodan dan");
				} else {
					System.out.println("  Termin " + (j+1) + ": Slobodan");
				}
			}
		}
	}
    
    public dogadjaj getTermin(int dan, int termin) {
		return nedelja[dan][termin];
	}
    
}