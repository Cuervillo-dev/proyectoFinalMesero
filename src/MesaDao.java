import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MesaDao {

    // Trae todas las mesas de la base de datos
    public static List<Mesa> listar() throws SQLException {
        List<Mesa> mesas = new ArrayList<>();
        String sql = "SELECT numeroMesa, estado FROM Mesas ORDER BY numeroMesa";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                mesas.add(new Mesa(rs.getInt("numeroMesa"), rs.getString("estado")));
            }
        }
        return mesas;
    }

    // LIBRE -> OCUPADA. Devuelve false si la mesa ya no estaba libre
    public static boolean ocupar(int numero) throws SQLException {
        String sql = "UPDATE Mesas SET estado = 'OCUPADA' WHERE numeroMesa = ? AND estado = 'LIBRE'";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, numero);
            return ps.executeUpdate() > 0;
        }
    }

    // OCUPADA -> LIBRE, y cierra los pedidos abiertos de esa mesa (todo o nada)
    public static void liberar(int numero) throws SQLException {
        try (Connection c = ConexionBD.obtenerConexion()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE Pedidos p JOIN Mesas m ON m.idMesa = p.idMesa " +
                                "SET p.estado = 'PAGADO' WHERE m.numeroMesa = ? AND p.estado <> 'PAGADO'")) {
                    ps.setInt(1, numero);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE Mesas SET estado = 'LIBRE' WHERE numeroMesa = ?")) {
                    ps.setInt(1, numero);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

}