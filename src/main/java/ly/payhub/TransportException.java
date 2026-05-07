package ly.payhub;

/** Network / decode failures — never reached the server cleanly. */
public class TransportException extends RuntimeException {
    public TransportException(String message) { super(message); }

    public static final class Timeout extends TransportException {
        public Timeout(String m) { super("payhub: timeout: " + m); }
    }
    public static final class Connection extends TransportException {
        public Connection(String m) { super("payhub: connection: " + m); }
    }
    public static final class Decode extends TransportException {
        public Decode(String m) { super("payhub: decode: " + m); }
    }
}
