public class PedidoResumen {
    private final int idPedido;
    private final int numeroMesa;
    private final String estado;
    private final double total;

    public PedidoResumen(int idPedido, int numeroMesa, String estado, double total) {
        this.idPedido = idPedido;
        this.numeroMesa = numeroMesa;
        this.estado = estado;
        this.total = total;
    }

    public int getIdPedido() { return idPedido; }
    public int getNumeroMesa() { return numeroMesa; }
    public String getEstado() { return estado; }
    public double getTotal() { return total; }
}