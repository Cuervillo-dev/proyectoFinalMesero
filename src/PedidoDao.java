import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PedidoDao {

    // El idDetalle más alto que existe ahora mismo en la BD
    public static int maxIdDetalle() throws SQLException {
        String sql = "SELECT COALESCE(MAX(idDetalle), 0) FROM DetallePedidos";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // Pedidos que aún no están pagados, con su total
    public static List<PedidoResumen> listarActivos() throws SQLException {
        List<PedidoResumen> lista = new ArrayList<>();
        String sql =
                "SELECT p.idPedido, m.numeroMesa, p.estado, " +
                        "       COALESCE(SUM(d.cantidad * d.precioUnitario), 0) AS total " +
                        "FROM Pedidos p " +
                        "JOIN Mesas m ON m.idMesa = p.idMesa " +
                        "LEFT JOIN DetallePedidos d ON d.idPedido = p.idPedido " +
                        "WHERE p.estado <> 'PAGADO' " +
                        "GROUP BY p.idPedido, m.numeroMesa, p.estado " +
                        "ORDER BY m.numeroMesa";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new PedidoResumen(
                        rs.getInt("idPedido"), rs.getInt("numeroMesa"),
                        rs.getString("estado"), rs.getDouble("total")));
            }
        }
        return lista;
    }

    // Productos de un pedido
    public static List<DetalleItem> detalle(int idPedido) throws SQLException {
        List<DetalleItem> lista = new ArrayList<>();
        String sql =
                "SELECT d.idDetalle, pr.nombre, d.cantidad, d.precioUnitario, d.estado " +
                        "FROM DetallePedidos d " +
                        "JOIN Productos pr ON pr.idProducto = d.idProducto " +
                        "WHERE d.idPedido = ? " +
                        "ORDER BY d.idDetalle";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new DetalleItem(
                            rs.getInt("idDetalle"), rs.getString("nombre"),
                            rs.getInt("cantidad"), rs.getDouble("precioUnitario"),
                            rs.getString("estado")));
                }
            }
        }
        return lista;
    }

    // PENDIENTE -> ENTREGADO
    public static void marcarEntregado(int idDetalle) throws SQLException {
        String sql = "UPDATE DetallePedidos SET estado = 'ENTREGADO' WHERE idDetalle = ?";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idDetalle);
            ps.executeUpdate();
        }
    }
    // Detalle nuevo que aún no se ha notificado (idDetalle > ultimoVisto)
    public static List<DetalleItem> nuevosDesde(int ultimoVisto) throws SQLException {
        List<DetalleItem> lista = new ArrayList<>();
        String sql =
                "SELECT d.idDetalle, m.numeroMesa, pr.nombre, d.cantidad, d.precioUnitario, d.estado " +
                        "FROM DetallePedidos d " +
                        "JOIN Pedidos p ON p.idPedido = d.idPedido " +
                        "JOIN Mesas m ON m.idMesa = p.idMesa " +
                        "JOIN Productos pr ON pr.idProducto = d.idProducto " +
                        "WHERE d.idDetalle > ? ORDER BY d.idDetalle";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ultimoVisto);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new DetalleItem(
                            rs.getInt("idDetalle"), rs.getInt("numeroMesa") + " | " + rs.getString("nombre"),
                            rs.getInt("cantidad"), rs.getDouble("precioUnitario"), rs.getString("estado")));
                }
            }
        }
        return lista;
    }

    // Mesas cuya cuenta fue solicitada y aún no se ha avisado
    public static List<Integer> mesasConCuentaSolicitada() throws SQLException {
        List<Integer> mesas = new ArrayList<>();
        String sql = "SELECT m.numeroMesa FROM Pedidos p JOIN Mesas m ON m.idMesa = p.idMesa " +
                "WHERE p.estado = 'CUENTA_SOLICITADA'";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) mesas.add(rs.getInt(1));
        }
        return mesas;
    }

}