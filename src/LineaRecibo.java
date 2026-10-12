public record LineaRecibo(String nombre, int cantidad, double precioUnitario) {
    public double subtotal() { return cantidad * precioUnitario; }
}