import java.util.Scanner;

public class Nova {
    public static void main(String[] args) {
        String divider = "____________________________________________________________\n";

        String banner = " _  _              \n"
                + "| \\| |___ ___ __ _ \n"
                + "| .` / _ \\ V  V / _` |\n"
                + "|_|\\_\\___/\\_/\\_/\\__,_|\n";

        System.out.print(divider);
        System.out.println(banner);
        System.out.println("Hello! I'm Nova.");
        System.out.println("What can I do for you?");
        System.out.print(divider);

        Scanner scanner = new Scanner(System.in);

        TaskList taskList = new TaskList();
        CommandHandler commandHandler = new CommandHandler(taskList);

        while (true) {
            String command = scanner.nextLine();

            System.out.print(divider);

            if (command.equals("bye")) {
                break;
            }

            commandHandler.handleCommand(command);

            System.out.print(divider);
        }

        System.out.println("Bye. Hope to see you again soon!");
        System.out.print(divider);

        scanner.close();
    }
}
