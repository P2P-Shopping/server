import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class ReproduceH2Error {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
        Connection conn = DriverManager.getConnection(url, "sa", "");
        Statement stmt = conn.createStatement();
        try {
            stmt.execute("set client_min_messages = WARNING");
            System.out.println("Success!");
        } catch (Exception e) {
            System.out.println("Failed: " + e.getMessage());
        }
        conn.close();
    }
}
