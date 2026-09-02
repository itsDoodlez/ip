public class CommandHandler {
    private TaskList taskList;

    public CommandHandler(TaskList taskList) {
        this.taskList = taskList;
    }

    public void handleCommand(String command) {
        if (command.equals("list")) {
            listTasks();

        } else if (command.startsWith("mark ")) {
            markTask(command);

        } else if (command.startsWith("unmark ")) {
            unmarkTask(command);

        } else {
            addTask(command);
        }
    }

    private void listTasks() {
        taskList.listTasks();
    }

    private void markTask(String command) {
        int taskNumber = Integer.parseInt(command.substring(5));

        Task task = taskList.getTask(taskNumber);
        task.markAsDone();

        System.out.println(" Nice! I've marked this task as done:");
        System.out.println("   " + task);
    }

    private void unmarkTask(String command) {
        int taskNumber = Integer.parseInt(command.substring(7));

        Task task = taskList.getTask(taskNumber);
        task.markAsNotDone();

        System.out.println(" OK, I've marked this task as not done yet:");
        System.out.println("   " + task);
    }

    private void addTask(String command) {
        taskList.addTask(command);

        System.out.println(" added: " + command);
    }
}
