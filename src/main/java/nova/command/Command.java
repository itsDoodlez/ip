package nova.command;

import nova.exception.NovaException;
import nova.storage.Storage;
import nova.task.TaskList;
import nova.ui.Ui;

/**
 * Represents a user action whose arguments have already been parsed.
 * Subclasses perform the action through a common execution method.
 */
public abstract class Command {
    /**
     * Performs the action, displays feedback, and saves any task changes.
     *
     * @param tasks the current task list
     * @param ui displays the result to the user
     * @param storage saves changes made by this command
     * @throws NovaException if the action or saving its changes fails
     */
    public abstract void execute(TaskList tasks, Ui ui, Storage storage) throws NovaException;

    /**
     * Indicates whether the application should exit after this command completes.
     * Ordinary commands keep the application running.
     *
     * @return false unless a subclass represents an exit command
     */
    public boolean isExit() {
        return false;
    }
}
