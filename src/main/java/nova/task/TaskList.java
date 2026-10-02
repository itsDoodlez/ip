package nova.task;

import java.util.ArrayList;
import java.util.List;

import nova.exception.NovaException;

/**
 * Stores Nova's tasks, including tasks loaded from a previous session.
 */
public class TaskList {
    private final List<Task> tasks;

    public TaskList() {
        tasks = new ArrayList<>();
    }

    /**
     * Adds a task to the end of the dynamically sized list.
     *
     * @param task task to add
     */
    public void addTask(Task task) {
        tasks.add(task);
    }

    /**
     * Returns a task using its one-based position in the list.
     *
     * @param taskNumber one-based task number
     * @return the requested task
     * @throws NovaException if the task number does not refer to a task
     */
    public Task getTask(int taskNumber) throws NovaException {
        validateTaskNumber(taskNumber);
        return tasks.get(taskNumber - 1);
    }

    /**
     * Removes a task using its displayed number. Later tasks shift up automatically.
     *
     * @param taskNumber one-based task number
     * @return the removed task, for displaying confirmation
     * @throws NovaException if the task number does not refer to a task
     */
    public Task removeTask(int taskNumber) throws NovaException {
        validateTaskNumber(taskNumber);
        return tasks.remove(taskNumber - 1);
    }

    public int getTaskCount() {
        return tasks.size();
    }

    /**
     * Checks the displayed task number before converting it to a list index.
     *
     * @param taskNumber one-based task number
     * @throws NovaException if the list is empty or the number is outside its range
     */
    private void validateTaskNumber(int taskNumber) throws NovaException {
        if (tasks.isEmpty()) {
            throw new NovaException(" OOPS! Your task list is empty.");
        }
        if (taskNumber < 1 || taskNumber > tasks.size()) {
            throw new NovaException(
                    " OOPS! There is no task numbered " + taskNumber + ". Please choose a number from 1 to "
                            + tasks.size() + ".");
        }
    }
}
