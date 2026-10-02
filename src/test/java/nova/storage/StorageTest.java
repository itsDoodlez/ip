package nova.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import nova.command.AddCommand;
import nova.command.Command;
import nova.command.CommandHandler;
import nova.exception.NovaException;
import nova.task.Deadline;
import nova.task.Event;
import nova.task.Task;
import nova.task.TaskList;
import nova.task.Todo;
import nova.ui.Ui;

/**
 * Runs persistence regression tests with Java alone, using isolated temporary folders.
 */
public class StorageTest {
    public static void main(String[] args) throws Exception {
        Path testFolder = Files.createTempDirectory("nova-storage-test-");
        try (Ui ui = new Ui()) {
            testDeadlineDisplay();
            testMissingAndEmptyFiles(testFolder.resolve("missing"));
            testRoundTrip(testFolder.resolve("round-trip"));
            testCommands(testFolder.resolve("commands"), ui);
            testPreparedAddCommands(testFolder.resolve("prepared-additions"), ui);
            testDeadlineDates(testFolder.resolve("deadline-dates"), ui);
            testCorruptFiles(testFolder.resolve("corrupt"));
            testDynamicCapacity(testFolder.resolve("capacity"), ui);
            testIoErrors(testFolder.resolve("errors"), ui);
            testRestart(testFolder.resolve("restart"));
            System.out.println("All storage tests passed.");
        } finally {
            deleteTestFolder(testFolder);
        }
    }

    private static void testMissingAndEmptyFiles(Path folder) throws Exception {
        Path file = folder.resolve("data").resolve("nova.txt");
        Storage storage = new Storage(file);
        check(storage.load().getTaskCount() == 0, "A missing folder should load an empty list.");
        Files.createDirectories(file.getParent());
        check(storage.load().getTaskCount() == 0, "A missing file should load an empty list.");
        Files.writeString(file, "", StandardCharsets.UTF_8);
        check(storage.load().getTaskCount() == 0, "An empty file should load an empty list.");
    }

    private static void testRoundTrip(Path folder) throws Exception {
        Storage storage = new Storage(folder.resolve("data").resolve("nova.txt"));
        TaskList original = new TaskList();
        original.addTask(new Todo("read | book\\notes\n中文\rnext line"));
        original.addTask(new Deadline("return | book\\notes", LocalDate.of(2019, 10, 15)));
        original.addTask(new Event("meeting", "Aug 6th 2pm\\start", "Aug 6th 4pm | end"));
        original.getTask(1).markAsDone();
        original.getTask(3).markAsDone();
        storage.save(original);
        checkSameTasks(original, storage.load());
        storage.save(new TaskList());
        check(storage.load().getTaskCount() == 0, "Saving an empty list must remove old tasks.");
    }

    private static void testCommands(Path folder, Ui ui) throws Exception {
        Path file = folder.resolve("nova.txt");
        CountingStorage storage = new CountingStorage(file);
        TaskList tasks = new TaskList();
        CommandHandler handler = new CommandHandler(tasks, storage, ui);
        List<String> changes = List.of("todo read book", "deadline return book /by 2019-10-15",
                "event meeting /from 2pm /to 4pm", "mark 1", "unmark 1", "mark 2", "mark 3", "delete 1");
        for (String command : changes) {
            int previousSaveCalls = storage.saveCalls;
            handler.handleCommand(command);
            check(storage.saveCalls == previousSaveCalls + 1,
                    "Each modifying command must save exactly once: " + command);
            checkSameTasks(tasks, storage.load());
        }

        String saved = Files.readString(file, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.fromMillis(1000));
        FileTime timestamp = Files.getLastModifiedTime(file);
        handler.handleCommand("list");
        handler.handleCommand("find book");
        handler.handleCommand("find absent");
        for (String command : List.of("todo", "mark 4", "unmark 0", "deadline missing date", "unknown",
                "find", "find    ")) {
            try {
                handler.handleCommand(command);
                throw new AssertionError("Expected an invalid command error: " + command);
            } catch (NovaException e) {
                check(saved.equals(Files.readString(file, StandardCharsets.UTF_8)),
                        "Invalid commands must not change saved data.");
            }
        }
        check(timestamp.equals(Files.getLastModifiedTime(file)),
                "Listing, searching, and invalid commands must not rewrite the save file.");
        check(storage.saveCalls == changes.size(), "Listing, searching, and invalid commands must not attempt a save.");
    }

    /**
     * Exercises additions through the abstract command API before a parser exists.
     */
    private static void testPreparedAddCommands(Path folder, Ui ui) throws Exception {
        CountingStorage storage = new CountingStorage(folder.resolve("nova.txt"));
        TaskList tasks = new TaskList();
        List<Task> additions = List.of(new Todo("read book"), new Deadline("return book", LocalDate.of(2019, 10, 15)),
                new Event("meeting", "2pm", "4pm"));
        for (Task task : additions) {
            int previousCount = tasks.getTaskCount();
            Command command = new AddCommand(task);
            check(!command.isExit(), "Adding a task must keep Nova running.");
            check(tasks.getTaskCount() == previousCount && storage.saveCalls == previousCount,
                    "Preparing a command must not add or save a task.");
            command.execute(tasks, ui, storage);
            check(tasks.getTaskCount() == previousCount + 1, "Execution must add exactly one task.");
            check(tasks.getTask(previousCount + 1) == task, "The command must add its prepared task.");
            check(storage.saveCalls == previousCount + 1, "Execution must save exactly once.");
            checkSameTasks(tasks, storage.load());
        }
    }

    /**
     * Checks real calendar validation, typed dates, stable display, and ISO persistence.
     */
    private static void testDeadlineDates(Path folder, Ui ui) throws Exception {
        Path file = folder.resolve("nova.txt");
        CountingStorage storage = new CountingStorage(file);
        TaskList tasks = new TaskList();
        CommandHandler handler = new CommandHandler(tasks, storage, ui);
        List<LocalDate> dates = List.of(LocalDate.of(2019, 10, 15), LocalDate.of(2000, 2, 29),
                LocalDate.of(2024, 2, 29));
        for (LocalDate date : dates) {
            handler.handleCommand("deadline return book /by " + date);
            Deadline deadline = (Deadline) tasks.getTask(tasks.getTaskCount());
            check(date.equals(deadline.getBy()), "The task must hold a LocalDate matching the input.");
            Deadline reloaded = (Deadline) storage.load().getTask(tasks.getTaskCount());
            check(date.equals(reloaded.getBy()), "Reloading must preserve the typed date.");
        }
        check(Files.readAllLines(file).equals(List.of("D|0|return book|2019-10-15",
                "D|0|return book|2000-02-29", "D|0|return book|2024-02-29")),
                "Storage must use ISO dates rather than the display format.");

        String saved = Files.readString(file);
        for (String invalid : List.of("2019-02-29", "1900-02-29", "2019-04-31", "2019-13-01",
                "2019-00-10", "2019-10-00", "2019-2-03", "15/10/2019", "June 6th", "2019-10-15 1800")) {
            try {
                handler.handleCommand("deadline invalid /by " + invalid);
                throw new AssertionError("Expected an invalid deadline date error: " + invalid);
            } catch (NovaException e) {
                check(e.getMessage().contains("yyyy-MM-dd"), "Explain the expected deadline date format.");
                check(tasks.getTaskCount() == dates.size(), "Invalid dates must not add tasks.");
                check(storage.saveCalls == dates.size(), "Invalid dates must not attempt to save.");
                check(saved.equals(Files.readString(file)), "Invalid dates must leave saved data intact.");
            }
        }
    }

    /**
     * Initializes the deadline formatter under a non-English locale to check stable month names.
     */
    private static void testDeadlineDisplay() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);
            Deadline deadline = new Deadline("return book", LocalDate.of(2019, 10, 15));
            check(deadline.toString().equals("[D][ ] return book (by: Oct 15 2019)"),
                    "Display English month names even when the default locale is different.");
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private static void testCorruptFiles(Path folder) throws Exception {
        Files.createDirectories(folder);
        Path file = folder.resolve("nova.txt");
        Storage storage = new Storage(file);
        List<String> invalidLines = List.of("", "X|0|unknown", "T|2|bad status", "T|0",
                "T|0|", "T|0|extra|field", "D|0|missing date", "D|0|blank date| ",
                "D|0|invalid date|2019-02-29", "D|0|legacy date|June 6th",
                "E|0|missing end|2pm", "E|0|blank end|2pm|", "T|0|bad\\q", "T|0|trailing\\");
        for (String invalidLine : invalidLines) {
            String contents = "T|1|valid task\n" + invalidLine + "\n";
            Files.writeString(file, contents, StandardCharsets.UTF_8);
            checkLoadRejected(storage, "line 2");
            check(contents.equals(Files.readString(file, StandardCharsets.UTF_8)),
                    "A corrupt file must remain unchanged.");
        }
        Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28});
        checkLoadRejected(storage, "Could not read");
    }

    /**
     * Verifies that saving and loading preserve a list beyond the former 100-task limit.
     */
    private static void testDynamicCapacity(Path folder, Ui ui) throws Exception {
        Path file = folder.resolve("nova.txt");
        Storage storage = new Storage(file);
        TaskList tasks = new TaskList();
        for (int i = 1; i <= 100; i++) {
            tasks.addTask(new Todo("task " + i));
        }
        storage.save(tasks);
        TaskList loaded = storage.load();
        checkSameTasks(tasks, loaded);
        CommandHandler handler = new CommandHandler(loaded, storage, ui);
        handler.handleCommand("mark 100");
        check(storage.load().getTask(100).isDone(), "The last task must still support saving updates.");
        handler.handleCommand("todo task 101");
        check(loaded.getTaskCount() == 101, "The task list must grow beyond 100 tasks.");
        checkSameTasks(loaded, storage.load());
        handler.handleCommand("mark 101");
        check(storage.load().getTask(101).isDone(), "Tasks beyond 100 must support saving updates.");
    }

    private static void testIoErrors(Path folder, Ui ui) throws Exception {
        Files.createDirectories(folder);
        Path blockedFolder = folder.resolve("blocked");
        Files.writeString(blockedFolder, "keep this file", StandardCharsets.UTF_8);
        Storage storage = new Storage(blockedFolder.resolve("nova.txt"));
        TaskList tasks = new TaskList();
        CommandHandler handler = new CommandHandler(tasks, storage, ui);
        try {
            handler.handleCommand("todo still in memory");
            throw new AssertionError("Expected a save error when the parent is a regular file.");
        } catch (NovaException e) {
            check(e.getMessage().contains("could not save"), "Saving errors should explain the failure.");
            check(tasks.getTaskCount() == 1, "A failed save should retain the change in memory.");
            check(Files.readString(blockedFolder).equals("keep this file"), "Keep the blocking file intact.");
        }
        // Remove only the test's blocking file to check recovery on the next change.
        Files.delete(blockedFolder);
        handler.handleCommand("mark 1");
        checkSameTasks(tasks, storage.load());

        checkLoadRejected(new Storage(folder), "Could not read");
        Path blockedFile = folder.resolve("nova.txt");
        Files.createDirectory(blockedFile);
        Files.writeString(blockedFile.resolve("keep.txt"), "preserve", StandardCharsets.UTF_8);
        try {
            new Storage(blockedFile).save(tasks);
            throw new AssertionError("Expected an error replacing a nonempty directory.");
        } catch (NovaException e) {
            check(Files.readString(blockedFile.resolve("keep.txt")).equals("preserve"),
                    "A failed replacement must preserve the existing contents.");
            try (var files = Files.list(folder)) {
                check(files.noneMatch(path -> path.toString().endsWith(".tmp")),
                        "A failed save should clean up its temporary file.");
            }
        }
    }

    /**
     * Starts separate chatbot processes to verify the packaged entry point and relative path.
     */
    private static void testRestart(Path folder) throws Exception {
        Files.createDirectories(folder);
        runNova(folder, "todo read book\ndeadline return book /by 2019-10-15\n"
                + "event meeting /from 2pm /to 4pm\nmark 2\nbye\n");
        String output = runNova(folder, "list\nbye\n");
        check(output.contains("1.[T][ ] read book"), "Reload the todo on startup.");
        check(output.contains("2.[D][X] return book (by: Oct 15 2019)"), "Reload the completed deadline.");
        check(output.contains("3.[E][ ] meeting (from: 2pm to: 4pm)"), "Reload both event times.");
        runNova(folder, "unmark 2\nbye\n");
        output = runNova(folder, "list\nbye\n");
        check(output.contains("2.[D][ ] return book"), "Unmarked tasks must stay unmarked after a restart.");

        Path file = folder.resolve("data").resolve("nova.txt");
        String corrupt = "T|0|valid\nD|0|missing date\n";
        Files.writeString(file, corrupt, StandardCharsets.UTF_8);
        output = runNova(folder, "todo must not overwrite\nbye\n");
        check(output.contains("line 2"), "Startup should explain corruption without crashing.");
        check(corrupt.equals(Files.readString(file)), "Startup must protect corrupt data from overwrites.");
    }

    private static String runNova(Path folder, String commands) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = Path.of(System.getProperty("java.class.path")).toAbsolutePath().toString();
        Process process = new ProcessBuilder(java, "-cp", classes, "nova.Nova")
                .directory(folder.toFile()).redirectErrorStream(true).start();
        try (var input = process.getOutputStream()) {
            input.write(commands.getBytes(StandardCharsets.UTF_8));
        }
        String output;
        try (var result = process.getInputStream()) {
            output = new String(result.readAllBytes(), StandardCharsets.UTF_8);
        }
        check(process.waitFor() == 0, "Nova should exit without an uncaught exception: " + output);
        return output;
    }

    private static void checkLoadRejected(Storage storage, String expectedMessage) throws Exception {
        try {
            storage.load();
            throw new AssertionError("Expected a load error containing: " + expectedMessage);
        } catch (NovaException e) {
            check(e.getMessage().contains(expectedMessage), "Unexpected load error: " + e.getMessage());
        }
    }

    private static void checkSameTasks(TaskList expected, TaskList actual) throws Exception {
        check(expected.getTaskCount() == actual.getTaskCount(), "The saved task count must match.");
        for (int i = 1; i <= expected.getTaskCount(); i++) {
            Task expectedTask = expected.getTask(i);
            Task actualTask = actual.getTask(i);
            check(expectedTask.getClass().equals(actualTask.getClass()), "Preserve each task's type.");
            check(expectedTask.toString().equals(actualTask.toString()), "Preserve all task fields and status.");
            if (expectedTask instanceof Deadline expectedDeadline && actualTask instanceof Deadline actualDeadline) {
                check(expectedDeadline.getBy().equals(actualDeadline.getBy()), "Preserve the deadline's LocalDate.");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Counts save attempts while retaining real file I/O to detect duplicate saves.
     */
    private static class CountingStorage extends Storage {
        private int saveCalls;

        CountingStorage(Path filePath) {
            super(filePath);
        }

        @Override
        public void save(TaskList tasks) throws NovaException {
            saveCalls++;
            super.save(tasks);
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
