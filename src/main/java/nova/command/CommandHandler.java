package nova.command;

import nova.exception.NovaException;
import nova.storage.Storage;
import nova.task.Deadline;
import nova.task.Event;
import nova.task.Task;
import nova.task.TaskList;
import nova.task.Todo;
import nova.ui.Ui;

/**
 * Parses user commands and delegates additions to AddCommand.
 * Other task operations are still executed here during the gradual extraction.
 */
public class CommandHandler {
    private static final String TODO_COMMAND = "todo ";
    private static final String DEADLINE_COMMAND = "deadline ";
    private static final String EVENT_COMMAND = "event ";
    private static final String MARK_COMMAND = "mark ";
    private static final String UNMARK_COMMAND = "unmark ";
    private static final String DELETE_COMMAND = "delete ";
    private static final String BY_SEPARATOR = " /by ";
    private static final String FROM_SEPARATOR = " /from ";
    private static final String TO_SEPARATOR = " /to ";

    private final TaskList taskList;
    private final Storage storage;
    private final Ui ui;

    public CommandHandler(TaskList taskList, Storage storage, Ui ui) {
        this.taskList = taskList;
        this.storage = storage;
        this.ui = ui;
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
            ui.showTasks(taskList);
            return;

        } else if (command.equals(MARK_COMMAND.trim()) || command.startsWith(MARK_COMMAND)) {
            markTask(command);

        } else if (command.equals(UNMARK_COMMAND.trim()) || command.startsWith(UNMARK_COMMAND)) {
            unmarkTask(command);

        } else if (command.equals(DELETE_COMMAND.trim()) || command.startsWith(DELETE_COMMAND)) {
            deleteTask(command);

        } else if (command.equals(TODO_COMMAND.trim()) || command.startsWith(TODO_COMMAND)) {
            parseTodo(command).execute(taskList, ui, storage);
            return;

        } else if (command.equals(DEADLINE_COMMAND.trim()) || command.startsWith(DEADLINE_COMMAND)) {
            parseDeadline(command).execute(taskList, ui, storage);
            return;

        } else if (command.equals(EVENT_COMMAND.trim()) || command.startsWith(EVENT_COMMAND)) {
            parseEvent(command).execute(taskList, ui, storage);
            return;

        } else {
            throw new NovaException(
                    " OOPS! I don't recognize that command. Try: list, todo, deadline, event, mark, unmark, or delete.");
        }

        // AddCommand saves its own changes; only mark, unmark, and delete reach here.
        storage.save(taskList);
    }

    private void markTask(String command) throws NovaException {
        updateTaskStatus(command, MARK_COMMAND, true);
    }

    private void unmarkTask(String command) throws NovaException {
        updateTaskStatus(command, UNMARK_COMMAND, false);
    }

    /**
     * Removes the selected task and asks the UI to confirm the removal.
     *
     * @param command the complete delete command
     * @throws NovaException if the task number is missing, invalid, or out of range
     */
    private void deleteTask(String command) throws NovaException {
        int taskNumber = parseTaskNumber(command, DELETE_COMMAND, "delete");
        Task task = taskList.removeTask(taskNumber);

        ui.showTaskRemoved(task, taskList.getTaskCount());
    }

    /**
     * Changes a task's completion status and asks the UI to display feedback.
     *
     * @param command the complete mark or unmark command
     * @param commandPrefix the prefix used by the command
     * @param shouldBeDone whether the task should be marked as done
     */
    private void updateTaskStatus(String command, String commandPrefix,
            boolean shouldBeDone) throws NovaException {
        int taskNumber = parseTaskNumber(command, commandPrefix, "update");
        Task task = taskList.getTask(taskNumber);
        if (shouldBeDone) {
            task.markAsDone();
        } else {
            task.markAsNotDone();
        }

        ui.showTaskStatusChanged(task);
    }

    /**
     * Parses a task number shared by the mark, unmark, and delete commands.
     * Range validation is handled by the task list.
     *
     * @param command the complete command
     * @param commandPrefix the command name followed by a space
     * @param action action described in the missing-number error
     * @return the task number entered by the user
     * @throws NovaException if the number is missing or cannot be parsed as an integer
     */
    private int parseTaskNumber(String command, String commandPrefix, String action) throws NovaException {
        String taskNumberText = command.equals(commandPrefix.trim())
                ? ""
                : command.substring(commandPrefix.length()).trim();
        if (taskNumberText.isEmpty()) {
            throw new NovaException(
                    " OOPS! Please provide the number of the task to " + action + ".");
        }

        try {
            return Integer.parseInt(taskNumberText);
        } catch (NumberFormatException e) {
            throw new NovaException(
                    " OOPS! The task number must be a valid whole number.");
        }
    }

    /**
     * Validates a todo description and prepares its addition without executing it.
     */
    private Command parseTodo(String command) throws NovaException {
        String description = command.equals(TODO_COMMAND.trim())
                ? ""
                : command.substring(TODO_COMMAND.length()).trim();
        validateText(description, "todo description");

        return new AddCommand(new Todo(description));
    }

    /**
     * Parses the deadline's description and date into a command ready to execute.
     */
    private Command parseDeadline(String command) throws NovaException {
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

        return new AddCommand(new Deadline(description, by));
    }

    /**
     * Parses the event's description and time range into a command ready to execute.
     */
    private Command parseEvent(String command) throws NovaException {
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

        return new AddCommand(new Event(description, from, to));
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
