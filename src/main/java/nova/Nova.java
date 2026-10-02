package nova;

import java.nio.file.Path;

import nova.command.CommandHandler;
import nova.exception.NovaException;
import nova.storage.Storage;
import nova.task.TaskList;
import nova.ui.Ui;

/**
 * Coordinates startup, user interaction, and command handling for Nova.
 */
public class Nova {

    private static final String EXIT_COMMAND = "bye";

    public static void main(String[] args) {
        try (Ui ui = new Ui()) {
            ui.showWelcome();

            Storage storage = new Storage(Path.of("data", "nova.txt"));
            TaskList taskList;
            try {
                taskList = storage.load();
            } catch (NovaException e) {
                ui.showLoadingError(e.getMessage());
                return;
            }

            CommandHandler commandHandler = new CommandHandler(taskList, storage, ui);
            runCommandLoop(ui, commandHandler);
            ui.showGoodbye();
        }
    }

    /**
     * Reads and handles commands until the user enters the exit command.
     *
     * @param ui reads commands and displays feedback
     * @param commandHandler processes each command
     */
    private static void runCommandLoop(Ui ui, CommandHandler commandHandler) {
        while (ui.hasNextCommand()) {
            String command = ui.readCommand();

            ui.showDivider();

            if (command.equals(EXIT_COMMAND)) {
                break;
            }

            try {
                commandHandler.handleCommand(command);
            } catch (NovaException e) {
                ui.showError(e.getMessage());
            }

            ui.showDivider();
        }
    }
}
