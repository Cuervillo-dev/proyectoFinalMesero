import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PanelAdmin extends JPanel {

    private final Component ventana;
    private final DefaultTableModel modelo =
            new DefaultTableModel(new Object[]{"ID", "Nombre", "Descripción", "Categoría", "Precio (C$)"}, 0) {
                public boolean isCellEditable(int r, int c) { return false; }
            };
    private final JTable tabla = new JTable(modelo);
    private List<Producto> productos = new ArrayList<>();

    public PanelAdmin(Component ventana, Runnable alCerrarSesion) {
        super(new BorderLayout(8, 8));
        this.ventana = ventana;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton btnAgregar = new JButton("Agregar producto");
        btnAgregar.addActionListener(e -> agregar());

        JButton btnModificar = new JButton("Modificar");
        btnModificar.addActionListener(e -> modificar());

        JButton btnEliminar = new JButton("Eliminar");
        btnEliminar.addActionListener(e -> eliminar());

        JButton btnSalir = new JButton("Cerrar sesión");
        btnSalir.addActionListener(e -> alCerrarSesion.run());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        botones.add(btnAgregar);
        botones.add(btnModificar);
        botones.add(btnEliminar);
        botones.add(btnSalir);

        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
    }

    // Vuelve a leer los productos de la BD
    public void cargar() {
        modelo.setRowCount(0);
        try {
            productos = ProductoDao.listarActivos();
            for (Producto p : productos) {
                modelo.addRow(new Object[]{p.id(), p.nombre(), p.descripcion(), p.categoria(),
                        String.format(Locale.US, "%.2f", p.precio())});
            }
        } catch (SQLException ex) {
            mostrarError(ex);
        }
    }

    private void agregar() {
        Producto nuevo = pedirDatos("Agregar producto", null);
        if (nuevo == null) return;
        try {
            ProductoDao.agregar(nuevo);
            cargar();
        } catch (SQLException ex) {
            mostrarError(ex);
        }
    }

    private void modificar() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(ventana, "Selecciona un producto de la tabla.");
            return;
        }
        Producto base = productos.get(fila);
        Producto editado = pedirDatos("Modificar producto", base);
        if (editado == null) return;
        try {
            ProductoDao.actualizar(editado);
            cargar();
        } catch (SQLException ex) {
            mostrarError(ex);
        }
    }

    private void eliminar() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(ventana, "Selecciona un producto de la tabla.");
            return;
        }
        Producto p = productos.get(fila);
        int r = JOptionPane.showConfirmDialog(ventana,
                "¿Eliminar \"" + p.nombre() + "\" del menú?\nLos pedidos y ventas anteriores se conservan.",
                "Confirmar", JOptionPane.YES_NO_OPTION);
        if (r != JOptionPane.YES_OPTION) return;
        try {
            ProductoDao.desactivar(p.id());
            cargar();
        } catch (SQLException ex) {
            mostrarError(ex);
        }
    }

    // Formulario para agregar (base == null) o modificar. Devuelve null si cancela
    private Producto pedirDatos(String titulo, Producto base) {
        JTextField txtNombre = new JTextField(base == null ? "" : base.nombre(), 20);
        JTextField txtDesc = new JTextField(base == null ? "" : base.descripcion(), 20);
        JTextField txtPrecio = new JTextField(base == null ? "" : String.format(Locale.US, "%.2f", base.precio()), 20);

        JComboBox<String> cmbCategoria = new JComboBox<>(Producto.CATEGORIAS);
        if (base != null) cmbCategoria.setSelectedItem(base.categoria());

        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        form.add(new JLabel("Nombre:"));
        form.add(txtNombre);
        form.add(new JLabel("Descripción:"));
        form.add(txtDesc);
        form.add(new JLabel("Categoría:"));
        form.add(cmbCategoria);
        form.add(new JLabel("Precio (C$):"));
        form.add(txtPrecio);

        while (true) {
            int r = JOptionPane.showConfirmDialog(ventana, form, titulo,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return null;
            try {
                String nombre = txtNombre.getText().trim();
                if (nombre.isEmpty()) throw new IllegalArgumentException();
                double precio = Double.parseDouble(txtPrecio.getText().trim().replace(',', '.'));
                if (precio <= 0) throw new IllegalArgumentException();
                return new Producto(base == null ? 0 : base.id(), nombre,
                        txtDesc.getText().trim(), (String) cmbCategoria.getSelectedItem(), precio);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(ventana,
                        "Revisa los datos: el nombre es obligatorio y el precio debe ser un número mayor que 0.",
                        "Datos inválidos", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void mostrarError(Exception ex) {
        JOptionPane.showMessageDialog(ventana, "Error: " + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}