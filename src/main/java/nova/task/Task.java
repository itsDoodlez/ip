package nova.task;

/**
 * Holds the description and completion status shared by every task type.
 */
public class Task {
    private String description;
    private boolean isDone;

    /**
     * Creates an incomplete task with the supplied description.
     *
     * @param description text describing what needs to be done
     */
    public Task(String description) {
        this.description = description;
        this.isDone = false;
    }

    /**
     * Marks this task as completed.
     */
    public void markAsDone() {
        isDone = true;
    }

    /**
     * Marks this task as incomplete.
     */
    public void markAsNotDone() {
        isDone = false;
    }

    /**
     * Returns the character used to display this task's completion status.
     *
     * @return {@code "X"} for a completed task, or a single space otherwise
     */
    public String getStatusIcon() {
        return isDone ? "X" : " ";
    }

    /**
     * Returns the description without status icons or scheduling details.
     *
     * @return the task description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Reports whether this task has been completed.
     *
     * @return {@code true} if the task is marked as done
     */
    public boolean isDone() {
        return isDone;
    }

    /**
     * Formats the shared part of a task's console display.
     *
     * @return the status icon in brackets followed by the description
     */
    @Override
    public String toString() {
        return "[" + getStatusIcon() + "] " + description;
    }
}
