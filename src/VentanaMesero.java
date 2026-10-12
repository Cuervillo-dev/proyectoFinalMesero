import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import javax.swing.table.DefaultTableModel;
import javax.swing.Timer;
import java.util.List;


public class VentanaMesero extends JFrame {
    private final JTextArea txtNotifPedidos = new JTextArea(5, 20);
    private final JTextArea txtNotifCuenta = new JTextArea(5, 20);
    private int ultimoDetalleVisto = 0;
    private volatile boolean escaneando = false;

    private final java.util.Set<Integer> mesasYaAvisadas = new java.util.HashSet<>();
    private final JPanel panelMesas = new JPanel(new GridLayout(1, 0, 12, 12));
    private final JTabbedPane tabs = new JTabbedPane();
    private PanelAdmin panelAdmin;
    private boolean adminAutenticado = false;

    private final JTable tablaPedidos = new JTable();
    private final DefaultTableModel modeloPedidos =
            new DefaultTableModel(new Object[]{"idPedido", "Mesa", "Estado", "Total"}, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };

    private final JTable tablaDetalle = new JTable();
    private final DefaultTableModel modeloDetalle =
            new DefaultTableModel(new Object[]{"idDetalle", "Producto", "Cant.", "Precio", "Subtotal", "Estado"}, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };

    public VentanaMesero() {
        super("Food Zone - Atención Mesero");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 380);
        setLocationRelativeTo(null);

        panelMesas.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JButton btnRefrescar = new JButton("Refrescar");
        btnRefrescar.addActionListener(e -> cargarMesas());

        tablaPedidos.setModel(modeloPedidos);
        tablaDetalle.setModel(modeloDetalle);

        tablaPedidos.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) cargarDetalle();
        });

        JButton btnEntregado = new JButton("Marcar entregado");
        btnEntregado.addActionListener(e -> marcarEntregadoSeleccionado());

        JPanel panelPedidos = new JPanel(new BorderLayout(8, 8));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(tablaPedidos), new JScrollPane(tablaDetalle));
        split.setResizeWeight(0.4);
        panelPedidos.add(split, BorderLayout.CENTER);
        panelPedidos.add(btnEntregado, BorderLayout.SOUTH);

        panelAdmin = new PanelAdmin(this, this::cerrarSesionAdmin);
        tabs.addTab("Mesas", panelMesas);
        tabs.addTab("Pedidos", panelPedidos);
        tabs.addTab("Administración", panelAdmin);

        // Al entrar a Administración pide login; al salir de la pestaña se cierra la sesión
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == panelAdmin) {
                if (!adminAutenticado) {
                    if (pedirLogin()) {
                        adminAutenticado = true;
                        panelAdmin.cargar();
                    } else {
                        tabs.setSelectedIndex(0);
                    }
                }
            } else {
                adminAutenticado = false;
            }
        });

        txtNotifPedidos.setEditable(false);
        txtNotifCuenta.setEditable(false);

        JButton btnLimpiar = new JButton("Limpiar");
        btnLimpiar.addActionListener(e -> {
            txtNotifPedidos.setText("");
            txtNotifCuenta.setText("");
        });

        JPanel panelNotif = new JPanel(new BorderLayout(5, 5));

        JPanel cajasNotif = new JPanel(new GridLayout(1, 2, 8, 0));
        cajasNotif.add(crearCajaNotif("Productos agregados", txtNotifPedidos));
        cajasNotif.add(crearCajaNotif("Cuenta solicitada", txtNotifCuenta));

        panelNotif.add(cajasNotif, BorderLayout.CENTER);
        panelNotif.add(btnLimpiar, BorderLayout.SOUTH);

        add(panelNotif, BorderLayout.EAST);
        add(tabs, BorderLayout.CENTER);
        JButton btnVentas = new JButton("Ventas del día");
        btnVentas.addActionListener(e -> mostrarVentasDelDia());

        JPanel panelSur = new JPanel(new GridLayout(1, 2, 4, 0));
        panelSur.add(btnRefrescar);
        panelSur.add(btnVentas);
        add(panelSur, BorderLayout.SOUTH);

        cargarMesas();
        inicializarBaseline();
        iniciarEscaneo();
    }

    private void cargarPedidos() {
        modeloPedidos.setRowCount(0);
        try {
            for (PedidoResumen p : PedidoDao.listarActivos()) {
                modeloPedidos.addRow(new Object[]{
                        p.getIdPedido(), p.getNumeroMesa(), p.getEstado(),
                        String.format("C$%.2f", p.getTotal())});
            }
        } catch (SQLException ex) {
            mostrarError(ex);
        }
        modeloDetalle.setRowCount(0);
    }

    private void cargarDetalle() {
        modeloDetalle.setRowCount(0);
        int fila = tablaPedidos.getSelectedRow();
        if (fila < 0) return;
        int idPedido = (int) modeloPedidos.getValueAt(fila, 0);
        try {
            for (DetalleItem d : PedidoDao.detalle(idPedido)) {
                modeloDetalle.addRow(new Object[]{
                        d.getIdDetalle(), d.getProducto(), d.getCantidad(),
                        String.format("C$%.2f", d.getPrecioUnitario()),
                        String.format("C$%.2f", d.getSubtotal()), d.getEstado()});
            }
        } catch (SQLException ex) {
            mostrarError(ex);
        }
    }

    private void marcarEntregadoSeleccionado() {
        int fila = tablaDetalle.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Selecciona un producto del detalle.");
            return;
        }
        int idDetalle = (int) modeloDetalle.getValueAt(fila, 0);
        try {
            PedidoDao.marcarEntregado(idDetalle);
            cargarDetalle();
        } catch (SQLException ex) {
            mostrarError(ex);
        }
    }

    // Vuelve a leer las mesas de la BD y redibuja las tarjetas
    private void cargarMesas() {
        panelMesas.removeAll();
        try {
            for (Mesa m : MesaDao.listar()) {
                panelMesas.add(crearTarjeta(m));
            }
        } catch (SQLException ex) {
            mostrarError(ex);
        }
        panelMesas.revalidate();
        panelMesas.repaint();
        cargarPedidos();
    }

    private JPanel crearTarjeta(Mesa m) {
        JPanel card = new JPanel(new GridLayout(5, 1, 4, 4));
        card.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JLabel lblMesa = new JLabel("Mesa " + m.getNumero(), SwingConstants.CENTER);
        lblMesa.setFont(lblMesa.getFont().deriveFont(Font.BOLD, 18f));

        JLabel lblEstado = new JLabel(m.getEstado(), SwingConstants.CENTER);
        lblEstado.setForeground(m.isLibre() ? new Color(0, 140, 0) : Color.RED);

        JButton btnOcupar = new JButton("Ocupar");
        btnOcupar.setEnabled(m.isLibre());          // solo si está libre
        btnOcupar.addActionListener(e -> {
            try {
                if (!MesaDao.ocupar(m.getNumero())) {
                    JOptionPane.showMessageDialog(this, "Esa mesa ya estaba ocupada.");
                }
            } catch (SQLException ex) {
                mostrarError(ex);
            }
            cargarMesas();
        });

        JButton btnRecibo = new JButton("Recibo");
        btnRecibo.setEnabled(!m.isLibre());         // solo si está ocupada
        btnRecibo.addActionListener(e -> {
            try {
                java.nio.file.Path ruta = ReciboService.generarCsv(m.getNumero());
                mostrarRecibo("Recibo guardado en:", ruta);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "No se pudo generar", JOptionPane.WARNING_MESSAGE);
            }
        });

        JButton btnLiberar = new JButton("Liberar");
        btnLiberar.setEnabled(!m.isLibre());        // solo si está ocupada
        btnLiberar.addActionListener(e -> {
            int numero = m.getNumero();
            try {
                Integer idPedido = PedidoDao.idPedidoActivoDeMesa(numero);
                boolean faltaRecibo = idPedido != null && !ReciboService.yaImpreso(idPedido);

                String pregunta = "¿Cerrar la cuenta y liberar la mesa " + numero + "?";
                if (faltaRecibo) pregunta += "\nEl recibo aún no se ha impreso: se generará automáticamente.";
                int resp = JOptionPane.showConfirmDialog(this, pregunta,
                        "Liberar mesa", JOptionPane.YES_NO_OPTION);
                if (resp != JOptionPane.YES_OPTION) return;

                // 1) Recibo solo si falta (si falla, NO se cierra la cuenta)
                java.nio.file.Path ruta = null;
                if (faltaRecibo) ruta = ReciboService.generarCsv(numero);

                // 2) Pedido -> PAGADO y mesa -> LIBRE (transacción)
                PedidoDao.cerrarCuenta(numero);

                // 3) Limpiar aviso de cuenta solicitada
                mesasYaAvisadas.remove(numero);

                // 4) Refrescar antes de mostrar el diálogo
                cargarMesas();

                if (ruta != null) {
                    mostrarRecibo("Mesa liberada.\nRecibo generado automáticamente en:", ruta);
                } else {
                    JOptionPane.showMessageDialog(this, idPedido != null
                            ? "Mesa liberada.\nEl recibo ya estaba impreso."
                            : "Mesa liberada.");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "No se pudo liberar la mesa " + numero + ":\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        card.add(lblMesa);
        card.add(lblEstado);
        card.add(btnOcupar);
        card.add(btnRecibo);
        card.add(btnLiberar);
        return card;
    }

    // El "día" de ventas va de INICIO_DIA a FIN_DIA (hora local)
    private static final java.time.LocalTime INICIO_DIA = java.time.LocalTime.of(1, 0);
    private static final java.time.LocalTime FIN_DIA = java.time.LocalTime.of(23, 0);

    private void mostrarVentasDelDia() {
        java.time.LocalDateTime ahora = java.time.LocalDateTime.now();
        // Antes de la 1:00 a.m. todavía cuenta el día anterior
        java.time.LocalDate dia = ahora.toLocalTime().isBefore(INICIO_DIA)
                ? ahora.toLocalDate().minusDays(1) : ahora.toLocalDate();
        java.time.LocalDateTime desde = dia.atTime(INICIO_DIA);
        java.time.LocalDateTime hasta = dia.atTime(FIN_DIA);

        try {
            java.sql.Timestamp tDesde = java.sql.Timestamp.valueOf(desde);
            java.sql.Timestamp tHasta = java.sql.Timestamp.valueOf(hasta);
            double[] r = PedidoDao.resumenVentas(tDesde, tHasta);
            List<String> productos = PedidoDao.productosVendidos(tDesde, tHasta);

            java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            StringBuilder sb = new StringBuilder();
            sb.append("Del ").append(desde.format(f)).append("\nal ").append(hasta.format(f)).append("\n\n");
            sb.append("Cuentas cerradas: ").append((int) r[0]).append("\n");
            sb.append(String.format("Total vendido: C$%.2f%n", r[1]));
            sb.append("\nProductos vendidos:\n");
            if (productos.isEmpty()) sb.append("  (ninguno)\n");
            for (String p : productos) sb.append("  ").append(p).append("\n");

            JTextArea area = new JTextArea(sb.toString(), 14, 32);
            area.setEditable(false);
            String[] opciones = {"Guardar ventas en CSV", "Cerrar"};
            int op = JOptionPane.showOptionDialog(this, new JScrollPane(area),
                    "Ventas del día", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                    null, opciones, opciones[1]);
            if (op == 0) {
                java.nio.file.Path ruta = VentasService.generarCsv(desde, hasta);
                mostrarArchivo("Ventas del día", "Ventas guardadas en:", ruta, "Abrir archivo");
            }
        } catch (Exception ex) {
            mostrarError(ex);
        }
    }

    private boolean pedirLogin() {
        JTextField txtUsuario = new JTextField(15);
        JPasswordField txtPass = new JPasswordField(15);
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(new JLabel("Usuario:"));
        form.add(txtUsuario);
        form.add(new JLabel("Contraseña:"));
        form.add(txtPass);

        int r = JOptionPane.showConfirmDialog(this, form, "Administración",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return false;
        try {
            if (UsuarioDao.validar(txtUsuario.getText().trim(), new String(txtPass.getPassword()))) {
                return true;
            }
            JOptionPane.showMessageDialog(this, "Usuario o contraseña incorrectos.",
                    "Acceso denegado", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            mostrarError(ex);
        }
        return false;
    }

    private void cerrarSesionAdmin() {
        adminAutenticado = false;
        tabs.setSelectedIndex(0);
    }

    private void mostrarError(Exception ex) {
        JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }

    // Diálogo con la ruta del recibo y botones para abrirlo
    private void mostrarRecibo(String mensaje, java.nio.file.Path ruta) {
        mostrarArchivo("Recibo", mensaje, ruta, "Abrir recibo");
    }

    private void mostrarArchivo(String titulo, String mensaje, java.nio.file.Path ruta, String etiquetaAbrir) {
        String[] opciones = {etiquetaAbrir, "Abrir carpeta", "Cerrar"};
        int op = JOptionPane.showOptionDialog(this,
                mensaje + "\n" + ruta.toAbsolutePath(),
                titulo, JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, opciones, opciones[0]);
        try {
            if (op == 0) abrirArchivo(ruta.toAbsolutePath().toFile());
            else if (op == 1) abrirArchivo(ruta.toAbsolutePath().getParent().toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo abrir:\n" + ex.getMessage(),
                    "Error", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void abrirArchivo(java.io.File f) throws java.io.IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(f);
        } else {
            new ProcessBuilder("xdg-open", f.getAbsolutePath()).start();   // respaldo en Linux
        }
    }

    private JPanel crearCajaNotif(String titulo, JTextArea area) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(titulo));
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        return p;
    }

    // Toma una "foto" del estado actual, para no re-avisar lo que ya existía antes de abrir la ventana
    private void inicializarBaseline() {
        try {
            ultimoDetalleVisto = PedidoDao.maxIdDetalle();
            mesasYaAvisadas.addAll(PedidoDao.mesasConCuentaSolicitada());
        } catch (SQLException ex) {
            mostrarError(ex);
        }
    }

    private void iniciarEscaneo() {
        Timer timer = new Timer(5000, e -> escanear());
        timer.start();
    }

    private void escanear() {
        if (escaneando) return;   // el anterior no ha terminado, no dispares otro
        escaneando = true;

        new SwingWorker<Void, Void>() {
            List<DetalleItem> nuevosProductos;
            List<Integer> mesasCuenta;
            Exception error;

            @Override protected Void doInBackground() {
                try {
                    nuevosProductos = PedidoDao.nuevosDesde(ultimoDetalleVisto);
                    mesasCuenta = PedidoDao.mesasConCuentaSolicitada();

                } catch (Exception ex) {
                    error = ex;
                }
                return null;
            }

            @Override protected void done() {
                escaneando = false;

                if (error != null) {
                    txtNotifPedidos.append(error.getClass().getSimpleName() + ": " + error.getMessage() + "\n");
                    return;
                }
                boolean huboAlgo = false;

                for (DetalleItem d : nuevosProductos) {
                    txtNotifPedidos.append(d.getCantidad() + " x " + d.getProducto() + "\n");
                    ultimoDetalleVisto = d.getIdDetalle();
                    huboAlgo = true;
                }

                for (int mesa : mesasCuenta) {
                    if (mesasYaAvisadas.add(mesa)) {   // true solo la primera vez
                        txtNotifCuenta.append("Mesa " + mesa + " pidió la cuenta\n");
                        huboAlgo = true;
                    }
                }

                if (huboAlgo) {
                    Toolkit.getDefaultToolkit().beep();
                    cargarMesas();   // refresca pestañas Mesas y Pedidos también
                }
            }
        }.execute();
    }
}