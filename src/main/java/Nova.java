import java.util.Scanner;

/**
 * Runs Nova's command-line user interface.
 */
public class Nova {

    private static final String DIVIDER = "____________________________________________________________\n";
    private static final String BANNER = " _  _              \n"
            + "| \\| |___ ___ __ _ \n"
            + "| .` / _ \\ V  V / _` |\n"
            + "|_|\\_\\___/\\_/\\_/\\__,_|\n";
    private static final String EXIT_COMMAND = "bye";

    public static void main(String[] args) {
        printWelcomeMessage();

        Scanner scanner = new Scanner(System.in);
        TaskList taskList = new TaskList();
        CommandHandler commandHandler = new CommandHandler(taskList);

        runCommandLoop(scanner, commandHandler);
        printGoodbyeMessage();

        scanner.close();
    }

    private static void printWelcomeMessage() {
        System.out.print(DIVIDER);
        System.out.println(BANNER);
        System.out.println("Hello! I'm Nova.");
        System.out.println("What can I do for you?");
        System.out.print(DIVIDER);
    }

    /**
     * Reads and handles commands until the user enters the exit command.
     *
     * @param scanner reads commands from the user
     * @param commandHandler processes each command
     */
    private static void runCommandLoop(Scanner scanner, CommandHandler commandHandler) {
        while (true) {
            String command = scanner.nextLine();

            System.out.print(DIVIDER);

            if (command.equals(EXIT_COMMAND)) {
                break;
            }

            commandHandler.handleCommand(command);

            System.out.print(DIVIDER);
        }
    }

    private static void printGoodbyeMessage() {
        System.out.println("Bye. Hope to see you again soon!");
        System.out.print(DIVIDER);
    }
}
