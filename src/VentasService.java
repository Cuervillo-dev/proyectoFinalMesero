import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class VentasService {

    private static final String SEP = ",";            // si Excel lo abre en una sola columna, cambia a ";"
    private static final Path CARPETA = Paths.get("ventas");

    public static Path generarCsv(LocalDateTime desde, LocalDateTime hasta) throws SQLException, IOException {
        Timestamp tDesde = Timestamp.valueOf(desde);
        Timestamp tHasta = Timestamp.valueOf(hasta);
        DateTimeFormatter fecha = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        StringBuilder cuentas = new StringBuilder();
        StringBuilder productos = new StringBuilder();
        int numCuentas = 0;
        double total = 0;

        try (Connection c = ConexionBD.obtenerConexion()) {

            // Cuentas cerradas en el rango
            String sqlCuentas =
                    "SELECT p.idPedido, m.numeroMesa, p.fechaCierre, " +
                            "       COALESCE(SUM(d.cantidad * d.precioUnitario), 0) AS total " +
                            "FROM Pedidos p " +
                            "JOIN Mesas m ON m.idMesa = p.idMesa " +
                            "LEFT JOIN DetallePedidos d ON d.idPedido = p.idPedido " +
                            "WHERE p.estado = 'PAGADO' AND p.fechaCierre >= ? AND p.fechaCierre <= ? " +
                            "GROUP BY p.idPedido, m.numeroMesa, p.fechaCierre " +
                            "ORDER BY p.fechaCierre";
            try (PreparedStatement ps = c.prepareStatement(sqlCuentas)) {
                ps.setTimestamp(1, tDesde);
                ps.setTimestamp(2, tHasta);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double t = rs.getDouble("total");
                        numCuentas++;
                        total += t;
                        cuentas.append(rs.getInt("idPedido")).append(SEP)
                                .append(rs.getInt("numeroMesa")).append(SEP)
                                .append(rs.getTimestamp("fechaCierre").toLocalDateTime().format(fecha)).append(SEP)
                                .append(num(t)).append("\n");
                    }
                }
            }

            // Productos vendidos en el rango
            String sqlProductos =
                    "SELECT pr.nombre, SUM(d.cantidad) AS cant, SUM(d.cantidad * d.precioUnitario) AS monto " +
                            "FROM Pedidos p " +
                            "JOIN DetallePedidos d ON d.idPedido = p.idPedido " +
                            "JOIN Productos pr ON pr.idProducto = d.idProducto " +
                            "WHERE p.estado = 'PAGADO' AND p.fechaCierre >= ? AND p.fechaCierre <= ? " +
                            "GROUP BY pr.nombre ORDER BY cant DESC";
            try (PreparedStatement ps = c.prepareStatement(sqlProductos)) {
                ps.setTimestamp(1, tDesde);
                ps.setTimestamp(2, tHasta);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        productos.append(esc(rs.getString("nombre"))).append(SEP)
                                .append(rs.getInt("cant")).append(SEP)
                                .append(num(rs.getDouble("monto"))).append("\n");
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder("\uFEFF");   // BOM para que Excel respete acentos
        sb.append("Food Zone Restaurante\n");
        sb.append("Ventas del dia\n");
        sb.append("Desde").append(SEP).append(desde.format(fecha)).append("\n");
        sb.append("Hasta").append(SEP).append(hasta.format(fecha)).append("\n");
        sb.append("Cuentas cerradas").append(SEP).append(numCuentas).append("\n");
        sb.append("Total vendido (C$)").append(SEP).append(num(total)).append("\n\n");

        sb.append("CUENTAS\n");
        sb.append("Recibo").append(SEP).append("Mesa").append(SEP)
                .append("Fecha de cierre").append(SEP).append("Total (C$)").append("\n");
        sb.append(cuentas).append("\n");

        sb.append("PRODUCTOS VENDIDOS\n");
        sb.append("Producto").append(SEP).append("Cantidad").append(SEP).append("Monto (C$)").append("\n");
        sb.append(productos);

        Files.createDirectories(CARPETA);
        String nombre = "ventas_" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        Path destino = CARPETA.resolve(nombre);
        Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8);
        return destino;
    }

    private static String num(double v) { return String.format(Locale.US, "%.2f", v); }

    private static String esc(String s) {   // protege comas y comillas en el nombre
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}