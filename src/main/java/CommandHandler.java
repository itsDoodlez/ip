public class CommandHandler {
    private static final String TODO_COMMAND = "todo ";
    private static final String DEADLINE_COMMAND = "deadline ";
    private static final String EVENT_COMMAND = "event ";
    private static final String MARK_COMMAND = "mark ";
    private static final String UNMARK_COMMAND = "unmark ";
    private static final String BY_SEPARATOR = " /by ";
    private static final String FROM_SEPARATOR = " /from ";
    private static final String TO_SEPARATOR = " /to ";

    private TaskList taskList;

    public CommandHandler(TaskList taskList) {
        this.taskList = taskList;
    }

    public void handleCommand(String command) {
        if (command.equals("list")) {
            listTasks();

        } else if (command.startsWith(MARK_COMMAND)) {
            markTask(command);

        } else if (command.startsWith(UNMARK_COMMAND)) {
            unmarkTask(command);

        } else if (command.startsWith(TODO_COMMAND)) {
            addTodo(command);

        } else if (command.startsWith(DEADLINE_COMMAND)) {
            addDeadline(command);

        } else if (command.startsWith(EVENT_COMMAND)) {
            addEvent(command);

        } else {
            System.out.println(" Sorry, I don't understand that command.");
        }
    }

    private void listTasks() {
        taskList.listTasks();
    }

    private void markTask(String command) {
        updateTaskStatus(command, MARK_COMMAND, true,
                " Nice! I've marked this task as done:");
    }

    private void unmarkTask(String command) {
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
            boolean shouldBeDone, String confirmationMessage) {
        int taskNumber = Integer.parseInt(
                command.substring(commandPrefix.length()));

        Task task = taskList.getTask(taskNumber);
        if (shouldBeDone) {
            task.markAsDone();
        } else {
            task.markAsNotDone();
        }

        System.out.println(confirmationMessage);
        System.out.println("   " + task);
    }

    private void addTodo(String command) {
        String description = command.substring(TODO_COMMAND.length());

        Todo todo = new Todo(description);
        taskList.addTask(todo);

        printAddedMessage(todo);
    }

    private void addDeadline(String command) {
        String content = command.substring(DEADLINE_COMMAND.length());

        int separatorIndex = content.indexOf(BY_SEPARATOR);

        String description = content.substring(0, separatorIndex);
        String by = content.substring(separatorIndex + BY_SEPARATOR.length());

        Deadline deadline = new Deadline(description, by);
        taskList.addTask(deadline);

        printAddedMessage(deadline);
    }

    private void addEvent(String command) {
        String content = command.substring(EVENT_COMMAND.length());

        int fromIndex = content.indexOf(FROM_SEPARATOR);
        int toIndex = content.indexOf(TO_SEPARATOR);

        String description = content.substring(0, fromIndex);
        String from = content.substring(
                fromIndex + FROM_SEPARATOR.length(),
                toIndex);
        String to = content.substring(toIndex + TO_SEPARATOR.length());

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
}
