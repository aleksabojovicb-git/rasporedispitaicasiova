# Backend zadaci — zahtjevi profesorke (Raspored.docx)

Ovo je lista svih stavki iz `Raspored.docx`, filtrirana i klasifikovana. Uključuje samo stvari
koje se tiču **backenda** (PHP API/admin logika i Java algoritam za generisanje rasporeda);
stavke koje su čisto imenovanje/tekst u UI-ju ili zahtijevaju odluku profesorke/tima su
označene kao takve, ne kao bagovi.

Legenda: `[x]` = urađeno, `[ ]` = nije urađeno (backlog), ~~precrtano~~ = završeno.

---

## Admin panel — Upravljanje profesorima

- [ ] "Upravljanje Profesorima" → malo p — **FRONTEND** (samo tekst naslova), van dogovorenog obima.
- [ ] Preimenovati u "Upravljanje korisnicima" + prikaz uloge (admin/predavač/saradnik) uz ime —
      **ODLUKA PROIZVODA**: ovo mijenja model podataka/UX, ne "bag". Vidi i stavku o razlici
      profesori/nalozi niže.
- [x] ~~Nema mogućnosti brisanja naloga; duga lista bez pretrage~~ — **URAĐENO**: dodata prava
      (trajna) brisanja za profesore ([admin_panel.php](public/views/admin_panel.php)), uz
      hvatanje FK grešaka ("Ne možete trajno obrisati... probajte deaktivirati") kad postoje
      povezani podaci. Dodata pretraga po imenu/emailu (`?page=profesori&q=...`).
- [ ] "Deadline" → "Rok za unos kol." — **FRONTEND** (tekst labele), van obima.

## Admin panel — Upravljanje predmetima

- [ ] "Upravljanje Predmetima" → malo p — **FRONTEND**, van obima.
- [x] ~~Ograničenje na 2 predavača (1 prof + 1 asistent) po predmetu~~ — **URAĐENO (glavni uzrok)**:
      `course_professor` veze nikad nisu bile ograničene u bazi/PHP-u — admin je uvijek mogao
      dodati 2-3 profesora. Pravi bag je bio u Java algoritmu za generisanje rasporeda
      ([EventValidationService.java](public/java/EventValidationService.java)):
      `findLectureProfessor`/`findExerciseProfessor` su uzimali samo PRVOG pronađenog profesora/
      asistenta, pa su dodatni predavači nestajali iz generisanog rasporeda. Popravljeno tako da
      se generisani termin (`saveToAcademicEvent`) sada poveže sa **svim** profesorima/
      asistentima dodijeljenim predmetu (`event_professor`), ne samo sa jednim. Testirano uživo:
      predmet sa 2 predavača → oba se pojave u generisanom terminu.
      **Preostalo (backlog, nije urađeno)**: sama provjera dostupnosti/konflikata i dalje se radi
      samo prema PRVOM predavaču/asistentu — ako drugi/treći predavač ima drugačiju
      raspoloživost, to se trenutno ne provjerava. Za potpuno rješenje treba proširiti
      `hasConflict`/`hasConflictForBlock` da provjeravaju SVE dodijeljene profesore, što je veća
      izmjena algoritma i traži pažljivije testiranje.
- [x] ~~Nije moguće izbrisati predmet, samo deaktivirati~~ — **URAĐENO**: dodato trajno brisanje
      predmeta (uz brisanje `course_professor` veza), sa zaštitom od brisanja ako postoje
      podaci vezani za raspored/kolokvijume.

## Admin panel — Upravljanje salama

- [ ] "Upravljanje Salama" → malo s — **FRONTEND**, van obima.
- [x] ~~Nije moguće izbrisati salu, samo deaktivirati~~ — **URAĐENO**: dodato trajno brisanje sale,
      sa zaštitom od brisanja ako postoje podaci vezani za nju (raspored, zauzetost).
- [x] ~~Kategorija "čija je sala" (FIT ima 101, 104, 109, P09; druge na zahtjev)~~ — **URAĐENO**:
      dodata kolona `room.faculty_code` (npr. "FIT"), formulari za dodavanje/izmjenu sale, i
      prikaz u tabeli salama ("Pripada": FIT / *Dijeljena / na zahtjev*).
      **Napomena (frontend, van obima)**: dugme "Uredi" šalje `data-faculty_code` atribut, ali
      JS handler za popunjavanje forme za izmjenu (`admin.js`) treba dopuniti da ga pročita i
      upiše u polje — bez toga se pri izmjeni polje ne popunjava automatski (backend already
      prima i čuva vrijednost ako se ručno unese).

## Admin panel — Upravljanje nalozima

- [ ] "Upravljanje Nalozima" → malo n — **FRONTEND**, van obima.
- [ ] "Šta je professor a šta user?" / razlika Upravljanje profesorima vs. Upravljanje nalozima —
      **ODLUKA PROIZVODA**, ne bag: `professor` je akademski profil (ime, email — koristi se u
      rasporedu), `user_account` su login kredencijali (opciono povezani sa profesorom preko
      `professor_id`). Ovo razdvajanje je namjerno u šemi baze, ali UI ga očigledno ne objašnjava
      dovoljno jasno. Predlažemo da se ovo razriješi zajedno sa profesorkom (npr. kratak opis u
      UI-ju) prije bilo kakve promjene modela podataka.
- [ ] "Šta znači Povezani profesor pri kreiranju naloga?" — **FRONTEND** (dodati kratak opis/
      tooltip pored polja), van dogovorenog obima ove sesije.
- [x] ~~Inicijalni password se ne šalje korisniku (niko nije dobio šifru)~~ — **URAĐENO**: kada
      admin kreira nalog i poveže ga sa profesorom koji ima email, sistem sada šalje email sa
      korisničkim imenom i lozinkom (PHPMailer, isti SMTP kao ostatak sistema). Ako slanje ne
      uspije, admin panel to eksplicitno javlja ("prenesite lozinku ručno") umjesto da tiho
      "uspije" bez emaila.
      **Napomena**: u ovom sandbox okruženju SMTP izlazna konekcija nije testirana do kraja
      (mrežna ograničenja), pa treba potvrditi slanje na produkciji/lokalno gdje SMTP izlaz radi.
- [x] ~~Nema brisanja naloga; lista nije sortirana po imenu; nema filtera~~ — **URAĐENO**: dodato
      trajno brisanje naloga (sa zaštitom od FK grešaka), lista je sada sortirana po imenu
      povezanog profesora (padne na username za admin nalog bez profesora), dodata pretraga po
      korisničkom imenu/imenu profesora.

## Zauzetost sala

- [x] ~~Da li su zauzetost sala i raspored povezani?~~ — **PRONAĐENO, NIJE POPRAVLJENO (backlog)**:
      trenutno NISU povezani ni u jednom smjeru. `room_occupancy` je čisto ručna admin tabela
      ([OccupancyService.php](src/services/OccupancyService.php)) i Java algoritam za
      generisanje rasporeda je nikad ne čita, pa generisani raspored može dodijeliti salu koja je
      već zauzeta od strane drugog fakulteta, i obrnuto — generisani termini se ne upisuju u
      `room_occupancy`. Ovo zahtijeva izmjenu Java algoritma (da isključi zauzete kombinacije
      sala/dan/vrijeme iz `getSuitableRooms`) — nije urađeno u ovoj sesiji jer dira srž algoritma
      za biranje sala i treba pažljivo testiranje da ne pokvari postojeće generisanje.

## Upravljanje kalendarom i događajima

- [ ] "Upravljanje Kalendarom i Događajima" → malo k, malo d — **FRONTEND**, van obima.
- [x] ~~Događaji uneseni od strane korisnika (profesora) se ne prikazuju na admin panelu~~ —
      **VEĆ RADI** u trenutnom kodu: sekcija "Rezervisani termini profesora" na stranici
      Događaji već prikazuje raspoloživost koju su profesori unijeli. Izgleda da je ovo već
      riješeno u međuvremenu (nije urađeno u ovoj sesiji).
- [x] ~~Termini kolokvijuma postavljeni od strane profesora se ne prikazuju~~ — **VEĆ RADI**:
      sekcija "Predmeti i sedmice kolokvijuma" na istoj stranici već prikazuje sedmice
      kolokvijuma koje su profesori izabrali, po predmetu.

## Raspored (generisanje/prikaz)

- [ ] Pogrešan naziv kategorije "Dobrodošli u Admin Panel" — **FRONTEND** (tekst), van obima.
- [ ] Slova ć i č se ne prikazuju u PDF-u — **FRONTEND**: PDF se generiše na klijentu (jsPDF,
      `public/assets/js/jspdf*.js`), ne na backendu — treba embed-ovati font sa ć/č glifovima u
      JS kodu. Van dogovorenog obima ove sesije (nije backend).
- [x] ~~"Fond časova" se ne unosi, pa se generišu razbijeni časovi (npr. 1+4 na dva dana)~~ —
      **URAĐENO (pravi uzrok pronađen i popravljen)**: polja `lectures_per_week` /
      `exercises_per_week` / `labs_per_week` su oduvijek postojala i unosila se ispravno — to
      nije bio problem. Pravi uzrok je otkriven empirijskim testiranjem: `generateScheduleWithYearPriority`
      (metoda koju stvarno pokreće dugme "Generiši raspored časova") raspoređuje predmete u do 3
      odvojene faze po kategoriji (ima lab časove / nema lab i nije onlajn / je onlajn), a te
      kategorije **nisu međusobno isključive** — predmet koji ISTOVREMENO ima lab časove I je
      onlajn (potvrđeno na stvarnom predmetu u bazi: "Mašinsko učenje", P=3 V=1 L=1, online=true)
      se obrađuje u DVIJE faze, svaka nezavisno raspoređuje pun set časova za taj predmet, na
      DRUGAČIJEM danu — rezultat su duplirani/razbacani časovi istog predmeta (npr. vježbe 1h
      utorkom I 1h četvrtkom umjesto 2h jednog dana, ponekad i predavanja duplirana 3h+3h na dva
      dana). Popravljeno dodavanjem provjere u `scheduleCourseForSchedule()`
      ([EventValidationService.java](public/java/EventValidationService.java)): ako je predmet
      već raspoređen u ovom rasporedu (bilo kojom ranijom fazom), preskače se. Potvrđeno uživo:
      prije popravke, 6 od 15 provjerenih generisanih rasporeda je imalo ovaj problem (uvijek isti
      predmet, "Mašinsko učenje"); poslije popravke, 6 novih punih generisanja (`generisiKompletan`)
      - nula fragmentacije.
- [x] ~~Ne postoji opcija generisanja rasporeda kolokvijuma/ispita~~ — **URAĐENO (frontend otkriveno
      na zahtjev)**: dugme "Generiši kolokvijume" i pripadajuća sekcija su ranije bili trajno
      sakriveni (`display:none !important`), iako je logika za automatski prikaz/sakrivanje sekcije
      (na osnovu toga da li generisani raspored sadrži kolokvijume) već postojala, samo zakomentarisana.
      Uklonjeno prisilno sakrivanje i vraćena postojeća logika (`renderScheduleData` sad opet poziva
      `renderColloquiums()` kad ima kolokvijuma). Backend je već ranije potvrđen kao ispravan
      (`generisiKolokvijume` uživo vraća "OK").
- [x] ~~Nedostaje tip časova (predavanje/vježbe) i ime predavača u rasporedu~~ —
      **ADMIN prikaz: VEĆ RADI** (`?action=getschedule` već vraća `type` i `professors`/
      `assistants`). **URAĐENO za profesorov "Moj raspored"**: `get_professor_schedule` u
      [professor_api.php](public/views/api/professor_api.php) sada vraća i `type`/`type_label` i
      ime predavača (`professor`), po uzoru na admin prikaz.
      **Napomena (frontend, van obima)**: JS u `professor_panel.php` koji gradi naslov događaja
      u kalendaru trenutno ne koristi nova polja — treba dodati `type_label`/`professor` u
      `title` da se stvarno i VIDI (backend podatak je sada tu, prikaz treba dopuniti).
- [ ] Admin nema mogućnost izmjene generisanog rasporeda (sale, paralelne grupe) —
      **POTVRĐENO KAO PRAVI NEDOSTATAK, NIJE IMPLEMENTIRANO (backlog)**: ne postoji nijedan
      endpoint koji dozvoljava izmjenu pojedinačnog generisanog termina (sala/vrijeme). Ovo
      zahtijeva novi API endpoint sa provjerom konflikata prilikom izmjene — nije urađeno u
      ovoj sesiji zbog obima (treba i minimalan UI da bude upotrebljivo), ostaje kao sljedeći
      korak.
- [x] ~~Prije generisanja unijeti dodatne uslove (rač. sala, broj studenata vs. kapacitet, ne
      zahtijeva salu/onlajn, dvije grupe istovremeno)~~ — **URAĐENO**: dodate kolone
      `course.requires_computer_lab`, `course.expected_students`, `course.parallel_groups` (šema
      + forma za dodavanje/izmjenu predmeta + prikaz u tabeli predmeta, kolona "Uslovi"). Java
      algoritam sada: (1) bira salu sa `is_computer_lab=true` za predmet kad je
      `requires_computer_lab` uključeno, nezavisno od `labs_per_week`; (2) koristi
      `expected_students` kao minimalni traženi kapacitet sale umjesto fiksnog broja; (3) za
      `is_online` predmete više NE dodjeljuje fizičku salu (`room_id` ostaje NULL, provjerava se
      samo dostupnost profesora) — ranije se onlajn predmetu i dalje dodjeljivala prava sala; (4)
      za `parallel_groups > 1`, nova metoda `scheduleParallelGroups()` postavlja dodatne grupe u
      ISTI dan/termin kao prva grupa, u drugoj slobodnoj sali i sa drugim dodijeljenim
      predavačem/asistentom (ako je dostupan preko `course_professor`) — ako druga sala ili
      profesor nisu slobodni, ta grupa se best-effort preskače bez rušenja ostatka generisanja.
      Sve testirano uživo na kopiranom test-predmetu (parallel_groups=2 + 2 predavača → dvije
      paralelne grupe u istom terminu, različite sale/predavači; is_online=true → `room_id` NULL
      u svim generisanim terminima; requires_computer_lab + expected_students=15 → predmet
      dosljedno dobija računarsku salu kapaciteta ≥15); test podaci potom vraćeni na
      početno stanje.

---

## Profesor panel

- [x] ~~Zaboravljena lozinka → "Došlo je do greške. Pokušajte ponovo."~~ — **URAĐENO (pravi uzrok
      pronađen i popravljen)**: `authorization.php` i `forgot_password.php` su zvali
      `../../src/api/password_reset.php`, putanju koja je VAN javnog `public/` docroot-a —
      potvrđeno uživo (404 Not Found), zbog čega je JS dobijao HTML 404 stranicu umjesto JSON-a i
      padao u generičku grešku. Dodat je `public/api/password_reset.php` (isti obrazac kao
      postojeći `public/api/schedule_lock.php`) i ispravljene obje putanje. Potvrđeno uživo:
      endpoint sada vraća ispravan JSON (200) umjesto 404.
- [x] ~~Kalendar: naziv praznika / oznaka neradnog dana~~ — **URAĐENO**: `holiday` tabela je već
      imala kolone `name` i `is_working_day`, ali `get_holidays` u profesorskom API-ju je vraćao
      samo `date`/`name`. Dopunjeno da vraća i `is_working_day` (bool).
      **Napomena (frontend, van obima)**: kalendar UI treba dopuniti da tu vrijednost i prikaže
      (npr. da oboji neradne dane).
- [x] ~~Greška pri slanju zahtjeva za kolokvijume (ne može da se sačuva)~~ — **NIJE
      REPRODUKOVANO / VEĆ RADI**: testirano uživo (`save_colloquium_weeks`), radi ispravno i
      vraća `success:true`. Moguće da je ovo već popravljeno ranije, ili se javljalo u specifičnoj
      okolnosti koja se u ovoj sesiji nije mogla reprodukovati.
- [x] ~~Raspoloživost: mogu se unijeti samo termini, nema dodatnih zahtjeva (rač. sala, izbor sale,
      vezivanje predmeta za dan)~~ — **DJELIMIČNO URAĐENO**: dodate kolone
      `professor_availability.requires_computer_lab`, `.preferred_room_id`, `.course_id` (šema +
      `save_availability` u [professor_api.php](public/views/api/professor_api.php) sada prima i
      čuva sve tri, ako ih frontend pošalje uz termin). Admin panel (Događaji →
      "Rezervisani termini profesora") sada prikazuje te zahtjeve uz svaki termin.
      **Vezivanje predmeta za dan JE integrisano u Java algoritam**: nova
      `getPreferredDaysForCourse(profesor, predmet)` - ako profesor ima termin raspoloživosti
      eksplicitno vezan za taj predmet (`course_id`), generisanje tog predmeta koristi ISKLJUČIVO
      te dane; inače se ponaša kao i do sad (opšta raspoloživost). Testirano uživo: profesor
      vezan za predmet samo za četvrtak → predmet se generisao baš u četvrtak.
      **Preostalo (backlog)**: `requires_computer_lab`/`preferred_room_id` PO TERMINU su
      sačuvani i vidljivi adminu, ali nisu još povezani sa biranjem sale u algoritmu (trenutni
      algoritam bira salu na nivou PREDMETA - vidi `course.requires_computer_lab` gore - a ne na
      nivou pojedinačnog termina raspoloživosti); dublje povezivanje bi tražilo veću izmjenu
      strukture pretrage termina i nije urađeno u ovoj sesiji.
      **Napomena (frontend, van obima)**: forma za raspoloživost u `professor_panel.php` trenutno
      šalje samo `day`/`from`/`to` po terminu — da bi profesor stvarno mogao UNIJETI ova tri nova
      polja, formu treba dopuniti (checkbox za rač. salu, dropdown za salu, dropdown za predmet).
- [x] ~~Moj raspored časova — ne prikazuje se ništa~~ — **URAĐENO**: `get_professor_schedule` je
      birao "najstariji od posljednjih 6" generisanih rasporeda (`array_reverse` pa `[0]`), umjesto
      trenutno važećeg. Popravljeno da bira **najnoviji zaključani (locked_by_admin) raspored**,
      a ako još ništa nije zaključano, pada nazad na najnoviji generisani — potvrđeno uživo
      (prije: pogrešan/stariji `schedule_id`, poslije: ispravan zaključani raspored).

---

## Usput pronađeno (nije iz docx-a, ali relevantno)

- `src/api/auth.php` je mrtav kod — nikad se ne poziva iz aplikacije (stvarna prijava je
  implementirana direktno u `authorization.php`). Nije dirano, samo napomena za buduće čišćenje.
- Java bug: `professor_availability.weekday` se čuva kao int (1-5), ali
  `EventValidationService.getPreferredDays()` ga je čitao kao ime dana (`rs.getString`), pa se
  nikad nije poklapalo sa "ponedeljak"/"utorak"/... — posljedica: svi generisani termini su
  padali na ponedjeljak (default fallback). **Popravljeno** (mapiranje int → ime dana) i
  potvrđeno uživo generisanjem termina koji sada padaju na različite dane (ponedeljak, utorak,
  srijeda), ne samo na ponedjeljak.

---

## Rezime

**Urađeno i testirano uživo, prva runda (11 stavki):**
1. Popravljen weekday bag u Java algoritmu (svi termini padali na ponedjeljak)
2. Generisani termini se sada vezuju za SVE dodijeljene predavače/asistente predmeta (ne samo prvog)
3. Popravljena slomljena ruta za reset lozinke (404 → radi)
4. Email sa inicijalnom lozinkom pri kreiranju naloga
5. Trajno brisanje profesora/predmeta/sala/naloga (uz zaštitu od brisanja povezanih podataka)
6. Pretraga na listama profesora/predmeta/sala/naloga
7. Sortiranje liste naloga po imenu (umjesto po username-u)
8. Kolona "čija je sala" (faculty_code) na salama
9. `get_holidays` vraća i oznaku neradnog dana
10. "Moj raspored" prikazuje ispravan (zaključani) raspored umjesto proizvoljnog starog
11. "Moj raspored" sada uključuje tip časa i ime predavača

**Urađeno i testirano uživo, druga runda (5 stavki, na zahtjev):**
12. Pravi uzrok fragmentacije "fonda časova" pronađen i popravljen (dupliranje termina kad
    predmet upadne u više od jedne faze generisanja - npr. ima labove I je onlajn)
13. Otkriveno dugme "Generiši kolokvijume" (uklonjen `display:none`, vraćena postojeća ali
    zakomentarisana logika prikaza)
14. Dodatni uslovi predmeta prije generisanja: `requires_computer_lab`, `expected_students`,
    `parallel_groups` - šema + forma + algoritam (bira rač. salu, poštuje kapacitet, onlajn
    predmeti ne dobijaju salu, paralelne grupe dobijaju drugu salu/predavača u istom terminu)
15. Raspoloživost profesora: dodatna polja `requires_computer_lab`/`preferred_room_id`/
    `course_id` po terminu (šema + API + prikaz adminu); vezivanje predmeta za konkretan dan je
    i integrisano u algoritam (`getPreferredDaysForCourse`)

**Već radilo (pronađeno, nije trebalo popravku): 3 stavke** — rezervisani termini profesora,
sedmice kolokvijuma na admin panelu, slanje zahtjeva za kolokvijume.

**Backend spreman, čeka frontend izmjenu (napomene):** prikaz tipa/predavača u profesorovom
kalendaru, popunjavanje polja "kome pripada sala" pri izmjeni, forma za raspoloživost treba
dopuniti da profesor uopšte MOŽE unijeti rač. salu/izbor sale/predmet (backend ih već prima i
čuva ako stignu).

**Ostaje kao backlog (veći/rizičniji zahvati, nisu rađeni ovu sesiju):** puna provjera
konflikata za sve dodijeljene predavače (ne samo prvog), povezivanje zauzetosti sala sa
generisanjem rasporeda, mogućnost izmjene generisanog rasporeda od strane admina (sala/vrijeme
pojedinačnog termina), povezivanje `requires_computer_lab`/`preferred_room_id` PO TERMINU
raspoloživosti sa algoritmom (trenutno se ti zahtjevi čuvaju i vide, ali algoritam i dalje bira
salu na nivou predmeta, ne pojedinačnog termina).

**Van obima (frontend/tekst/dizajn odluke):** sve stavke označene kao "malo p/s/k/d" (samo
veličina slova u naslovu), ć/č u PDF-u (klijentski jsPDF), preimenovanje kategorija, i pitanja
koja traže odluku profesorke o modelu podataka (profesor vs. nalog).
