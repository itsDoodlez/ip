/**
 * Represents an error caused by an invalid command or task operation in Nova.
 */
public class NovaException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message that can be shown to the user.
     *
     * @param message explanation of what went wrong
     */
    public NovaException(String message) {
        super(message);
    }
}
