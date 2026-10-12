import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UsuarioDao {

    // true si el usuario existe y la contraseña coincide con el hash guardado
    public static boolean validar(String usuario, String password) throws SQLException {
        String sql = "SELECT passwordHash FROM Usuarios WHERE usuario = ?";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                return rs.getString(1).equalsIgnoreCase(sha256(password));
            }
        }
    }

    // Mismo resultado que SHA2('texto', 256) en MySQL/TiDB
    private static String sha256(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            return String.format("%064x", new BigInteger(1, hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}