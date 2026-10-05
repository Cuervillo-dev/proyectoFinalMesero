import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class GestorCredenciales {
    private static final Properties propiedades = new Properties();

    static {
        try (InputStream input = new FileInputStream("env.properties")) {
            propiedades.load(input);
        } catch (Exception e) {
            System.err.println("Advertencia: No se pudo cargar env.properties, intentando variables del sistema.");
        }
    }

    public static String get(String clave) {
        // Primero busca en el archivo env.properties, si no existe busca en las variables de entorno del sistema (OS)
        String valor = propiedades.getProperty(clave);
        if (valor == null || valor.isEmpty()) {
            valor = System.getenv(clave);
        }
        return valor;
    }
}