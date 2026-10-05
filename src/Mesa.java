public class Mesa {
    private final int numero;
    private final String estado;   // "LIBRE" u "OCUPADA"

    public Mesa(int numero, String estado) {
        this.numero = numero;
        this.estado = estado;
    }

    public int getNumero() { return numero; }
    public String getEstado() { return estado; }
    public boolean isLibre() { return "LIBRE".equals(estado); }
}