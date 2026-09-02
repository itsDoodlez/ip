public class TaskList {
    public static final int MAX_TASKS = 100;
    private Task[] tasks;
    private int taskCount;

    public TaskList() {
        tasks = new Task[MAX_TASKS];
        taskCount = 0;
    }

    public void addTask(String description) {
        tasks[taskCount] = new Task(description);
        taskCount++;
    }

    public void listTasks() {
        System.out.println(" Here are the tasks in your list:");

        for (int i = 0; i < taskCount; i++) {
            System.out.println(" " + (i + 1) + "." + tasks[i]);
        }
    }

    public Task getTask(int taskNumber) {
        return tasks[taskNumber - 1];
    }
}
