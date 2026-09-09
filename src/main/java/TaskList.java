/**
 * Stores the tasks created during the current Nova session.
 */
public class TaskList {
    private static final int MAX_TASKS = 100;

    private Task[] tasks;
    private int taskCount;

    public TaskList() {
        tasks = new Task[MAX_TASKS];
        taskCount = 0;
    }

    /**
     * Adds a task unless the list has reached its fixed capacity.
     *
     * @param task task to add
     * @throws NovaException if the task list is full
     */
    public void addTask(Task task) throws NovaException {
        if (taskCount >= MAX_TASKS) {
            throw new NovaException(
                    " OOPS! Your task list is full. Remove a task before adding another one.");
        }
        tasks[taskCount] = task;
        taskCount++;
    }

    public void listTasks() {
        System.out.println(" Here are the tasks in your list:");

        for (int i = 0; i < taskCount; i++) {
            System.out.println(" " + (i + 1) + "." + tasks[i]);
        }
    }

    /**
     * Returns a task using the one-based number shown by {@link #listTasks()}.
     *
     * @param taskNumber one-based task number
     * @return the requested task
     * @throws NovaException if the task number does not refer to a task
     */
    public Task getTask(int taskNumber) throws NovaException {
        if (taskNumber < 1 || taskNumber > taskCount) {
            throw new NovaException(
                    " OOPS! There is no task numbered " + taskNumber + ". Please choose a number from 1 to "
                            + taskCount + ".");
        }
        return tasks[taskNumber - 1];
    }

    public int getTaskCount() {
        return taskCount;
    }
}
