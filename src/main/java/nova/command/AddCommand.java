package nova.command;

import nova.exception.NovaException;
import nova.storage.Storage;
import nova.task.Task;
import nova.task.TaskList;
import nova.ui.Ui;

/**
 * Adds a parsed task, sharing the same execution steps for todos, deadlines, and events.
 */
public class AddCommand extends Command {
    private final Task task;

    /**
     * Prepares an addition without changing the list or writing to storage.
     *
     * @param task the task constructed from validated command fields
     */
    public AddCommand(Task task) {
        this.task = task;
    }

    /**
     * Adds the task, shows confirmation, and saves the updated list once.
     * A failed save leaves the addition in memory, matching Nova's existing behavior.
     *
     * @param tasks the list to which the prepared task is added
     * @param ui displays the added task and updated list size
     * @param storage saves the updated list
     * @throws NovaException if the updated list cannot be saved
     */
    @Override
    public void execute(TaskList tasks, Ui ui, Storage storage) throws NovaException {
        tasks.addTask(task);
        ui.showTaskAdded(task, tasks.getTaskCount());
        storage.save(tasks);
    }
}
