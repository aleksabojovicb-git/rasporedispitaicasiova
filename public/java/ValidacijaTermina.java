import java.sql.*;

public class ValidacijaTermina {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("GRESKA: Nedostaje akcija");
            return;
        }
        
        String akcija = args[0];
        Connection conn = null;
        
        try {
            conn = BazaInicijalizacija.uspostaviKonekciju();
            EventValidationService service = new EventValidationService(conn);
            
            String rezultat = "";
            
            if (akcija.equals("dodajPredavanje")) {
                if (args.length < 7) {
                    System.out.println("GRESKA: Nedostaju parametri");
                    return;
                }
                int idPredmet = Integer.parseInt(args[1]);
                int idSala = Integer.parseInt(args[2]);
                int idProfesor = Integer.parseInt(args[3]);
                String dan = args[4];
                String vremeOd = args[5];
                String vremeDo = args[6];
                rezultat = service.dodajPredavanje(idPredmet, idSala, idProfesor, dan, vremeOd, vremeDo);
                
            } else if (akcija.equals("dodajVjezbe")) {
                if (args.length < 7) {
                    System.out.println("GRESKA: Nedostaju parametri");
                    return;
                }
                int idPredmet = Integer.parseInt(args[1]);
                int idSala = Integer.parseInt(args[2]);
                int idProfesor = Integer.parseInt(args[3]);
                String dan = args[4];
                String vremeOd = args[5];
                String vremeDo = args[6];
                rezultat = service.dodajVjezbe(idPredmet, idSala, idProfesor, dan, vremeOd, vremeDo);
                
            } else if (akcija.equals("dodajKolokvijum")) {
                if (args.length < 8) {
                    System.out.println("GRESKA: Nedostaju parametri");
                    return;
                }
                int idPredmet = Integer.parseInt(args[1]);
                int idSala = Integer.parseInt(args[2]);
                int idProfesor = Integer.parseInt(args[3]);
                int idDezurni = Integer.parseInt(args[4]);
                String datum = args[5];
                String vremeOd = args[6];
                String vremeDo = args[7];
                rezultat = service.dodajKolokvijum(idPredmet, idSala, idProfesor, idDezurni, datum, vremeOd, vremeDo);
                
            } else if (akcija.equals("dodajIspit")) {
                if (args.length < 8) {
                    System.out.println("GRESKA: Nedostaju parametri");
                    return;
                }
                int idPredmet = Integer.parseInt(args[1]);
                int idSala = Integer.parseInt(args[2]);
                int idProfesor = Integer.parseInt(args[3]);
                String datum = args[4];
                String vremeOd = args[5];
                String vremeDo = args[6];
                String tipIspita = args[7];
                rezultat = service.dodajIspit(idPredmet, idSala, idProfesor, datum, vremeOd, vremeDo, tipIspita);
                
            } else if (akcija.equals("generisiPredavanja")) {
                if (args.length < 2) {
                    System.out.println("GRESKA: Nedostaje ID predmeta");
                    return;
                }
                int idPredmet = Integer.parseInt(args[1]);
                rezultat = service.generisiRasporedPredavanja(idPredmet);
                
            } else if (akcija.equals("generisiVjezbe")) {
                if (args.length < 2) {
                    System.out.println("GRESKA: Nedostaje ID predmeta");
                    return;
                }
                int idPredmet = Integer.parseInt(args[1]);
                rezultat = service.generisiRasporedVjezbi(idPredmet);
                
            } else {
                System.out.println("GRESKA: Nepoznata akcija");
                return;
            }
            
            System.out.println(rezultat);
            
        } catch (Exception e) {
            System.out.println("GRESKA: " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Greska pri zatvaranju konekcije");
                }
            }
        }
    }
}
