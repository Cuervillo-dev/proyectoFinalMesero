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
        setSize(800, 280);
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

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Mesas", panelMesas);
        tabs.addTab("Pedidos", panelPedidos);

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
        add(btnRefrescar, BorderLayout.SOUTH);

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
        JPanel card = new JPanel(new GridLayout(4, 1, 4, 4));
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

        JButton btnLiberar = new JButton("Liberar");
        btnLiberar.setEnabled(!m.isLibre());        // solo si está ocupada
        btnLiberar.addActionListener(e -> {
            int r = JOptionPane.showConfirmDialog(this,
                    "¿Liberar la mesa " + m.getNumero() + "?\nSe cerrará su cuenta.",
                    "Confirmar", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
            try {
                MesaDao.liberar(m.getNumero());
                mesasYaAvisadas.remove(m.getNumero());   // solo al liberar de verdad
            } catch (SQLException ex) {
                mostrarError(ex);
            }
            cargarMesas();
        });

        card.add(lblMesa);
        card.add(lblEstado);
        card.add(btnOcupar);
        card.add(btnLiberar);
        return card;
    }

    private void mostrarError(Exception ex) {
        JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
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