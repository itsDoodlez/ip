package nova;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/**
 * Checks complete console transcripts and saved files through Nova's entry point.
 * Each scenario uses its own temporary folder to protect the user's tasks.
 */
public class ConsoleTest {
    private static final String DIVIDER = "____________________________________________________________\n";
    /** Shared expected greeting, kept independent of the production UI for transcript checks. */
    static final String WELCOME = DIVIDER
            + ",---.   .--.    ,-----.    ,---.  ,---.   ____     \n"
            + "|    \\  |  |  .'  .-,  '.  |   /  |   | .'  __ `.  \n"
            + "|  ,  \\ |  | / ,-.|  \\ _ \\ |  |   |  .'/   '  \\  \\ \n"
            + "|  |\\_ \\|  |;  \\  '_ /  | :|  | _ |  | |___|  /  | \n"
            + "|  _( )_\\  ||  _`,/ \\ _/  ||  _( )_  |    _.-`   | \n"
            + "| (_ o _)  |: (  '\\_/ \\   ;\\ (_ o._) /  .'   _    |\n"
            + "|  (_,_)\\  | \\ `\"/  \\  ) /  \\ (_,_) /   |  _( )_  |\n"
            + "|  |    |  |  '. \\_/``\".'    \\     /    \\ (_ o _) /\n"
            + "'--'    '--'    '-----'       `---`      '.(_,_).' \n\n"
            + "Hello! I'm Nova.\nWhat can I do for you?\n" + DIVIDER;
    private static final String GOODBYE = "Bye. Hope to see you again soon!\n" + DIVIDER;

    /**
     * Accepts an optional compiled-classes folder to test an earlier build too.
     *
     * @param args optional path to the compiled classes; defaults to the current classpath
     * @throws Exception if test setup, execution, or cleanup fails
     */
    public static void main(String[] args) throws Exception {
        String classes = Path.of(args.length == 0 ? System.getProperty("java.class.path") : args[0])
                .toAbsolutePath().toString();
        Path folder = Files.createTempDirectory("nova-console-test-");
        try {
            testTaskCommands(folder.resolve("commands"), classes);
            testInvalidCommands(folder.resolve("invalid"), classes);
            testRestart(folder.resolve("restart"), classes);
            testEndOfInput(folder.resolve("eof"), classes);
            testCorruptSave(folder.resolve("corrupt"), classes);
            testDeadlineDates(folder.resolve("deadline-dates"), classes);
            testInvalidSavedDates(folder.resolve("invalid-saved-dates"), classes);
            testFindTasks(folder.resolve("find"), classes);
            testEmptyFind(folder.resolve("empty-find"), classes);
            testMissingEventStart(folder.resolve("missing-event-start"), classes);
            System.out.println("All 10 console scenarios passed (exact transcripts and saved files).");
        } finally {
            deleteTestFolder(folder);
        }
    }

    /**
     * Checks task changes, list output, and saved data through a complete console session.
     */
    private static void testTaskCommands(Path folder, String classes) throws Exception {
        String commands = "list\ntodo read book\ndeadline return book /by 2019-10-15\n"
                + "event meeting /from 2pm /to 4pm\nmark 2\nunmark 2\ndelete 1\nlist\nbye\n"
                + "todo must not run\n";
        String expected = session(true,
                " Here are the tasks in your list:\n",
                " Got it. I've added this task:\n   [T][ ] read book\n Now you have 1 tasks in the list.\n",
                " Got it. I've added this task:\n   [D][ ] return book (by: Oct 15 2019)\n"
                        + " Now you have 2 tasks in the list.\n",
                " Got it. I've added this task:\n   [E][ ] meeting (from: 2pm to: 4pm)\n"
                        + " Now you have 3 tasks in the list.\n",
                " Nice! I've marked this task as done:\n   [D][X] return book (by: Oct 15 2019)\n",
                " OK, I've marked this task as not done yet:\n   [D][ ] return book (by: Oct 15 2019)\n",
                " Noted. I've removed this task:\n   [T][ ] read book\n Now you have 2 tasks in the list.\n",
                " Here are the tasks in your list:\n 1.[D][ ] return book (by: Oct 15 2019)\n"
                        + " 2.[E][ ] meeting (from: 2pm to: 4pm)\n");
        checkEquals(expected, runNova(folder, classes, commands), "Task command transcript");
        checkEquals("D|0|return book|2019-10-15\nE|0|meeting|2pm|4pm\n",
                Files.readString(folder.resolve("data/nova.txt")), "Saved tasks after deletion");
    }

    /**
     * Checks error messages for invalid commands and verifies that saved tasks remain intact.
     */
    private static void testInvalidCommands(Path folder, String classes) throws Exception {
        String commands = "\nunknown\ntodo\ndeadline missing date\nevent missing times\n"
                + "mark\nunmark abc\ndelete\nmark 1\ntodo keep me\nmark 0\ndelete 2\nbye\n";
        String expected = session(true,
                " OOPS! Please enter a command.\n",
                " OOPS! I don't recognize that command. "
                        + "Try: list, find, todo, deadline, event, mark, unmark, or delete.\n",
                " OOPS! The todo description cannot be empty.\n",
                " OOPS! A deadline must follow this format: deadline <description> /by yyyy-MM-dd.\n",
                " OOPS! An event must follow this format: event <description> /from <start> /to <end>.\n",
                " OOPS! Please provide the number of the task to update.\n",
                " OOPS! The task number must be a valid whole number.\n",
                " OOPS! Please provide the number of the task to delete.\n",
                " OOPS! Your task list is empty.\n",
                " Got it. I've added this task:\n   [T][ ] keep me\n Now you have 1 tasks in the list.\n",
                " OOPS! There is no task numbered 0. Please choose a number from 1 to 1.\n",
                " OOPS! There is no task numbered 2. Please choose a number from 1 to 1.\n");
        checkEquals(expected, runNova(folder, classes, commands), "Invalid command transcript");
        checkEquals("T|0|keep me\n", Files.readString(folder.resolve("data/nova.txt")),
                "Invalid commands must preserve the saved task");
    }

    /**
     * Checks that completed tasks reload and deleting the final task leaves an empty save.
     */
    private static void testRestart(Path folder, String classes) throws Exception {
        runNova(folder, classes, "todo remember me\nmark 1\nbye\n");
        checkEquals(session(true, " Here are the tasks in your list:\n 1.[T][X] remember me\n"),
                runNova(folder, classes, "list\nbye\n"), "Reloaded completed task");
        checkEquals(session(true,
                " Noted. I've removed this task:\n   [T][X] remember me\n Now you have 0 tasks in the list.\n",
                " Here are the tasks in your list:\n"),
                runNova(folder, classes, "delete 1\nlist\nbye\n"), "Deleting the last task");
        checkEquals("", Files.readString(folder.resolve("data/nova.txt")), "Empty saved list");
    }

    /**
     * Checks clean shutdown at end-of-input, including a final line without a newline.
     */
    private static void testEndOfInput(Path folder, String classes) throws Exception {
        checkEquals(session(false), runNova(folder, classes, ""), "Empty input exits cleanly");
        checkEquals(session(false, " Here are the tasks in your list:\n"),
                runNova(folder, classes, "list"), "A final line without a newline is handled");
        checkEquals(session(true), runNova(folder, classes, "bye"), "Exit without a newline");
        if (Files.exists(folder.resolve("data/nova.txt"))) {
            throw new AssertionError("Read-only sessions must not create a save file.");
        }
    }

    /**
     * Checks that corrupt saves stop startup with a useful error and remain unchanged.
     */
    private static void testCorruptSave(Path folder, String classes) throws Exception {
        Path file = folder.resolve("data/nova.txt");
        Files.createDirectories(file.getParent());
        String corrupt = "T|0|valid\nD|0|missing date\n";
        Files.writeString(file, corrupt);
        String expected = WELCOME + " OOPS! Could not load saved task in " + Path.of("data", "nova.txt")
                + " at line 2: Expected 4 fields for task type D.\n"
                + " Please fix the saved file or its permissions and restart Nova.\n"
                + " Your file was not changed.\n" + DIVIDER;
        checkEquals(expected, runNova(folder, classes, "todo must not overwrite\nbye\n"),
                "Loading error transcript");
        checkEquals(corrupt, Files.readString(file), "Corrupt data must stay intact");
    }

    /**
     * Rejects invalid input without creating a save, then checks a valid leap date across a restart.
     */
    private static void testDeadlineDates(Path folder, String classes) throws Exception {
        String error = " OOPS! Please enter a valid deadline date in yyyy-MM-dd format (e.g., 2019-10-15).\n";
        checkEquals(session(true, error, error, error, " Here are the tasks in your list:\n"),
                runNova(folder, classes, "deadline bad date /by 2019-02-29\n"
                        + "deadline missing year /by June 6th\ndeadline timed /by 2/12/2019 1800\nlist\nbye\n"),
                "Invalid deadline dates must produce helpful errors");
        if (Files.exists(folder.resolve("data/nova.txt"))) {
            throw new AssertionError("Invalid date commands must not create a save file.");
        }
        checkEquals(session(true,
                " Got it. I've added this task:\n   [D][ ] leap day (by: Feb 29 2024)\n"
                        + " Now you have 1 tasks in the list.\n",
                " Nice! I've marked this task as done:\n   [D][X] leap day (by: Feb 29 2024)\n"),
                runNova(folder, classes, "deadline leap day /by 2024-02-29\nmark 1\nbye\n"),
                "Valid leap date transcript");
        checkEquals("D|1|leap day|2024-02-29\n", Files.readString(folder.resolve("data/nova.txt")),
                "Save the ISO date and completion status");
        checkEquals(session(true, " Here are the tasks in your list:\n 1.[D][X] leap day (by: Feb 29 2024)\n"),
                runNova(folder, classes, "list\nbye\n"), "Reload the formatted date and completion status");
    }

    /**
     * Reports the line containing an impossible or legacy date and preserves the entire save.
     */
    private static void testInvalidSavedDates(Path folder, String classes) throws Exception {
        Path file = folder.resolve("data/nova.txt");
        Files.createDirectories(file.getParent());
        String expected = WELCOME + " OOPS! Could not load saved task in " + Path.of("data", "nova.txt")
                + " at line 2: Deadline date must be a valid date in yyyy-MM-dd format (e.g., 2019-10-15).\n"
                + " Please fix the saved file or its permissions and restart Nova.\n"
                + " Your file was not changed.\n" + DIVIDER;
        for (String date : new String[] {"2019-02-29", "June 6th"}) {
            String contents = "T|0|keep me\nD|0|return book|" + date + "\n";
            Files.writeString(file, contents);
            checkEquals(expected, runNova(folder, classes, "todo must not overwrite\nbye\n"),
                    "Invalid saved date transcript");
            checkEquals(contents, Files.readString(file), "Invalid saved dates must stay intact");
        }
    }

    /**
     * Checks search scope, matching rules, result numbering, and preservation of loaded tasks.
     */
    private static void testFindTasks(Path folder, String classes) throws Exception {
        Path file = folder.resolve("data/nova.txt");
        Files.createDirectories(file.getParent());
        String saved = "T|0|buy milk\nT|1|read book\nD|1|return book|2019-10-15\n"
                + "E|0|book club|2pm|4pm\nT|0|notebook\nT|0|Read Book\n"
                + "E|0|meeting|book fair|4pm\nT|0|read book\n";
        Files.writeString(file, saved);
        String noMatches = " Here are the matching tasks in your list:\n No matching tasks found.\n";
        String expected = session(true,
                " Here are the matching tasks in your list:\n"
                        + " 1.[T][X] read book\n 2.[D][X] return book (by: Oct 15 2019)\n"
                        + " 3.[E][ ] book club (from: 2pm to: 4pm)\n 4.[T][ ] notebook\n"
                        + " 5.[T][ ] read book\n",
                " Here are the matching tasks in your list:\n 1.[T][ ] Read Book\n",
                " Here are the matching tasks in your list:\n 1.[D][X] return book (by: Oct 15 2019)\n",
                noMatches, noMatches, noMatches, noMatches,
                " Here are the tasks in your list:\n 1.[T][ ] buy milk\n 2.[T][X] read book\n"
                        + " 3.[D][X] return book (by: Oct 15 2019)\n"
                        + " 4.[E][ ] book club (from: 2pm to: 4pm)\n 5.[T][ ] notebook\n"
                        + " 6.[T][ ] Read Book\n 7.[E][ ] meeting (from: book fair to: 4pm)\n"
                        + " 8.[T][ ] read book\n");
        checkEquals(expected, runNova(folder, classes,
                "find book\nfind Book\nfind   return book   \nfind Oct\nfind 2pm\nfind [X]\n"
                        + "find absent\nlist\nbye\n"), "Search results and unchanged full list");
        checkEquals(saved, Files.readString(file), "Searching must preserve saved tasks");
    }

    /**
     * Checks empty lists and missing keywords without creating task data.
     */
    private static void testEmptyFind(Path folder, String classes) throws Exception {
        String missingKeyword = " OOPS! The search keyword cannot be empty.\n";
        checkEquals(session(true,
                " Here are the matching tasks in your list:\n No matching tasks found.\n",
                missingKeyword, missingKeyword,
                " OOPS! I don't recognize that command. "
                        + "Try: list, find, todo, deadline, event, mark, unmark, or delete.\n"),
                runNova(folder, classes, "find book\nfind\nfind    \nfindbook\nbye\n"),
                "Empty searches and missing keywords");
        if (Files.exists(folder.resolve("data/nova.txt"))) {
            throw new AssertionError("Searching must not create a save file.");
        }
    }

    /**
     * Checks adjacent event separators without a start value and recovery on the next command.
     */
    private static void testMissingEventStart(Path folder, String classes) throws Exception {
        String error = " OOPS! The event start cannot be empty.\n";
        checkEquals(session(true, error, error,
                " Got it. I've added this task:\n   [T][ ] still running\n Now you have 1 tasks in the list.\n",
                " Here are the tasks in your list:\n 1.[T][ ] still running\n"),
                runNova(folder, classes, "event meeting /from /to 4pm\n"
                        + "event meeting /from  /to 4pm\ntodo still running\nlist\nbye\n"),
                "An empty event start must not crash the command loop");
        checkEquals("T|0|still running\n", Files.readString(folder.resolve("data/nova.txt")),
                "Rejected events must not be saved");
    }

    /**
     * Builds the expected transcript, including the extra divider printed for bye.
     *
     * @param endsWithBye whether the session ends with an explicit exit command
     * @param responses command responses without surrounding dividers
     * @return the full expected console transcript
     */
    private static String session(boolean endsWithBye, String... responses) {
        StringBuilder expected = new StringBuilder(WELCOME);
        for (String response : responses) {
            expected.append(DIVIDER).append(response).append(DIVIDER);
        }
        if (endsWithBye) {
            expected.append(DIVIDER);
        }
        return expected.append(GOODBYE).toString();
    }

    /**
     * Runs the real CLI in an isolated directory using the same Java runtime as the test.
     * Redirecting output to a file prevents a full output pipe from blocking the child.
     *
     * @param folder the temporary working directory for the child process
     * @param classes the absolute path to the compiled classes
     * @param commands input lines sent to Nova
     * @return the captured console output
     * @throws Exception if process startup, communication, or cleanup fails
     * @throws AssertionError if Nova times out or exits with an error
     */
    private static String runNova(Path folder, String classes, String commands) throws Exception {
        Files.createDirectories(folder);
        Path outputFile = folder.resolve("console.txt");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-cp", classes, "nova.Nova")
                .directory(folder.toFile()).redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        try {
            try (var input = process.getOutputStream()) {
                input.write(commands.getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Nova did not exit within 10 seconds.");
            }
            String output = Files.readString(outputFile);
            if (process.exitValue() != 0) {
                throw new AssertionError("Nova exited with an error:\n" + output);
            }
            return output;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
        }
    }

    /**
     * Ignores only platform line-ending differences; spaces and blank lines must match.
     *
     * @param expected the required text
     * @param actual the text produced by the application or save file
     * @param label description of the check included in failure messages
     * @throws AssertionError if the texts differ after normalizing line endings
     */
    private static void checkEquals(String expected, String actual, String label) {
        if (!expected.replace("\r\n", "\n").equals(actual.replace("\r\n", "\n"))) {
            throw new AssertionError(label + "\nExpected:\n" + expected + "\nActual:\n" + actual);
        }
    }

    /**
     * Deletes only the temporary directory created by this test run, children first.
     *
     * @param folder the temporary test directory to remove
     * @throws IOException if the directory cannot be traversed or an entry cannot be deleted
     */
    private static void deleteTestFolder(Path folder) throws IOException {
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
