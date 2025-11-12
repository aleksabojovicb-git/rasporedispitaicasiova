package main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * SISTEM ZA RASPORED FAKULTETA
 * 
 * Funkcionalnosti:
 * - Kreiranje predmeta i profesora (DONE)
 * - Kreiranje sala (DONE)
 * - Kreiranje dogadjaja (DONE)
 * - Provera zauzetosti profesora (DONE)
 * - Provera zauzetosti sale (DONE)
 * - Dodavanje dogadjaja u raspored (DONE)
 * - Uklanjanje dogadjaja (DONE)
 * - Ispis rasporeda (DONE)
 * - Automatsko pronalaženje slobodne sale (DONE)
 * - Popunjavanje rasporeda sa praznicima (NOVO)
 */

public class main {

	private static List<dogadjaj> dogadjaji = new ArrayList<>();
	private static List<profesor> profesori = new ArrayList<>();
	private static List<ucionica> ucionice = new ArrayList<>();
	private static List<predmet> predmeti = new ArrayList<>();
	private static List<semestar> semestriPoGodini = new ArrayList<>();
	private static List<raspored> rasporedi = new ArrayList<>();
	private static dogadjaj noviDogadjaj;
	
	// Praznici - nedelje, dani (0 = ponedeljak, 6 = nedelja)
	private static int[][] praznici = {
		{2, 4}, // Treca nedelja, petak
		{5, 0}, // Sesta nedelja, ponedeljak
		{10, 2}, // Deseta nedelja, sreda
	};
	
	public static void main(String[] args) {
		System.out.println("========== INICIJALIZACIJA SISTEMA ==========\n");
		
		// 1. Kreiraj sve rasporede
		kreirajRasporede();
		System.out.println("✓ Kreirani rasporedi za 13 nedelja\n");
		
		// 4. Popuni rasporede sa praznicima i slobodnim danima
		popuniRasporediSaPraznicima();
		System.out.println("✓ Rasporedi popunjeni sa praznicima i slobodnim danima\n");
		

		// 6. Ispis rasporeda
		System.out.println("========== FINALNI RASPOREDI ==========\n");
		ispisiSveRasporede();
	}
	
	/**
	 * Popunjava sve rasporede sa praznicima i označava slobodne dane
	 */
	public static void popuniRasporediSaPraznicima() {
		System.out.println("Popunjavanje rasporeda sa praznicima...\n");
		
		// Definiši slobodne dane po danima u nedelji (0-6)
		// Recimo da je nedelja (6) uvek slobodan dan
		int[] slobodniDani = {6}; // Nedelja
		
		for (int nedelja = 0; nedelja < rasporedi.size(); nedelja++) {
			raspored r = rasporedi.get(nedelja);
			
			// Označi sve nedelje kao slobodne dane
			for (int dan : slobodniDani) {
				r.setSlobodanDan(true, dan);
				System.out.println("  Nedelja " + (nedelja + 1) + ", Dan " + (dan + 1) + ": SLOBODAN DAN");
			}
			
			// Označi praznike
			for (int[] praznik : praznici) {
				if (praznik[0] == nedelja) {
					r.setSlobodanDan(true, praznik[1]);
					String[] dani = {"Ponedeljak", "Utorak", "Sreda", "Četvrtak", "Petak", "Subota", "Nedelja"};
					System.out.println("  Nedelja " + (nedelja + 1) + ", " + dani[praznik[1]] + ": PRAZNIK");
				}
			}
		}
	}
	
	public static void kreirajRasporede() {

		for(int i = 0; i < 3; i++){
			semestar s = new semestar();
			semestriPoGodini.add(s);
		}
		
		for(semestar s : semestriPoGodini){
			for(int j = 0; j < 15; j++){
				s.addNedelja(new raspored());
			}
		}
	}
	
	/**
	 * Dodaje dogadjaj u raspored date nedelje
	 */
	public static void dodajDogadjajURaspored(dogadjaj d, int dan, int termin, int zadnjiTermin, int nedelja) {
		if (rasporedi.get(nedelja).dodajDogadjaj(d, dan, termin, zadnjiTermin)) {
			System.out.println("  Dogadjaj dodat u nedelju " + (nedelja + 1));
		} else {
			System.out.println("  ✗ Nije moguće dodati dogadjaj u raspored");
		}
	}
	
	/**
	 * Ispis svih rasporeda
	 */
	public static void ispisiSveRasporede() {
		for (int i = 0; i < rasporedi.size(); i++) {
			System.out.println("\n--- NEDELJA " + (i + 1) + " ---");
			rasporedi.get(i).ispisiRaspored();
		}
	}
	
	/**
	 * Originalna funkcija sa poboljšanjima
	 */
	public static Boolean mogucnostDodavanjaDogadjaja(String naziv, String profesorIme,
			String profesorMail, String asistentIme, String asistentMail, String imeSale,
			int trazeniKapacitet, Boolean imaRacunara, Boolean jeVjezba,
			String kod, int godinaStudija, Boolean isActive, int danUNedelji, int termin,
			int zadnjiTermin, Boolean sistemskiRaspored, int nedeljaUSemestru,
			Boolean jePrviKolokvijum, Boolean jeDrugiKolokvijum, Boolean jeZavrsni, Boolean jeVanredni, Boolean jePopravni) 
	{
		// Checkeri i setteri
		
		if(naziv == null || profesorIme == null || profesorMail == null || asistentIme == null || 
				asistentMail == null || imeSale == null || trazeniKapacitet < 0 || kod == null || 
				godinaStudija < 0 || godinaStudija > 2 || isActive == null || 
				danUNedelji > 6 || danUNedelji < 0 || termin < 0 || termin > 12 ||
				zadnjiTermin < termin || zadnjiTermin > 12 || nedeljaUSemestru < 0 || nedeljaUSemestru > 15) {
			System.out.println("Neispravni podaci za kreiranje predmeta i profesora.");
			return false;
		}
		Boolean jeTest = (jePrviKolokvijum == true || jeDrugiKolokvijum == true || jeZavrsni == true) ? true : false;
		if(semestriPoGodini.size() != 3) kreirajRasporede();
		
		if(semestriPoGodini.get(godinaStudija).getNedelja(nedeljaUSemestru).getSlobodanDan(danUNedelji) == true) {
			System.out.println("Slobodnim danom se odmara!");
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
			if(!profesori.contains(profesor))
				profesori.add(profesor);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		profesor asistent = null;
		if (asistentIme != null && !asistentIme.equals("")) {
			asistent = new profesor(asistentIme, asistentMail, null);
			try {
				for(profesor p : profesori) {
					if(p.getIme().equals(asistentIme)) {
						asistent = p;
						break;
					}
				}
				if(!profesori.contains(asistent))
					profesori.add(asistent);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		predmet predmet = new predmet(naziv, profesor, asistent, kod, godinaStudija, isActive);
		try {		
			for(predmet p : predmeti) {
				if(p.getNaziv().equals(naziv)) {
					predmet = p;
					break;
				}
			}
			if(!predmeti.contains(predmet))
				predmeti.add(predmet);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// Gledamo da li smo dodali vjezbe za predmet koji smo vec dodali vjezbe
		if(jeVjezba) {
			for(dogadjaj d : semestriPoGodini.get(godinaStudija).getNedelja(termin).SviDogadjaji()) {
				if(d.getPredmet() == predmet && d.getIsVjezbe()) {
					System.out.println("Ovaj predmet vec ima unesene vjezbe!");
					return false;
				}
			}
		}
			
		
		ucionica sala = null;
		int[] vremePocetka = new int[2];
		vremePocetka[0] = danUNedelji;
		vremePocetka[1] = termin;
		int[] vremeKraja = new int[2];
		vremeKraja[0] = danUNedelji;
		vremeKraja[1] = zadnjiTermin;
		
		// end of checkeri i setteri
		if(jePrviKolokvijum || jeDrugiKolokvijum || jeZavrsni || jeVanredni) {
			// Odrzavaju se u vrijeme vjezbi
			// Asistenti su prisutni tokom njigovog odrzavanja
			// Prvi kolokvijumi se mogu odrzati posle 4te nedelje i sami se generisu
			// Drugi posle 8me,
			// Zavrsni na 10oj
			// Popravni idk
			int nedeljaDogadjaja = (jePrviKolokvijum == true) ? 4 : (jeDrugiKolokvijum == true) ? 7 : (jeZavrsni == true) ? 11 : (jeVanredni) ? 14 : 0;			
			if(jePopravni) nedeljaDogadjaja = 10;
			if(nedeljaDogadjaja == 0) {
				System.out.println("Jesi ti krsten?");
			}
			int odabranaNedelja = 0;
			// Prvo gledamo koja nedelja je manje zauzeta
			// Samo u ovim slucajevima hocemo da damo maksimalni raspored kolokvijuma
			if(jePrviKolokvijum || jeDrugiKolokvijum || jeZavrsni) {
				int[] nedeljniKolokvijumi = {0, 0, 0};
				nedeljniKolokvijumi[0] = semestriPoGodini.get(godinaStudija).getNedelja(nedeljaUSemestru).brojTestova();
				nedeljniKolokvijumi[1] = semestriPoGodini.get(godinaStudija).getNedelja((nedeljaUSemestru + 1)).brojTestova();
				nedeljniKolokvijumi[2] = semestriPoGodini.get(godinaStudija).getNedelja((nedeljaUSemestru + 2)).brojTestova();
				odabranaNedelja = (nedeljniKolokvijumi[0] < 2) ? nedeljaDogadjaja :
					(nedeljniKolokvijumi[1] < 2) ? (nedeljaDogadjaja + 1) : (nedeljaDogadjaja + 2);
			} else {
				odabranaNedelja = nedeljaDogadjaja;
			}
			
			// Sad smo odredili nedelju u kojoj ce se odrzavati test.
			
			dogadjaj ponovljenDogadjaj = null;
			if(jePrviKolokvijum || jeDrugiKolokvijum || jeZavrsni) {
				for(int i = 0; i < 4; i++) {
					for(dogadjaj d : semestriPoGodini.get(godinaStudija).getNedelja(odabranaNedelja).SviDogadjaji()) {
						if(d.getPredmet() == predmet && d.getIsVjezbe() && d.getSlobodanDan() == true) {
							odabranaNedelja = (nedeljaDogadjaja < odabranaNedelja) ? odabranaNedelja-- : odabranaNedelja++;
							break;
						}
						if(ponovljenDogadjaj == d && d != null) {
							// Ako je isti, znaci da dogadjaj traje vise od sat vremena
							// Interni dogovor: trajanje vjezbe = trajanje 
							vremeKraja = d.getVremeKraja();
						}
						if(d.getPredmet() == predmet && d.getIsVjezbe()) {
							// U slucaju da smo nasli termin vjezbi, 
							sala = d.getUcionica();
							vremePocetka = d.getVremePocetka();
							vremeKraja = d.getVremeKraja();
							ponovljenDogadjaj = d;
						}
					}
					if(i == 3) {
						raspored r = semestriPoGodini.get(godinaStudija).getNedelja(odabranaNedelja);
						if(r.getSlobodanDan(5) == false);
						{
							// Subota je slobodan dan!
							// Racunamo da je profesor slobodan tog dana.. 
							sala = odrediSalu(trazeniKapacitet, imaRacunara);
							for(int t = 0; t < 13; t++) {
								// Proveravamo slobodan termin sale
								if(r.pretraziDogadjaj(6,t) == null) {
									int[] dog = {6,t};
									vremePocetka = dog;
									dog[1]++;
									vremeKraja = dog;
									break;
								}
							}
						} 
					}
				}
			}
			
			if(jeVanredni || jePopravni) {
				int errorNum = 0;
				for(int i = 0; i < 4; i++) {
					for(dogadjaj d : semestriPoGodini.get(godinaStudija).getNedelja(odabranaNedelja).SviDogadjaji()) {
						if(ponovljenDogadjaj == d && d != null) {
							// Ako je isti, znaci da se dogadjaj Ponavlja
							vremeKraja = d.getVremeKraja();
						}
						if(d.getPredmet() == predmet && !d.getIsVjezbe()) {
							// U slucaju da smo nasli termin vjezbi, 
							sala = d.getUcionica();
							vremePocetka = d.getVremePocetka();
							vremeKraja = d.getVremeKraja();
							ponovljenDogadjaj = d;
						}
					}
					if(errorNum == 3) {
						
						raspored r = semestriPoGodini.get(godinaStudija).getNedelja(odabranaNedelja);
						if(r.getSlobodanDan(5) == false);
						{
							// Subota je slobodan dan!
							// Racunamo da je profesor slobodan tog dana.. 
							sala = odrediSalu(trazeniKapacitet, imaRacunara);
						} 
						// Sad ne forsiramo nista jer hocemo da profesor sam bira zeljeni termin
					}
				}
			}
			// 'raspored' klasa koja predstavlja nedelju u semestru vec racuna koliko testova ima odredjena nedelja.
			// U slucaju da ih vec ima 2, 
		} else {
			if(!sistemskiRaspored) {
				for(ucionica u : ucionice) {
				    if(u.getNaziv().equals(imeSale)) {
				        sala = u;
				        break;
				    }
				}
				if(sala == null) {
				    System.out.println("Ucionica sa datim imenom ne postoji.");
				    return false;
				}
	
				if(sala.getImaRacunara() == false && imaRacunara == true) {
					System.out.println("Ucionica nema racunara, nije moguce kreirati laboratorijske vezbe u njoj.");
					return false;
				}
				
				if(zadnjiTermin > termin) {
					vremeKraja[1] = zadnjiTermin;
				} else {
					vremeKraja = null;
				}
			} else {
				List<ucionica> moguceSale = new ArrayList<>();
				for(ucionica u : ucionice) {
					if(u.getKapacitet() >= trazeniKapacitet && u.getImaRacunara() == imaRacunara) {
						moguceSale.add(u);
					}
				}
				moguceSale.sort((u1, u2) -> Integer.compare(u1.getKapacitet(), u2.getKapacitet()));
				
				if(moguceSale.isEmpty()) {
					System.out.println("Nije moguce naci salu koja odgovara kriterijumima.");
					return false;
				}
				
				sala = moguceSale.get(0);
				if(zadnjiTermin > termin) {
					vremeKraja[1] = zadnjiTermin;
				} else {
					vremeKraja = null;
				}
			}
		}
		
		if(sala == null) {
			System.out.println("Nije moguce naci slobodnu salu u trazenom terminu.");
			return false;
		}
		
		dogadjaj ovajDogadjaj = new dogadjaj(predmet, sala, vremePocetka, vremeKraja, profesor, asistent, false, jeVjezba, jeTest);
		dogadjaji.add(ovajDogadjaj);
		noviDogadjaj = ovajDogadjaj;
		return true;
	}



	public static List<raspored> getRasporedi() {
		return rasporedi;
	}
	
	public static dogadjaj getDogadjaj() {
		return noviDogadjaj;
	}
	
	public static ucionica odrediSalu(int trazeniKapacitet, Boolean imaRacunara) {
		ucionica sala = null;
		
		List<ucionica> moguceSale = new ArrayList<>();
		for(ucionica u : ucionice) {
			if(u.getKapacitet() >= trazeniKapacitet && u.getImaRacunara() == imaRacunara) {
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
	
	public Boolean slobodanTokomDana(int nedelja, int[] danUNedelji, List<semestar> semestriPoGodini){
		dogadjaj d = null;
		for(semestar s : semestriPoGodini){
			if((d = s.getNedelja(nedelja).getSpecificDogadjaj(danUNedelji)) != null){
				if(d.getAssistent() == this) return false;
			}
		}
		return true;
	}
}

class dogadjaj {
	private predmet predmet;
	private ucionica ucionica;
	private int[] vremePocetka;
	private int[] vremeKraja;
	private profesor profesor, asistent;
	private Boolean slobodanDan;
	private Boolean isOnline;
	private Boolean isCanceled;
	private Boolean isTest;
	private Boolean vjezbe;
	public dogadjaj(predmet predmet, ucionica ucionica, int[] vremePocetka, int[] vremeKraja, profesor profesor, profesor asistent, Boolean slobodanDan, Boolean vjezbe, Boolean isTest) {
		this.predmet = predmet;
		this.ucionica = ucionica;
		this.vremePocetka = vremePocetka;
		this.vremeKraja = vremeKraja;
		this.profesor = profesor;
		this.slobodanDan = slobodanDan;
		this.asistent = asistent;
		this.vjezbe = vjezbe;
		this.isTest = isTest;
	}
	
	public String toString() {
		String vrijeme = vremePocetka[1] + ":00";
		if (vremeKraja != null) {
			vrijeme += "-" + vremeKraja[1] + ":00";
		}
		return predmet.getNaziv() + " u " + ucionica.getNaziv() + " (" + vrijeme + ")";
	}
	
	public predmet getPredmet() {return predmet;}
	public ucionica getUcionica() {return ucionica;}
	public int[] getVremePocetka() {return vremePocetka;}
	public int[] getVremeKraja() {return vremeKraja;}
	public profesor getProfesor() {return profesor;}
	public Boolean getSlobodanDan() {return slobodanDan;}
	public Boolean getIsOnline() {return isOnline;}
	public Boolean getIsCanceled() {return isCanceled;}
	public profesor getAssistent() {return asistent;}
	public Boolean getIsVjezbe() {return vjezbe;}
	public Boolean getIsTest() {return isTest;}
	
	public void ukloniDogadjaj() {
		this.isCanceled = true;
		if(vremeKraja != null) {
			for(int i = vremePocetka[1]; i <= vremeKraja[1]; i++) {
				if(vremePocetka[0] == vremeKraja[0]) {
					ucionica.ukloniDogadjaj();
				}
			}
		} else {
			ucionica.ukloniDogadjaj();
		}
	}
	
	public Boolean profesorZauzetUTerminu(profesor p){
		if(profesor == null) return false;
		if(profesor == p) return true;
		return false;
	}
}

class raspored {
	private dogadjaj[][] nedelja = new dogadjaj[7][13];
	private List<dogadjaj> sviDogadjaji = new ArrayList<dogadjaj>();
	private Boolean[] slobodanDan = new Boolean[7];
    private int brTestovaOveNedelje = 0;
    
	public Boolean dodajDogadjaj(dogadjaj noviDogadjaj, int dan, int termin, int zadnjiTermin) {
    	if(nedelja[dan][termin] != null) {
			System.out.println("Termin je zauzet!");
			return false;
		}
    	
    	if(brTestovaOveNedelje >= 2 && noviDogadjaj.getIsTest() == true) 
    	{
    		System.out.println("Vec je unesen maksimalni broj testova i kolokvijuma ove nedelje!");
    		return false;
    	} else {
    		brTestovaOveNedelje++;
    	}
    	
    	List<raspored> rasporedi = main.getRasporedi();
    	for(int k = 0; k < rasporedi.size(); k++) {
    		raspored r = rasporedi.get(k);
    		dogadjaj terminDogadjaj = r.getTermin(dan, termin);
    		if(terminDogadjaj != null) {
    		    if(terminDogadjaj.getProfesor().getIme().equals(noviDogadjaj.getProfesor().getIme())) {
					System.out.println("Profesor nije slobodan!");
					return false;
			}
			}
		}
    	
    	if(zadnjiTermin > termin) {
			for(int t = termin; t <= zadnjiTermin; t++) {
				if(nedelja[dan][t] != null) {
					System.out.println("Neki termini su zauzeti!");
					return false;
				}
			}
			rasporedi = main.getRasporedi();
	    	for(int i = 0; i < rasporedi.size(); i++) {
	    		raspored r = rasporedi.get(i);
	    		for(int t = termin; t <= zadnjiTermin; t++) {
	    			dogadjaj terminDogadjaj = r.getTermin(dan, t);
	        		if(terminDogadjaj != null) {
	        		    if(terminDogadjaj.getProfesor().getIme().equals(noviDogadjaj.getProfesor().getIme())) {
						System.out.println("Profesor nije slobodan u sve termine!");
						return false;
	        		    }
					}
				}
	    	}
		}

		nedelja[dan][termin] = noviDogadjaj;
		if(zadnjiTermin > termin) {
			for(int t = termin; t <= zadnjiTermin; t++) {
				nedelja[dan][t] = noviDogadjaj;
			}
		}
		
		sviDogadjaji.add(noviDogadjaj);
		
		return true;
	}
    
	public List<dogadjaj> SviDogadjaji(){
		return sviDogadjaji;
	}
	public dogadjaj pretraziDogadjaj(int dan, int termin) {
		return nedelja[dan][termin];
    }
    
	public void ukloniDogadjaj(int dan, int termin) {
		nedelja[dan][termin] = null;
    }
    
	public dogadjaj[][] getDogadjaj(){
    	return nedelja;
    }
	
	public dogadjaj getSpecificDogadjaj(int[] termin) {
		return nedelja[termin[0]][termin[1]];
	}
    
	public void ispisiRaspored() {
		String[] dani = {"Ponedeljak", "Utorak", "Sreda", "Četvrtak", "Petak", "Subota", "Nedelja"};
		for(int i = 0; i < nedelja.length; i++) {
			System.out.println(dani[i] + ":");
			
			if(getSlobodanDan(i)) {
				System.out.println("  >>> SLOBODAN DAN / PRAZNIK <<<");
			} else {
				for(int j = 0; j < nedelja[i].length; j++) {
					if(nedelja[i][j] != null) {
						System.out.println("  [" + j + ":00] " + nedelja[i][j].toString());
					} else {
						System.out.println("  [" + j + ":00] Slobodno");
					}
				}
			}
		}
	}
    
	public dogadjaj getTermin(int dan, int termin) {
		return nedelja[dan][termin];
	}
    
	public void setSlobodanDan(Boolean state, int dan) {
    	slobodanDan[dan] = state;
    }

	public Boolean getSlobodanDan(int dan) {
        return slobodanDan[dan] != null && slobodanDan[dan] == true;
    }
	
	public int[] getTerminVjezbe(predmet predmet){
		int[] rok = new int[2]; 
		for(int i = 0; i < 7; i++){
			if(slobodanDan[i] == true) continue; // preskoci slobodne dane
			for(int j = 0; j < 13; j++){
				dogadjaj d = nedelja[i][j];
				if(d == null) continue;
				if(d.getPredmet() == predmet && d.getIsVjezbe()){
					rok[0] = i;
					rok[1] = j;
					return rok;
				}
			}
		}
		return rok;
	}
	
	public int brojTestova() {
		return brTestovaOveNedelje;
	}

}

class semestar {
	List<raspored> sveNedelje = new ArrayList<raspored>();

	public void addNedelja(raspored r) {
		sveNedelje.add(r);
	}
	
	public raspored getNedelja(int count) {
		return sveNedelje.get(count);
	}
	
	public int[] getRokZaKolokvijum(predmet predmet, int odNedelje, List<semestar> semestri){
		// Prvi slobodni termin posle trece nedelje
		int[] nullRok = {0,0};
		for(int i = odNedelje; i < sveNedelje.size(); i++){
			// find vjezbe and popoulate it if it's allowed.
			// dodati provjere da li je
			int[] terminRoka = sveNedelje.get(i).getTerminVjezbe(predmet);
			for(int j = 0; j < 13; j++) {
				
			}
			if(predmet.getAsistent().slobodanTokomDana(i, terminRoka, semestri)) continue;
			if(terminRoka != nullRok){
				return terminRoka;
			}
			
		}
		return nullRok;
	}
}

class JSONSchedulingLoader {

    /**
     * Loads all data from university_scheduling.json and populates the system lists
     * @param profesoriList - List to populate with professor objects
     * @param ucioniceList - List to populate with classroom objects
     * @param predmetiList - List to populate with subject objects
     * @param dogadjajiList - List to populate with event objects
     * @return true if loading was successful, false otherwise
     */
	
    public static boolean loadSchedulingData(
            List<profesor> profesoriList,
            List<ucionica> ucioniceList,
            List<predmet> predmetiList,
            List<dogadjaj> dogadjajiList) {

        try {
            System.out.println("========== UČITAVANJE PODATAKA IZ JSON-a ==========\n");

            // Read the entire JSON file
            String jsonContent = readFile("data.json");

            // Parse professors
            loadProfessors(jsonContent, profesoriList);

            // Parse classrooms
            loadClassrooms(jsonContent, ucioniceList);

            // Parse subjects
            loadSubjects(jsonContent, predmetiList);

            // Parse events
            loadEvents(jsonContent, dogadjajiList, predmetiList, ucioniceList, profesoriList);

            System.out.println("\n========== UČITAVANJE ZAVRŠENO ==========\n");
            return true;

        } catch (IOException e) {
            System.out.println("Greška: Datoteka data.json nije pronađena!");
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.out.println("Greška: Neočekivana greška pri učitavanju podataka!");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Reads the entire file content into a string
     */
    private static String readFile(String filename) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * Extracts array content from JSON string
     * Finds "key": [ ... ] and returns the content between brackets
     */
    private static List<String> extractJsonArray(String json, String arrayName) {
        List<String> items = new ArrayList<>();

        String searchKey = "\"" + arrayName + "\":";
        int startIdx = json.indexOf(searchKey);

        if (startIdx == -1) {
            return items;
        }

        int bracketIdx = json.indexOf("[", startIdx);
        if (bracketIdx == -1) {
            return items;
        }

        int depth = 0;
        int objectStart = -1;

        for (int i = bracketIdx; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart != -1) {
                    items.add(json.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }

        return items;
    }

    /**
     * Extracts a string value from JSON object
     */
    private static String extractString(String jsonObj, String key) {
        String searchKey = "\"" + key + "\":";
        int idx = jsonObj.indexOf(searchKey);

        if (idx == -1) {
            return null;
        }

        int startQuote = jsonObj.indexOf("\"", idx + searchKey.length());
        if (startQuote == -1) {
            return null;
        }

        int endQuote = jsonObj.indexOf("\"", startQuote + 1);
        if (endQuote == -1) {
            return null;
        }

        return jsonObj.substring(startQuote + 1, endQuote);
    }

    /**
     * Extracts a number value from JSON object
     */
  

    /**
     * Extracts a boolean value from JSON object
     */
    private static boolean extractBoolean(String jsonObj, String key) {
        String searchKey = "\"" + key + "\":";
        int idx = jsonObj.indexOf(searchKey);

        if (idx == -1) {
            return false;
        }

        int startIdx = idx + searchKey.length();

        if (jsonObj.substring(startIdx, startIdx + 4).equals("true")) {
            return true;
        }

        return false;
    }

    /**
     * Loads professors from JSON and populates the professor list
     */
    private static void loadProfessors(String json, List<profesor> profesoriList) {
        List<String> profObjects = extractJsonArray(json, "professors");

        System.out.println("✓ Učitavanje profesora...");
        for (String profObj : profObjects) {
            String ime = extractString(profObj, "name");
            String mail = extractString(profObj, "email");

            if (ime != null && mail != null) {
                profesor p = new profesor(ime, mail, null);
                profesoriList.add(p);
                System.out.println("  + " + ime + " (" + mail + ")");
            }
        }
        System.out.println("  Ukupno učitano profesora: " + profesoriList.size() + "\n");
    }

    /**
     * Loads classrooms from JSON and populates the classroom list
     */
    private static void loadClassrooms(String json, List<ucionica> ucioniceList) {
        List<String> roomObjects = extractJsonArray(json, "classrooms");

        System.out.println("✓ Učitavanje učionica...");
        for (String roomObj : roomObjects) {
            String naziv = extractString(roomObj, "name");
            long capacity = extractNumber(roomObj, "capacity");
            boolean hasComputers = extractBoolean(roomObj, "has_computers");
            
            ucionica u = new ucionica(naziv, (int) capacity, hasComputers);
            ucioniceList.add(u);
            System.out.println("  + " + naziv + " (Kapacitet: " + capacity + ", Računari: " + (hasComputers ? "DA" : "NE") + ")");
        }
        System.out.println("  Ukupno učitano učionica: " + ucioniceList.size() + "\n");
    }

    /**
     * Loads subjects from JSON and populates the subject list
     */
    private static void loadSubjects(String json, List<predmet> predmetiList) {
        List<String> subjObjects = extractJsonArray(json, "subjects");

        System.out.println("✓ Učitavanje predmeta...");
        for (String subjObj : subjObjects) {
            String naziv = extractString(subjObj, "name");
            String kod = extractString(subjObj, "code");
            long godinaStudija = extractNumber(subjObj, "year");
            boolean isActive = extractBoolean(subjObj, "is_active");

            if (naziv != null && kod != null) {
                predmet p = new predmet(naziv, null, null, kod, (int) godinaStudija, isActive);
                predmetiList.add(p);
                System.out.println("  + " + naziv + " (" + kod + ") - Godina: " + godinaStudija);
            }
        }
        System.out.println("  Ukupno učitano predmeta: " + predmetiList.size() + "\n");
    }

    /**
     * Loads events from JSON and populates the event list
     */
    private static void loadEvents(String json, List<dogadjaj> dogadjajiList,
                                    List<predmet> predmetiList, List<ucionica> ucioniceList,
                                    List<profesor> profesoriList) {
        List<String> eventObjects = extractJsonArray(json, "events");

        System.out.println("✓ Učitavanje dogadjaja...");
        for (String eventObj : eventObjects) {
            String subjectCode = extractString(eventObj, "subject_code");
            String professorName = extractString(eventObj, "professor_name");
            String assistentName = extractString(eventObj, "assistent_name");
            String classroomName = extractString(eventObj, "classroom_name");
            long startHour = extractNumber(eventObj, "start_hour");
            long endHour = extractNumber(eventObj, "end_hour");
            long dayNumber = extractNumber(eventObj, "day_number");
            Boolean isLab = extractBoolean(eventObj, "is_Lab");
            Boolean isTest = extractBoolean(eventObj, "is_Test");

            if (subjectCode == null || professorName == null || classroomName == null) {
                continue;
            }

            // Get subject
            predmet predmet = findSubjectByCode(subjectCode, predmetiList);
            if (predmet == null) {
                System.out.println("  ✗ Predmet sa kodom " + subjectCode + " nije pronađen!");
                continue;
            }

            // Get professor
            profesor profesor = findProfessorByName(professorName, profesoriList);
            if (profesor == null) {
                System.out.println("  ✗ Profesor " + professorName + " nije pronađen!");
                continue;
            }
            
            profesor asistent = findAssistentByName(assistentName, profesoriList);


            // Get classroom
            ucionica ucionica = findClassroomByName(classroomName, ucioniceList);
            if (ucionica == null) {
                System.out.println("  ✗ Učionica " + classroomName + " nije pronađena!");
                continue;
            }

            // Create arrays for time
            int[] vremePocetka = new int[2];
            vremePocetka[0] = (int) dayNumber;
            vremePocetka[1] = (int) startHour;

            int[] vremeKraja = new int[2];
            vremeKraja[0] = (int) dayNumber;
            vremeKraja[1] = (int) endHour;

            // Create the event
            dogadjaj dogadjaj = new dogadjaj(predmet, ucionica, vremePocetka, vremeKraja, profesor, asistent, false, isLab, isTest);
            dogadjajiList.add(dogadjaj);

            String eventType = extractString(eventObj, "event_type");
            System.out.println("  + " + predmet.getNaziv() + " (" + eventType + ") - " + professorName);
        }
        System.out.println("  Ukupno učitano dogadjaja: " + dogadjajiList.size() + "\n");
    }

    /**
     * Helper method to find a subject by its code
     */
    private static predmet findSubjectByCode(String kod, List<predmet> predmetiList) {
        for (predmet p : predmetiList) {
            if (p.getKod().equals(kod)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Helper method to find a professor by name
     */
    private static profesor findProfessorByName(String ime, List<profesor> profesoriList) {
        for (profesor p : profesoriList) {
            if (p.getIme().equals(ime)) {
                return p;
            }
        }
        return null;
    }
    
    private static profesor findAssistentByName(String ime, List<profesor> profesoriList) {
        for (profesor p : profesoriList) {
            if (p.getIme().equals(ime)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Helper method to find a classroom by name
     */
    private static ucionica findClassroomByName(String naziv, List<ucionica> ucioniceList) {
        for (ucionica u : ucioniceList) {
            if (u.getNaziv().equals(naziv)) {
                return u;
            }
        }
        return null;
    }
    
    private static long extractNumber(String jsonObj, String key) {
        String searchKey = "\"" + key + "\":";
        int idx = jsonObj.indexOf(searchKey);

        if (idx == -1) {
            return -1;
        }

        int startIdx = idx + searchKey.length();
        
        // SKIP WHITESPACE
        while (startIdx < jsonObj.length() && Character.isWhitespace(jsonObj.charAt(startIdx))) {
            startIdx++;
        }
        
        int endIdx = startIdx;

        while (endIdx < jsonObj.length() && Character.isDigit(jsonObj.charAt(endIdx))) {
            endIdx++;
        }

        if (startIdx == endIdx) {
            return -1;
        }

        return Long.parseLong(jsonObj.substring(startIdx, endIdx));
    }

}

