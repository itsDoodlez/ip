package nova.task;

/**
 * Represents a task with a start and an end date or time.
 */
public class Event extends Task {
    private final String from;
    private final String to;

    /**
     * Creates an incomplete event, retaining its start and end as text.
     *
     * @param description text describing the event
     * @param from the event's start date or time as entered by the user
     * @param to the event's end date or time as entered by the user
     */
    public Event(String description, String from, String to) {
        super(description);
        this.from = from;
        this.to = to;
    }

    /**
     * Returns the event's start without parsing it as a date or time.
     *
     * @return the stored start text
     */
    public String getFrom() {
        return from;
    }

    /**
     * Returns the event's end without parsing it as a date or time.
     *
     * @return the stored end text
     */
    public String getTo() {
        return to;
    }

    /**
     * Formats this event for display in the console.
     *
     * @return the {@code [E]} label, completion status, description, and start and end text
     */
    @Override
    public String toString() {
        return "[E]" + super.toString()
                + " (from: " + from + " to: " + to + ")";
    }
}
