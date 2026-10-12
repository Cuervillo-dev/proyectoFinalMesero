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

    // Pedido -> PAGADO y mesa -> LIBRE (transacción)
    public static void cerrarCuenta(int numeroMesa) throws SQLException {
        String sqlPedido =
                "UPDATE Pedidos SET estado = 'PAGADO', fechaCierre = ? " +
                        "WHERE idMesa = (SELECT idMesa FROM Mesas WHERE numeroMesa = ?) " +
                        "AND estado <> 'PAGADO'";
        String sqlMesa = "UPDATE Mesas SET estado = 'LIBRE' WHERE numeroMesa = ?";

        try (Connection c = ConexionBD.obtenerConexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement p1 = c.prepareStatement(sqlPedido);
                 PreparedStatement p2 = c.prepareStatement(sqlMesa)) {

                p1.setTimestamp(1, Timestamp.valueOf(java.time.LocalDateTime.now()));
                p1.setInt(2, numeroMesa);
                p1.executeUpdate();

                p2.setInt(1, numeroMesa);
                p2.executeUpdate();

                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    // Pedido abierto de una mesa (null si no hay)
    public static Integer idPedidoActivoDeMesa(int numeroMesa) throws SQLException {
        String sql = "SELECT p.idPedido FROM Pedidos p " +
                "JOIN Mesas m ON m.idMesa = p.idMesa " +
                "WHERE m.numeroMesa = ? AND p.estado <> 'PAGADO' " +
                "ORDER BY p.idPedido DESC LIMIT 1";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, numeroMesa);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    // Líneas del recibo
    public static List<LineaRecibo> lineasRecibo(int idPedido) throws SQLException {
        List<LineaRecibo> lista = new ArrayList<>();
        String sql = "SELECT pr.nombre, d.cantidad, d.precioUnitario " +
                "FROM DetallePedidos d " +
                "JOIN Productos pr ON pr.idProducto = d.idProducto " +
                "WHERE d.idPedido = ? ORDER BY d.idDetalle";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new LineaRecibo(rs.getString(1), rs.getInt(2), rs.getDouble(3)));
                }
            }
        }
        return lista;
    }

    // Total actual del pedido
    public static double totalPedido(int idPedido) throws SQLException {
        String sql = "SELECT COALESCE(SUM(cantidad * precioUnitario), 0) FROM DetallePedidos WHERE idPedido = ?";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getDouble(1);
            }
        }
    }

    // Total con el que se imprimió el último recibo (null si nunca se imprimió)
    public static Double reciboTotal(int idPedido) throws SQLException {
        String sql = "SELECT reciboTotal FROM Pedidos WHERE idPedido = ?";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPedido);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                java.math.BigDecimal v = rs.getBigDecimal(1);
                return v == null ? null : v.doubleValue();
            }
        }
    }

    public static void marcarReciboImpreso(int idPedido, double total) throws SQLException {
        String sql = "UPDATE Pedidos SET reciboTotal = ? WHERE idPedido = ?";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, total);
            ps.setInt(2, idPedido);
            ps.executeUpdate();
        }
    }

    // {cantidad de cuentas cerradas, total vendido} entre dos fechas
    public static double[] resumenVentas(Timestamp desde, Timestamp hasta) throws SQLException {
        String sql =
                "SELECT COUNT(DISTINCT p.idPedido), COALESCE(SUM(d.cantidad * d.precioUnitario), 0) " +
                        "FROM Pedidos p " +
                        "LEFT JOIN DetallePedidos d ON d.idPedido = p.idPedido " +
                        "WHERE p.estado = 'PAGADO' AND p.fechaCierre >= ? AND p.fechaCierre <= ?";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, desde);
            ps.setTimestamp(2, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new double[]{rs.getInt(1), rs.getDouble(2)};
            }
        }
    }

    // Productos vendidos en el rango, del más vendido al menos vendido
    public static List<String> productosVendidos(Timestamp desde, Timestamp hasta) throws SQLException {
        List<String> lista = new ArrayList<>();
        String sql =
                "SELECT pr.nombre, SUM(d.cantidad) AS cant " +
                        "FROM Pedidos p " +
                        "JOIN DetallePedidos d ON d.idPedido = p.idPedido " +
                        "JOIN Productos pr ON pr.idProducto = d.idProducto " +
                        "WHERE p.estado = 'PAGADO' AND p.fechaCierre >= ? AND p.fechaCierre <= ? " +
                        "GROUP BY pr.nombre ORDER BY cant DESC";
        try (Connection c = ConexionBD.obtenerConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, desde);
            ps.setTimestamp(2, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(rs.getInt("cant") + " x " + rs.getString("nombre"));
            }
        }
        return lista;
    }

}