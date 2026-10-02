package nova.task;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Represents a task that must be completed by a calendar date.
 */
public class Deadline extends Task {
    /** Keeps month names in English regardless of the computer's locale. */
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd uuuu", Locale.ENGLISH);
    private final LocalDate by;

    /**
     * Creates an incomplete task with a calendar due date.
     *
     * @param description text describing what needs to be done
     * @param by the date by which the task should be completed
     */
    public Deadline(String description, LocalDate by) {
        super(description);
        this.by = by;
    }

    /**
     * Returns the due date as a date value, independent of its display format.
     *
     * @return the task's due date
     */
    public LocalDate getBy() {
        return by;
    }

    /**
     * Formats this deadline with an English month name, day, and year.
     *
     * @return the {@code [D]} label, completion status, description, and due date
     */
    @Override
    public String toString() {
        return "[D]" + super.toString() + " (by: " + by.format(DISPLAY_FORMAT) + ")";
    }
}
