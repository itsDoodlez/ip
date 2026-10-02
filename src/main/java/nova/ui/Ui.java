package nova.ui;

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
    private static final String BANNER = " _  _              \n"
            + "| \\| |___ ___ __ _ \n"
            + "| .` / _ \\ V  V / _` |\n"
            + "|_|\\_\\___/\\_/\\_/\\__,_|\n";

    private final Scanner scanner;

    public Ui() {
        scanner = new Scanner(System.in);
    }

    public boolean hasNextCommand() {
        return scanner.hasNextLine();
    }

    public String readCommand() {
        return scanner.nextLine();
    }

    public void showDivider() {
        System.out.print(DIVIDER);
    }

    public void showWelcome() {
        showDivider();
        System.out.println(BANNER);
        System.out.println("Hello! I'm Nova.");
        System.out.println("What can I do for you?");
        showDivider();
    }

    public void showGoodbye() {
        System.out.println("Bye. Hope to see you again soon!");
        showDivider();
    }

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

    public void showTaskAdded(Task task, int taskCount) {
        System.out.println(" Got it. I've added this task:");
        System.out.println("   " + task);
        showTaskCount(taskCount);
    }

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

    private void showTaskCount(int taskCount) {
        System.out.println(" Now you have " + taskCount + " tasks in the list.");
    }

    @Override
    public void close() {
        scanner.close();
    }
}
