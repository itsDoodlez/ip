package nova.command;

import nova.exception.NovaException;
import nova.task.Deadline;
import nova.task.Event;
import nova.task.Task;
import nova.task.TaskList;
import nova.task.Todo;

/**
 * Parses commands entered by the user and applies them to the task list.
 */
public class CommandHandler {
    private static final String TODO_COMMAND = "todo ";
    private static final String DEADLINE_COMMAND = "deadline ";
    private static final String EVENT_COMMAND = "event ";
    private static final String MARK_COMMAND = "mark ";
    private static final String UNMARK_COMMAND = "unmark ";
    private static final String BY_SEPARATOR = " /by ";
    private static final String FROM_SEPARATOR = " /from ";
    private static final String TO_SEPARATOR = " /to ";

    private final TaskList taskList;

    public CommandHandler(TaskList taskList) {
        this.taskList = taskList;
    }

    /**
     * Handles one command entered by the user.
     *
     * @param command the command to handle
     * @throws NovaException if the command is invalid or cannot be completed
     */
    public void handleCommand(String command) throws NovaException {
        if (command == null || command.isBlank()) {
            throw new NovaException(" OOPS! Please enter a command.");
        }

        if (command.equals("list")) {
            listTasks();

        } else if (command.equals(MARK_COMMAND.trim()) || command.startsWith(MARK_COMMAND)) {
            markTask(command);

        } else if (command.equals(UNMARK_COMMAND.trim()) || command.startsWith(UNMARK_COMMAND)) {
            unmarkTask(command);

        } else if (command.equals(TODO_COMMAND.trim()) || command.startsWith(TODO_COMMAND)) {
            addTodo(command);

        } else if (command.equals(DEADLINE_COMMAND.trim()) || command.startsWith(DEADLINE_COMMAND)) {
            addDeadline(command);

        } else if (command.equals(EVENT_COMMAND.trim()) || command.startsWith(EVENT_COMMAND)) {
            addEvent(command);

        } else {
            throw new NovaException(
                    " OOPS! I don't recognize that command. Try: list, todo, deadline, event, mark, or unmark.");
        }
    }

    private void listTasks() {
        taskList.listTasks();
    }

    private void markTask(String command) throws NovaException {
        updateTaskStatus(command, MARK_COMMAND, true,
                " Nice! I've marked this task as done:");
    }

    private void unmarkTask(String command) throws NovaException {
        updateTaskStatus(command, UNMARK_COMMAND, false,
                " OK, I've marked this task as not done yet:");
    }

    /**
     * Changes a task's completion status and prints the corresponding feedback.
     *
     * @param command the complete mark or unmark command
     * @param commandPrefix the prefix used by the command
     * @param shouldBeDone whether the task should be marked as done
     * @param confirmationMessage the message to display after updating the task
     */
    private void updateTaskStatus(String command, String commandPrefix,
            boolean shouldBeDone, String confirmationMessage) throws NovaException {
        String taskNumberText = command.equals(commandPrefix.trim())
                ? ""
                : command.substring(commandPrefix.length()).trim();
        if (taskNumberText.isEmpty()) {
            throw new NovaException(
                    " OOPS! Please provide the number of the task to update.");
        }

        int taskNumber;
        try {
            taskNumber = Integer.parseInt(taskNumberText);
        } catch (NumberFormatException e) {
            throw new NovaException(
                    " OOPS! The task number must be a valid whole number.");
        }

        Task task = taskList.getTask(taskNumber);
        if (shouldBeDone) {
            task.markAsDone();
        } else {
            task.markAsNotDone();
        }

        System.out.println(confirmationMessage);
        System.out.println("   " + task);
    }

    private void addTodo(String command) throws NovaException {
        String description = command.equals(TODO_COMMAND.trim())
                ? ""
                : command.substring(TODO_COMMAND.length()).trim();
        validateText(description, "todo description");

        Todo todo = new Todo(description);
        taskList.addTask(todo);

        printAddedMessage(todo);
    }

    private void addDeadline(String command) throws NovaException {
        String content = command.equals(DEADLINE_COMMAND.trim())
                ? ""
                : command.substring(DEADLINE_COMMAND.length()).trim();

        int separatorIndex = content.indexOf(BY_SEPARATOR);
        if (separatorIndex < 0) {
            throw new NovaException(
                    " OOPS! A deadline must follow this format: deadline <description> /by <date or time>.");
        }

        String description = content.substring(0, separatorIndex).trim();
        String by = content.substring(separatorIndex + BY_SEPARATOR.length()).trim();
        validateText(description, "deadline description");
        validateText(by, "deadline date or time");

        Deadline deadline = new Deadline(description, by);
        taskList.addTask(deadline);

        printAddedMessage(deadline);
    }

    private void addEvent(String command) throws NovaException {
        String content = command.equals(EVENT_COMMAND.trim())
                ? ""
                : command.substring(EVENT_COMMAND.length()).trim();

        int fromIndex = content.indexOf(FROM_SEPARATOR);
        int toIndex = content.indexOf(TO_SEPARATOR);
        if (fromIndex < 0 || toIndex < 0 || toIndex < fromIndex) {
            throw new NovaException(
                    " OOPS! An event must follow this format: event <description> /from <start> /to <end>.");
        }

        String description = content.substring(0, fromIndex).trim();
        String from = content.substring(
                fromIndex + FROM_SEPARATOR.length(), toIndex).trim();
        String to = content.substring(toIndex + TO_SEPARATOR.length()).trim();
        validateText(description, "event description");
        validateText(from, "event start");
        validateText(to, "event end");

        Event event = new Event(description, from, to);
        taskList.addTask(event);

        printAddedMessage(event);
    }

    private void printAddedMessage(Task task) {
        System.out.println(" Got it. I've added this task:");
        System.out.println("   " + task);
        System.out.println(" Now you have " + taskList.getTaskCount()
                + " tasks in the list.");
    }

    /**
     * Rejects missing text in a command field with a specific user-facing error.
     *
     * @param value text supplied by the user
     * @param fieldName name of the field being checked
     * @throws NovaException if the field contains no text
     */
    private void validateText(String value, String fieldName) throws NovaException {
        if (value.isBlank()) {
            throw new NovaException(
                    " OOPS! The " + fieldName + " cannot be empty.");
        }
    }
}
