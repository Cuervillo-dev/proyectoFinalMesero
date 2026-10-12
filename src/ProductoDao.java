import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductoDao {

    // Solo productos activos (los "eliminados" quedan con activo = 0)
    public static List<Producto> listarActivos() throws SQLException {
        List<Producto> lista = new ArrayList<>();
        String sql = "SELECT idProducto, nombre, descripcion, categoria, precio " +
                "FROM Productos WHERE activo = 1 ORDER BY categoria, idProducto";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String desc = rs.getString("descripcion");
                lista.add(new Producto(
                        rs.getInt("idProducto"), rs.getString("nombre"),
                        desc == null ? "" : desc,
                        rs.getString("categoria"), rs.getDouble("precio")));
            }
        }
        return lista;
    }

    public static void agregar(Producto p) throws SQLException {
        String sql = "INSERT INTO Productos (nombre, descripcion, categoria, precio) VALUES (?, ?, ?, ?)";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.nombre());
            ps.setString(2, p.descripcion());
            ps.setString(3, p.categoria());
            ps.setDouble(4, p.precio());
            ps.executeUpdate();
        }
    }

    public static void actualizar(Producto p) throws SQLException {
        String sql = "UPDATE Productos SET nombre = ?, descripcion = ?, categoria = ?, precio = ? " +
                "WHERE idProducto = ?";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.nombre());
            ps.setString(2, p.descripcion());
            ps.setString(3, p.categoria());
            ps.setDouble(4, p.precio());
            ps.setInt(5, p.id());
            ps.executeUpdate();
        }
    }

    // "Eliminar" = ocultar. No se borra la fila para no romper pedidos y ventas anteriores
    public static void desactivar(int idProducto) throws SQLException {
        String sql = "UPDATE Productos SET activo = 0 WHERE idProducto = ?";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            ps.executeUpdate();
        }
    }
}