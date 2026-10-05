import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexionBD {
    public static Connection obtenerConexion() throws SQLException {
        String host = GestorCredenciales.get("DB_HOST");
        String port = GestorCredenciales.get("DB_PORT");
        String db = GestorCredenciales.get("DB_NAME");
        String user = GestorCredenciales.get("DB_USER");
        String pass = GestorCredenciales.get("DB_PASS");

        String url = String.format("jdbc:mysql://%s:%s/%s?sslMode=VERIFY_IDENTITY", host, port, db);
        return DriverManager.getConnection(url, user, pass);
    }
}