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
    private static final String WELCOME = DIVIDER
            + " _  _              \n"
            + "| \\| |___ ___ __ _ \n"
            + "| .` / _ \\ V  V / _` |\n"
            + "|_|\\_\\___/\\_/\\_/\\__,_|\n\n"
            + "Hello! I'm Nova.\nWhat can I do for you?\n" + DIVIDER;
    private static final String GOODBYE = "Bye. Hope to see you again soon!\n" + DIVIDER;

    /**
     * Accepts an optional compiled-classes folder to test an earlier build too.
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
            System.out.println("All 5 console scenarios passed (exact transcripts and saved files).");
        } finally {
            deleteTestFolder(folder);
        }
    }

    private static void testTaskCommands(Path folder, String classes) throws Exception {
        String commands = "list\ntodo read book\ndeadline return book /by June 6th\n"
                + "event meeting /from 2pm /to 4pm\nmark 2\nunmark 2\ndelete 1\nlist\nbye\n"
                + "todo must not run\n";
        String expected = session(true,
                " Here are the tasks in your list:\n",
                " Got it. I've added this task:\n   [T][ ] read book\n Now you have 1 tasks in the list.\n",
                " Got it. I've added this task:\n   [D][ ] return book (by: June 6th)\n"
                        + " Now you have 2 tasks in the list.\n",
                " Got it. I've added this task:\n   [E][ ] meeting (from: 2pm to: 4pm)\n"
                        + " Now you have 3 tasks in the list.\n",
                " Nice! I've marked this task as done:\n   [D][X] return book (by: June 6th)\n",
                " OK, I've marked this task as not done yet:\n   [D][ ] return book (by: June 6th)\n",
                " Noted. I've removed this task:\n   [T][ ] read book\n Now you have 2 tasks in the list.\n",
                " Here are the tasks in your list:\n 1.[D][ ] return book (by: June 6th)\n"
                        + " 2.[E][ ] meeting (from: 2pm to: 4pm)\n");
        checkEquals(expected, runNova(folder, classes, commands), "Task command transcript");
        checkEquals("D|0|return book|June 6th\nE|0|meeting|2pm|4pm\n",
                Files.readString(folder.resolve("data/nova.txt")), "Saved tasks after deletion");
    }

    private static void testInvalidCommands(Path folder, String classes) throws Exception {
        String commands = "\nunknown\ntodo\ndeadline missing date\nevent missing times\n"
                + "mark\nunmark abc\ndelete\nmark 1\ntodo keep me\nmark 0\ndelete 2\nbye\n";
        String expected = session(true,
                " OOPS! Please enter a command.\n",
                " OOPS! I don't recognize that command. Try: list, todo, deadline, event, mark, unmark, or delete.\n",
                " OOPS! The todo description cannot be empty.\n",
                " OOPS! A deadline must follow this format: deadline <description> /by <date or time>.\n",
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

    private static void testEndOfInput(Path folder, String classes) throws Exception {
        checkEquals(session(false), runNova(folder, classes, ""), "Empty input exits cleanly");
        checkEquals(session(false, " Here are the tasks in your list:\n"),
                runNova(folder, classes, "list"), "A final line without a newline is handled");
        checkEquals(session(true), runNova(folder, classes, "bye"), "Exit without a newline");
        if (Files.exists(folder.resolve("data/nova.txt"))) {
            throw new AssertionError("Read-only sessions must not create a save file.");
        }
    }

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
     * Builds the expected transcript, including the extra divider printed for bye.
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
     */
    private static void checkEquals(String expected, String actual, String label) {
        if (!expected.replace("\r\n", "\n").equals(actual.replace("\r\n", "\n"))) {
            throw new AssertionError(label + "\nExpected:\n" + expected + "\nActual:\n" + actual);
        }
    }

    /**
     * Deletes only the temporary directory created by this test run, children first.
     */
    private static void deleteTestFolder(Path folder) throws IOException {
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
