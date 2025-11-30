import java.sql.*;

public class BazaInicijalizacija {
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/raspored_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";
    
    public static Connection uspostaviKonekciju() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("Konekcija uspjesna!");
            return conn;
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL driver nije pronadjen: " + e.getMessage());
            throw new SQLException("Driver error", e);
        } catch (SQLException e) {
            System.err.println("Greska pri povezivanju na bazu: " + e.getMessage());
            throw e;
        }
    }
    
    public static void main(String[] args) {
        Connection conn = null;
        
        try {
            conn = uspostaviKonekciju();
            
            System.out.println("\n=== Testiranje sistema ===\n");
            
            EventValidationService service = new EventValidationService(conn);
            
            System.out.println("\n=== Podaci ucitani u memoriju ===\n");
            
            System.out.println("Sistem je spreman za rad!");
            
        } catch (SQLException e) {
            System.err.println("Greska: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                    System.out.println("\nKonekcija zatvorena.");
                } catch (SQLException e) {
                    System.err.println("Greska pri zatvaranju konekcije: " + e.getMessage());
                }
            }
        }
    }
}
