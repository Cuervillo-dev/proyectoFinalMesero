import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ReciboService {

    private static final String SEP = ",";          // si Excel lo abre en una sola columna, cambia a ";"
    private static final Path CARPETA = Paths.get("recibos");

    public static Path generarCsv(int numeroMesa) throws SQLException, IOException {
        Integer idPedido = PedidoDao.idPedidoActivoDeMesa(numeroMesa);
        if (idPedido == null) {
            throw new IllegalStateException("La mesa " + numeroMesa + " no tiene un pedido activo.");
        }
        List<LineaRecibo> lineas = PedidoDao.lineasRecibo(idPedido);
        LocalDateTime ahora = LocalDateTime.now();

        StringBuilder sb = new StringBuilder("\uFEFF");   // BOM para que Excel respete acentos
        sb.append("Food Zone Restaurante\n");
        sb.append("Recibo").append(SEP).append(idPedido).append("\n");
        sb.append("Mesa").append(SEP).append(numeroMesa).append("\n");
        sb.append("Fecha").append(SEP)
                .append(ahora.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
        sb.append("Producto").append(SEP).append("Cantidad").append(SEP)
                .append("Precio unitario (C$)").append(SEP).append("Subtotal (C$)").append("\n");

        double total = 0;
        for (LineaRecibo l : lineas) {
            total += l.subtotal();
            sb.append(esc(l.nombre())).append(SEP)
                    .append(l.cantidad()).append(SEP)
                    .append(num(l.precioUnitario())).append(SEP)
                    .append(num(l.subtotal())).append("\n");
        }
        sb.append("TOTAL").append(SEP).append(SEP).append(SEP).append(num(total)).append("\n");

        Files.createDirectories(CARPETA);
        String nombre = "recibo_mesa" + numeroMesa + "_" +
                ahora.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        Path destino = CARPETA.resolve(nombre);
        Files.writeString(destino, sb.toString(), StandardCharsets.UTF_8);
        PedidoDao.marcarReciboImpreso(idPedido, total);
        return destino;
    }

    private static String num(double v) { return String.format(Locale.US, "%.2f", v); }

    private static String esc(String s) {   // protege comas y comillas en el nombre
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    // true si ya hay un recibo impreso con el total actual del pedido
    public static boolean yaImpreso(int idPedido) throws SQLException {
        Double impreso = PedidoDao.reciboTotal(idPedido);
        return impreso != null && Math.abs(impreso - PedidoDao.totalPedido(idPedido)) < 0.005;
    }
}