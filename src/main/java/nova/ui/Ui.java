package nova.ui;

import java.util.List;
import java.util.Scanner;

import nova.exception.NovaException;
import nova.task.Task;
import nova.task.TaskList;

/**
 * Reads console input and displays Nova's messages and task lists.
 * Owns the input scanner, which is closed when the application exits.
 */
public class Ui implements AutoCloseable {
    private static final String DIVIDER = "____________________________________________________________\n";
    private static final String BANNER =
            ",---.   .--.    ,-----.    ,---.  ,---.   ____     \n"
                    + "|    \\  |  |  .'  .-,  '.  |   /  |   | .'  __ `.  \n"
                    + "|  ,  \\ |  | / ,-.|  \\ _ \\ |  |   |  .'/   '  \\  \\ \n"
                    + "|  |\\_ \\|  |;  \\  '_ /  | :|  | _ |  | |___|  /  | \n"
                    + "|  _( )_\\  ||  _`,/ \\ _/  ||  _( )_  |    _.-`   | \n"
                    + "| (_ o _)  |: (  '\\_/ \\   ;\\ (_ o._) /  .'   _    |\n"
                    + "|  (_,_)\\  | \\ `\"/  \\  ) /  \\ (_,_) /   |  _( )_  |\n"
                    + "|  |    |  |  '. \\_/``\".'    \\     /    \\ (_ o _) /\n"
                    + "'--'    '--'    '-----'       `---`      '.(_,_).' \n";

    private final Scanner scanner;

    /**
     * Creates a console reader backed by standard input.
     */
    public Ui() {
        scanner = new Scanner(System.in);
    }

    /**
     * Checks for another input line, waiting for input if necessary.
     *
     * @return {@code true} if another command line is available, or {@code false} at end-of-input
     */
    public boolean hasNextCommand() {
        return scanner.hasNextLine();
    }

    /**
     * Reads one complete input line without trimming spaces.
     * Call {@link #hasNextCommand()} first to check that input is available.
     *
     * @return the next command line without its line separator
     * @throws java.util.NoSuchElementException if input has ended
     */
    public String readCommand() {
        return scanner.nextLine();
    }

    /**
     * Prints the horizontal line that separates console responses.
     */
    public void showDivider() {
        System.out.print(DIVIDER);
    }

    /**
     * Displays Nova's banner, greeting, and opening prompt between dividers.
     */
    public void showWelcome() {
        showDivider();
        System.out.println(BANNER);
        System.out.println("Hello! I'm Nova.");
        System.out.println("What can I do for you?");
        showDivider();
    }

    /**
     * Displays the farewell message and a closing divider.
     */
    public void showGoodbye() {
        System.out.println("Bye. Hope to see you again soon!");
        showDivider();
    }

    /**
     * Displays an error message supplied by command handling or storage.
     *
     * @param message the explanation to show to the user
     */
    public void showError(String message) {
        System.out.println(message);
    }

    /**
     * Explains why startup stopped and reassures the user that the save is intact.
     *
     * @param message the loading error reported by storage
     */
    public void showLoadingError(String message) {
        showError(message);
        System.out.println(" Please fix the saved file or its permissions and restart Nova.");
        System.out.println(" Your file was not changed.");
        showDivider();
    }

    /**
     * Displays tasks in their stored order with one-based numbers for commands.
     *
     * @param taskList the tasks to display
     * @throws NovaException if a task cannot be retrieved by its number
     */
    public void showTasks(TaskList taskList) throws NovaException {
        System.out.println(" Here are the tasks in your list:");
        for (int i = 1; i <= taskList.getTaskCount(); i++) {
            System.out.println(" " + i + "." + taskList.getTask(i));
        }
    }

    /**
     * Displays search results numbered from one, or explains that none matched.
     * These numbers describe the results; task-changing commands still use list numbers.
     *
     * @param matches tasks matching the search, in their original order
     */
    public void showMatchingTasks(List<Task> matches) {
        System.out.println(" Here are the matching tasks in your list:");
        if (matches.isEmpty()) {
            System.out.println(" No matching tasks found.");
            return;
        }
        for (int i = 0; i < matches.size(); i++) {
            System.out.println(" " + (i + 1) + "." + matches.get(i));
        }
    }

    /**
     * Confirms an addition and displays the updated number of tasks.
     *
     * @param task the task that was added
     * @param taskCount the total number of tasks after the addition
     */
    public void showTaskAdded(Task task, int taskCount) {
        System.out.println(" Got it. I've added this task:");
        System.out.println("   " + task);
        showTaskCount(taskCount);
    }

    /**
     * Confirms a removal and displays the number of tasks remaining.
     *
     * @param task the task that was removed
     * @param taskCount the total number of tasks after the removal
     */
    public void showTaskRemoved(Task task, int taskCount) {
        System.out.println(" Noted. I've removed this task:");
        System.out.println("   " + task);
        showTaskCount(taskCount);
    }

    /**
     * Displays the appropriate confirmation after a task's status has changed.
     *
     * @param task the updated task
     */
    public void showTaskStatusChanged(Task task) {
        System.out.println(task.isDone()
                ? " Nice! I've marked this task as done:"
                : " OK, I've marked this task as not done yet:");
        System.out.println("   " + task);
    }

    /**
     * Displays the list size after an addition or removal.
     *
     * @param taskCount the current total number of tasks
     */
    private void showTaskCount(int taskCount) {
        System.out.println(" Now you have " + taskCount + " tasks in the list.");
    }

    /**
     * Closes the input scanner and its underlying standard input stream.
     */
    @Override
    public void close() {
        scanner.close();
    }
}
