package nova.task;

/**
 * Represents a task without a scheduled date or time.
 */
public class Todo extends Task {

    /**
     * Creates an incomplete task without scheduling information.
     *
     * @param description text describing what needs to be done
     */
    public Todo(String description) {
        super(description);
    }

    /**
     * Formats this todo for display in the console.
     *
     * @return the {@code [T]} label, completion status, and description
     */
    @Override
    public String toString() {
        return "[T]" + super.toString();
    }
}
