public class DetalleItem {
    private final int idDetalle;
    private final String producto;
    private final int cantidad;
    private final double precioUnitario;
    private final String estado;

    public DetalleItem(int idDetalle, String producto, int cantidad, double precioUnitario, String estado) {
        this.idDetalle = idDetalle;
        this.producto = producto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.estado = estado;
    }

    public int getIdDetalle() { return idDetalle; }
    public String getProducto() { return producto; }
    public int getCantidad() { return cantidad; }
    public double getPrecioUnitario() { return precioUnitario; }
    public double getSubtotal() { return cantidad * precioUnitario; }
    public String getEstado() { return estado; }
}