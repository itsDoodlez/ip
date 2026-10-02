package nova;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import nova.command.CommandHandler;
import nova.exception.NovaException;
import nova.storage.Storage;
import nova.task.Deadline;
import nova.task.Event;
import nova.task.Task;
import nova.task.TaskList;
import nova.ui.Ui;

/**
 * Runs deterministic command matrices, boundary cases, and generated input tests.
 * Expectations come from a small independent task model, never from Nova's output.
 * All save files and child-process working directories belong to this test run.
 */
public class SystematicTest {
    private static final String DIVIDER = "____________________________________________________________\n";
    private static final String WELCOME = DIVIDER + " _  _              \n"
            + "| \\| |___ ___ __ _ \n| .` / _ \\ V  V / _` |\n|_|\\_\\___/\\_/\\_/\\__,_|\n\n"
            + "Hello! I'm Nova.\nWhat can I do for you?\n" + DIVIDER;
    private static final String GOODBYE = "Bye. Hope to see you again soon!\n" + DIVIDER;
    private static final String UNKNOWN = " OOPS! I don't recognize that command. "
            + "Try: list, find, todo, deadline, event, mark, unmark, or delete.\n";
    private static final String DATE_ERROR = " OOPS! Please enter a valid deadline date in yyyy-MM-dd format "
            + "(e.g., 2019-10-15).\n";
    private static final String EVENT_FORMAT = " OOPS! An event must follow this format: "
            + "event <description> /from <start> /to <end>.\n";
    private static final String DEADLINE_FORMAT = " OOPS! A deadline must follow this format: "
            + "deadline <description> /by yyyy-MM-dd.\n";
    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static long checks;
    private static int sequences;
    private static volatile int processes;
    private static int generatedInputs;

    /**
     * Runs all tests, or only console matrices when passed "console".
     * "core" skips the slower separate-process matrix; "boundaries" runs only CLI boundaries.
     * An optional second argument
     * selects another compiled class directory or JAR for console verification.
     */
    public static void main(String[] args) throws Exception {
        check(Runtime.version().feature() == 25, "Run this suite with Java 25.");
        Path root = Files.createTempDirectory("nova-systematic-");
        PrintStream report = System.out;
        String classes = Path.of(args.length > 1 ? args[1] : System.getProperty("java.class.path"))
                .toAbsolutePath().toString();
        try (Ui ui = new Ui()) {
            if (args.length == 0 || args[0].equals("core")) {
                testCommandMatrices(root, ui);
                report.println("Passed " + sequences + " ordered command pairs/triples across six starting states.");
                testInputBoundaries(root, ui);
                testCalendarBoundaries(root, ui);
                report.println("Passed command, number, whitespace, text, and calendar boundaries.");
                testLongSequences(root, ui);
                report.println("Passed 10,000 modeled operations with repeated save/reload cycles.");
                testGeneratedInputs(root, ui);
                report.println("Passed " + generatedInputs + " generated malformed/valid input safety checks.");
                testStorageInputs(root);
                testSaveRecovery(root, ui);
                report.println("Passed storage corruption, escaping, and failed-save recovery checks.");
            }
            if (args.length == 0 || !args[0].equals("core")) {
                testConsoleBoundaries(root, classes);
                if (args.length == 0 || !args[0].equals("boundaries")) {
                    testConsoleMatrices(root, classes, report);
                }
            }
            report.println("PASS: " + checks + " assertions; " + sequences + " modeled pairs/triples; "
                    + processes + " real CLI sessions; " + generatedInputs + " generated inputs.");
        } finally {
            System.setOut(report);
            deleteTestFolder(root);
        }
    }

    /** The supported operations, plus a deliberately invalid command for recovery checks. */
    private enum Kind { TODO, DEADLINE, EVENT, LIST, FIND, MARK, UNMARK, DELETE, BYE, INVALID }

    /** A task value owned by the oracle, independent of production Task implementations. */
    private record ExpectedTask(char type, String description, String from, String to, boolean done) {
        ExpectedTask withDone(boolean value) {
            return new ExpectedTask(type, description, from, to, value);
        }

        /** Formats expected output without calling the production task classes. */
        String display() {
            String text = "[" + type + "][" + (done ? "X" : " ") + "] " + description;
            if (type == 'D') {
                LocalDate date = LocalDate.parse(from);
                text += " (by: " + MONTHS[date.getMonthValue() - 1] + " "
                        + String.format(java.util.Locale.ROOT, "%02d %04d", date.getDayOfMonth(), date.getYear()) + ")";
            } else if (type == 'E') {
                text += " (from: " + from + " to: " + to + ")";
            }
            return text;
        }

        /** Produces an expected save record independently of Storage. */
        String saved() {
            String line = type + "|" + (done ? "1" : "0") + "|" + escape(description);
            if (type == 'D' || type == 'E') {
                line += "|" + escape(from);
            }
            if (type == 'E') {
                line += "|" + escape(to);
            }
            return line + "\n";
        }
    }

    /** Input together with its intended meaning; no production parser supplies expectations. */
    private record Operation(String input, Kind kind, ExpectedTask task, int number, String keyword, String error) {
    }

    /** Predicts task state, exact response text, and whether a command should attempt a save. */
    private static class Model {
        private final List<ExpectedTask> tasks = new ArrayList<>();
        private boolean changed;

        /** Updates the oracle and returns the exact expected response for one operation. */
        String apply(Operation op) {
            changed = false;
            if (op.kind == Kind.INVALID) {
                return op.error;
            }
            if (op.task != null) {
                tasks.add(op.task);
                changed = true;
                return " Got it. I've added this task:\n   " + op.task.display() + "\n" + count();
            }
            if (op.kind == Kind.LIST || op.kind == Kind.FIND) {
                StringBuilder result = new StringBuilder(op.kind == Kind.LIST
                        ? " Here are the tasks in your list:\n" : " Here are the matching tasks in your list:\n");
                int found = 0;
                for (ExpectedTask task : tasks) {
                    if (op.kind == Kind.LIST || task.description.contains(op.keyword)) {
                        result.append(" ").append(++found).append(".").append(task.display()).append("\n");
                    }
                }
                if (op.kind == Kind.FIND && found == 0) {
                    result.append(" No matching tasks found.\n");
                }
                return result.toString();
            }
            if (tasks.isEmpty()) {
                return " OOPS! Your task list is empty.\n";
            }
            if (op.number < 1 || op.number > tasks.size()) {
                return " OOPS! There is no task numbered " + op.number
                        + ". Please choose a number from 1 to " + tasks.size() + ".\n";
            }
            changed = true;
            if (op.kind == Kind.DELETE) {
                ExpectedTask removed = tasks.remove(op.number - 1);
                return " Noted. I've removed this task:\n   " + removed.display() + "\n" + count();
            }
            ExpectedTask task = tasks.get(op.number - 1).withDone(op.kind == Kind.MARK);
            tasks.set(op.number - 1, task);
            return (task.done ? " Nice! I've marked this task as done:\n"
                    : " OK, I've marked this task as not done yet:\n") + "   " + task.display() + "\n";
        }

        String count() {
            return " Now you have " + tasks.size() + " tasks in the list.\n";
        }

        String saved() {
            StringBuilder result = new StringBuilder();
            tasks.forEach(task -> result.append(task.saved()));
            return result.toString();
        }
    }

    /** Counts real writes so rejected and read-only commands can be checked for side effects. */
    private static class CountingStorage extends Storage {
        private int saves;

        CountingStorage(Path file) {
            super(file);
        }

        @Override
        public void save(TaskList tasks) throws NovaException {
            saves++;
            super.save(tasks);
        }
    }

    /** A real handler and file, checked against the independent model after every command. */
    private static class Session {
        private final Path file;
        private final Ui ui;
        private final Model model = new Model();
        private final CountingStorage storage;
        private TaskList tasks;
        private CommandHandler handler;

        /** Starts from a known saved state, replacing only this suite's temporary fixture. */
        Session(Path root, Ui ui, List<ExpectedTask> initial) throws Exception {
            file = root.resolve("current.txt");
            this.ui = ui;
            model.tasks.addAll(initial);
            Files.writeString(file, model.saved());
            storage = new CountingStorage(file);
            reload();
        }

        /** Replaces all production task objects with freshly loaded ones to simulate a restart. */
        void reload() throws Exception {
            tasks = storage.load();
            handler = new CommandHandler(tasks, storage, ui);
            assertTasks(model.tasks, tasks);
        }

        /** Verifies the response, save count, memory, and real saved data after one operation. */
        void step(Operation op) throws Exception {
            int savesBefore = storage.saves;
            byte[] before = Files.readAllBytes(file);
            String expected = model.apply(op);
            String actual = invoke(handler, op.input);
            equal(expected, actual, "Response to " + printable(op.input));
            check(storage.saves == savesBefore + (model.changed ? 1 : 0), "Save count: " + printable(op.input));
            assertTasks(model.tasks, tasks);
            if (!model.changed) {
                check(Arrays.equals(before, Files.readAllBytes(file)), "Rejected/read-only input changed the file.");
            }
            equal(model.saved(), Files.readString(file), "Saved state after " + printable(op.input));
            assertTasks(model.tasks, storage.load());
        }
    }

    /** Enumerates every ordered pair and triple, including repeats, using first and last task numbers. */
    private static void testCommandMatrices(Path root, Ui ui) throws Exception {
        List<ExpectedTask> mixed = List.of(task('T', "book", "", "", false),
                task('D', "book", "2024-02-29", "", true), task('E', "Book", "book fair", "4pm", false),
                task('T', "notebook", "", "", true));
        List<List<ExpectedTask>> seeds = List.of(List.of(), List.of(mixed.get(0)), List.of(mixed.get(1)),
                List.of(mixed.get(2)), mixed, mixed.stream().map(t -> t.withDone(true)).toList());
        for (List<ExpectedTask> seed : seeds) {
            for (boolean last : List.of(false, true)) {
                for (int length : List.of(2, 3)) {
                    int combinations = length == 2 ? 64 : 512;
                    for (int code = 0; code < combinations; code++) {
                        Session session = new Session(root, ui, seed);
                        int remaining = code;
                        for (int position = 0; position < length; position++) {
                            Kind kind = Kind.values()[remaining % 8];
                            remaining /= 8;
                            int number = last ? Math.max(1, session.model.tasks.size()) : 1;
                            session.step(operation(kind, number));
                            session.reload();
                        }
                        sequences++;
                    }
                }
            }
        }
    }

    /** Checks missing fields, invalid numbers, strict command names, literal text, and search scope. */
    private static void testInputBoundaries(Path root, Ui ui) throws Exception {
        Session session = new Session(root, ui, List.of());
        for (String input : Arrays.asList(null, "", " ", "\t", "\u2003")) {
            session.step(invalid(input, " OOPS! Please enter a command.\n"));
        }
        for (String name : List.of("list", "find", "todo", "deadline", "event", "mark", "unmark", "delete", "bye")) {
            for (String input : List.of(name.toUpperCase(java.util.Locale.ROOT), " " + name,
                    name + "x", name + "\targument")) {
                session.step(invalid(input, UNKNOWN));
            }
        }
        for (String input : List.of("list ", "list extra", "bye ", "bye extra", "help", "save", "undo", "1")) {
            session.step(invalid(input, UNKNOWN));
        }
        for (String tail : List.of("", " ", "    ", " \t ", " \u2003")) {
            session.step(invalid("todo" + tail, " OOPS! The todo description cannot be empty.\n"));
            session.step(invalid("find" + tail, " OOPS! The search keyword cannot be empty.\n"));
            for (String name : List.of("mark", "unmark", "delete")) {
                String error = tail.contains("\u2003") ? " OOPS! The task number must be a valid whole number.\n"
                        : " OOPS! Please provide the number of the task to "
                        + (name.equals("delete") ? "delete" : "update") + ".\n";
                session.step(invalid(name + tail, error));
            }
        }
        for (String input : List.of("deadline", "deadline task", "deadline /by 2024-01-01",
                "deadline task /by", "deadline task /by ", "deadline task/by 2024-01-01",
                "deadline task /BY 2024-01-01", "deadline task /by\t2024-01-01")) {
            session.step(invalid(input, DEADLINE_FORMAT));
        }
        session.step(invalid("deadline task /by \u2003", " OOPS! The deadline date cannot be empty.\n"));
        for (String input : List.of("event", "event task", "event /from 2pm /to 4pm",
                "event task /from 2pm", "event task /to 4pm /from 2pm", "event task /from 2pm /to ",
                "event task/from 2pm /to 4pm", "event task /FROM 2pm /to 4pm")) {
            session.step(invalid(input, EVENT_FORMAT));
        }
        for (String gap : List.of("", " ", "   ", "\t", "\u2003")) {
            session.step(invalid("event task /from " + gap + " /to 4pm", " OOPS! The event start cannot be empty.\n"));
        }
        session.step(invalid("event task /from 2pm /to \u2003", " OOPS! The event end cannot be empty.\n"));
        session.step(invalid("deadline \u2003 /by 2024-01-01", " OOPS! The deadline description cannot be empty.\n"));
        session.step(invalid("event \u2003 /from 2pm /to 4pm", " OOPS! The event description cannot be empty.\n"));

        for (boolean populated : List.of(false, true)) {
            if (populated) {
                session.step(operation(Kind.TODO, 1));
            }
            for (Kind kind : List.of(Kind.MARK, Kind.UNMARK, Kind.DELETE)) {
                String name = kind.name().toLowerCase(java.util.Locale.ROOT);
                for (int number : new int[] {Integer.MIN_VALUE, -1, 0, 2, Integer.MAX_VALUE}) {
                    session.step(operation(kind, number));
                }
                for (String number : List.of("abc", "1.0", "1e0", "1 2", "1,000", "--1", "+", "-", "NaN",
                        "2147483648", "-2147483649", "9".repeat(300), "0x1", "1/2", "\u20031")) {
                    session.step(invalid(name + " " + number, " OOPS! The task number must be a valid whole number.\n"));
                }
            }
        }
        for (String number : List.of("+1", "01", "0001", "  1  ", "\t1\t", "\u0661", "\uff11")) {
            session.step(new Operation("mark " + number, Kind.MARK, null, 1, null, null));
            session.step(new Operation("unmark " + number, Kind.UNMARK, null, 1, null, null));
        }

        for (String text : List.of("read book", "Read Book", "notebook", "read  book", "中文 😀 café",
                "|\\\\n\\r\\|", "bye", "mark 1", "/from /to /by", "x".repeat(20000))) {
            session.step(add("todo   " + text + "   ", task('T', text, "", "", false)));
            session.step(find("find " + text, text));
        }
        session.step(add("event reversed /from 4pm /to 2pm", task('E', "reversed", "4pm", "2pm", false)));
        session.step(add("event text /from tomorrow | \\start /to yesterday | \\end",
                task('E', "text", "tomorrow | \\start", "yesterday | \\end", false)));
        session.step(operation(Kind.DEADLINE, 1));
        for (String keyword : List.of("book", "Book", "read book", "read  book", "absent", "[X]", "2024", "Feb", "2pm")) {
            session.step(find("find   " + keyword + "   ", keyword));
        }
        session.reload();
        session.step(operation(Kind.LIST, 1));
    }

    /** Tests every month around leap-century and range boundaries, including one day past each month. */
    private static void testCalendarBoundaries(Path root, Ui ui) throws Exception {
        for (int year : new int[] {0, 1, 4, 100, 400, 1900, 1999, 2000, 2024, 2100, 2400, 9999}) {
            Session session = new Session(root, ui, List.of());
            for (int month = 1; month <= 12; month++) {
                LocalDate first = LocalDate.of(year, month, 1);
                for (LocalDate date : List.of(first, first.withDayOfMonth(first.lengthOfMonth()))) {
                    session.step(add("deadline date /by " + date, task('D', "date", date.toString(), "", false)));
                }
                String impossible = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month,
                        first.lengthOfMonth() + 1);
                session.step(invalid("deadline invalid /by " + impossible, DATE_ERROR));
            }
        }
        Session session = new Session(root, ui, List.of());
        for (String date : List.of("2024-00-01", "2024-13-01", "2024-01-00", "2024-1-01", "2024-01-1",
                "24-01-01", "2024/01/01", "2024-01-01T00:00", "2024-01-01Z", "tomorrow", "null",
                "2024-01-01 extra", "2024-01-01 /by 2025-01-01", "2024-٠١-01", "9999999999-01-01")) {
            session.step(invalid("deadline invalid /by " + date, DATE_ERROR));
        }
    }

    /** Uses a fixed seed to reproduce long mixed sequences, invalid operations, and index shifts. */
    private static void testLongSequences(Path root, Ui ui) throws Exception {
        Random random = new Random(2113);
        for (int run = 0; run < 10; run++) {
            Session session = new Session(root, ui, List.of());
            for (int step = 0; step < 1000; step++) {
                int size = session.model.tasks.size();
                Kind kind = size > 20 ? Kind.DELETE : Kind.values()[random.nextInt(8)];
                int number = size > 20 ? 1 : random.nextInt(size + 3);
                Operation op = step % 11 == 0 ? operation(Kind.INVALID, 1) : operation(kind, number);
                session.step(op);
                if (step % 17 == 0) {
                    session.reload();
                }
            }
        }
        Session capacity = new Session(root, ui, List.of());
        for (int i = 1; i <= 256; i++) {
            capacity.step(add("todo task " + i, task('T', "task " + i, "", "", false)));
        }
        for (int number : new int[] {1, 99, 100, 101, 128, 255, 256}) {
            capacity.step(operation(Kind.MARK, number));
            capacity.step(operation(Kind.UNMARK, number));
        }
        while (!capacity.model.tasks.isEmpty()) {
            capacity.step(operation(Kind.DELETE, Math.max(1, capacity.model.tasks.size() / 2)));
        }
        capacity.reload();
        capacity.step(operation(Kind.LIST, 1));
        capacity.step(operation(Kind.MARK, 1));
    }

    /** Generates separator permutations and random text; rejected input must never mutate or save. */
    private static void testGeneratedInputs(Path root, Ui ui) throws Exception {
        CountingStorage storage = new CountingStorage(root.resolve("generated.txt"));
        String[] pieces = {"", " ", "x", "/from", "/to", "/by", " /from ", " /to ",
            " /by ", "\t", "\u2003", "|\\"};
        for (String first : pieces) {
            for (String second : pieces) {
                for (String third : pieces) {
                    for (String name : List.of("event", "deadline")) {
                        safetyCheck(name + " task " + first + second + third + " end", storage, ui);
                    }
                }
            }
        }
        Random random = new Random(0x2113);
        String[] tokens = {" ", "\t", "\u2003", "中文", "😀", "|", "\\", "/from", "/to", "/by",
            "0", "-1", "2147483648", "2024-02-29", "x", "\u0000", "\r", "\n"};
        for (int i = 0; i < 10000; i++) {
            StringBuilder input = new StringBuilder(Kind.values()[random.nextInt(Kind.values().length)]
                    .name().toLowerCase(java.util.Locale.ROOT));
            input.append(' ');
            for (int j = random.nextInt(12); j >= 0; j--) {
                input.append(tokens[random.nextInt(tokens.length)]);
            }
            safetyCheck(input.toString(), storage, ui);
        }
    }

    /** Runs one generated input against existing tasks and verifies acceptance/rejection invariants. */
    private static void safetyCheck(String input, CountingStorage storage, Ui ui) throws Exception {
        List<ExpectedTask> seed = List.of(task('T', "keep | \\ me", "", "", true));
        TaskList tasks = new TaskList();
        tasks.addTask(new nova.task.Todo(seed.get(0).description));
        tasks.getTask(1).markAsDone();
        storage.save(tasks);
        int before = storage.saves;
        String output = invoke(new CommandHandler(tasks, storage, ui), input);
        if (output.startsWith(" OOPS!")) {
            check(storage.saves == before, "Rejected fuzz input attempted a save: " + printable(input));
            assertTasks(seed, tasks);
            assertTasks(seed, storage.load());
        } else {
            check(storage.saves == before || storage.saves == before + 1, "Unexpected number of saves.");
            TaskList loaded = storage.load();
            check(tasks.getTaskCount() == loaded.getTaskCount(), "Accepted fuzz input must reload.");
            for (int i = 1; i <= tasks.getTaskCount(); i++) {
                check(tasks.getTask(i).getDescription().equals(loaded.getTask(i).getDescription()),
                        "Generated text changed after reloading.");
                equal(tasks.getTask(i).toString(), loaded.getTask(i).toString(), "Generated input round trip");
            }
        }
        generatedInputs++;
    }

    /** Tries corrupt fields at the start, middle, and end of saves, and many escaped Unicode records. */
    private static void testStorageInputs(Path root) throws Exception {
        Path file = root.resolve("storage-inputs.txt");
        Storage storage = new Storage(file);
        List<String> corrupt = new ArrayList<>(List.of("", " ", "T", "T|0", "T|0|", "T|0| \t",
                "T|0|\u2003", "T|0|extra|field", "t|0|x", "X|0|x", " T|0|x", "\ufeffT|0|x",
                "T|0|bad\\q", "T|0|bad\\", "T|0|bad\\t", "D|0|x|2024-02-30", "D|0|x|tomorrow",
                "D|0|x| 2024-01-01", "D|0|x|2024-01-01 ", "E|0|x|start", "E|0|x||end", "E|0|x|start|"));
        for (String status : List.of("", " ", "2", "-1", "true", "00", "01", " 1", "1 ")) {
            for (String type : List.of("T", "D", "E")) {
                corrupt.add(type + "|" + status + "|x" + (type.equals("D") ? "|2024-01-01"
                        : type.equals("E") ? "|start|end" : ""));
            }
        }
        for (String line : corrupt) {
            for (int index = 0; index < 3; index++) {
                List<String> lines = new ArrayList<>(List.of("T|0|first", "D|1|middle|2024-02-29", "E|0|last|a|b"));
                lines.set(index, line);
                byte[] original = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
                Files.write(file, original);
                try {
                    storage.load();
                    throw new AssertionError("Accepted corrupt record: " + printable(line));
                } catch (NovaException e) {
                    check(e.getMessage().contains("line " + (index + 1) + ":"), "Report the correct corrupt line.");
                    check(Arrays.equals(original, Files.readAllBytes(file)), "Loading corruption changed the file.");
                }
            }
        }
        for (byte[] bytes : List.of(new byte[] {(byte) 0xc3, 0x28}, new byte[] {(byte) 0xff},
                new byte[] {(byte) 0xe2, (byte) 0x82})) {
            Files.write(file, bytes);
            try {
                storage.load();
                throw new AssertionError("Accepted invalid UTF-8.");
            } catch (NovaException e) {
                check(e.getMessage().contains("Could not read"), "Explain invalid encoding.");
                check(Arrays.equals(bytes, Files.readAllBytes(file)), "Keep invalid bytes intact.");
            }
        }
        for (String newline : List.of("\n", "\r\n", "\r")) {
            for (boolean trailing : List.of(false, true)) {
                Files.writeString(file, "T|0|first" + newline + "E|1|last|a|b" + (trailing ? newline : ""));
                assertTasks(List.of(task('T', "first", "", "", false), task('E', "last", "a", "b", true)), storage.load());
            }
        }
        List<ExpectedTask> expected = new ArrayList<>();
        String[] fragments = {"x", "|", "\\", "\r", "\n", "中文", "😀", "\t", "\\n", "\\|"};
        Random random = new Random(32113);
        for (int i = 0; i < 1000; i++) {
            StringBuilder text = new StringBuilder("task " + i + " ");
            for (int j = 0; j < 8; j++) {
                text.append(fragments[random.nextInt(fragments.length)]);
            }
            expected.add(task(i % 2 == 0 ? 'T' : 'E', text.toString(), "start " + text, "end " + text, i % 3 == 0));
        }
        StringBuilder contents = new StringBuilder();
        expected.forEach(t -> contents.append(t.saved()));
        Files.writeString(file, contents.toString());
        TaskList loaded = storage.load();
        assertTasks(expected, loaded);
        storage.save(loaded);
        equal(contents.toString(), Files.readString(file), "Escaped records survive writing");
        assertTasks(expected, storage.load());
    }

    /** Forces real save failures for each mutation, then checks recovery saves the complete current state. */
    private static void testSaveRecovery(Path root, Ui ui) throws Exception {
        for (Kind kind : List.of(Kind.TODO, Kind.DEADLINE, Kind.EVENT, Kind.MARK, Kind.UNMARK, Kind.DELETE)) {
            Session session = new Session(root, ui, List.of(task('T', "first", "", "", true),
                    task('D', "second", "2024-02-29", "", false), task('E', "third", "2pm", "4pm", true)));
            Files.delete(session.file);
            Files.createDirectory(session.file);
            Path marker = session.file.resolve("keep.txt");
            Files.writeString(marker, "preserve");
            Operation op = operation(kind, 1);
            String expected = session.model.apply(op);
            int before = session.storage.saves;
            String actual = invoke(session.handler, op.input).replace("\r\n", "\n");
            check(actual.startsWith(expected + " OOPS! Your change is in memory, but Nova could not save"),
                    "Explain failed save after " + kind + ": " + printable(actual));
            check(session.storage.saves == before + 1, "One failed save attempt per mutation.");
            assertTasks(session.model.tasks, session.tasks);
            equal("preserve", Files.readString(marker), "Preserve blocked save target");
            for (Operation readOnly : List.of(operation(Kind.LIST, 1), find("find first", "first"),
                    operation(Kind.INVALID, 1))) {
                equal(session.model.apply(readOnly), invoke(session.handler, readOnly.input), "Read after failed save");
                check(session.storage.saves == before + 1, "Read-only/error commands must not retry saving.");
            }
            try (var files = Files.list(root)) {
                check(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")), "Clean failed-save temp files.");
            }
            Files.delete(marker);
            Files.delete(session.file);
            Files.writeString(session.file, "");
            session.step(add("todo recovery", task('T', "recovery", "", "", false)));
            session.reload();
        }
    }

    /** Runs all 81 pairs and 729 triples of the nine CLI commands, including early exit. */
    private static void testConsoleMatrices(Path root, String classes, PrintStream report) throws Exception {
        // Limit concurrency and give each child its own folder; Java startup dominates these short sessions.
        try (var workers = Executors.newFixedThreadPool(8)) {
            var completed = new ExecutorCompletionService<Void>(workers);
            int submitted = 0;
            for (int length : List.of(2, 3)) {
                int combinations = length == 2 ? 81 : 729;
                for (int code = 0; code < combinations; code++) {
                    List<Operation> commands = new ArrayList<>();
                    int remaining = code;
                    for (int position = 0; position < length; position++) {
                        commands.add(operation(Kind.values()[remaining % 9], 1));
                        remaining /= 9;
                    }
                    Path folder = root.resolve("console-matrix-" + length + "-" + code);
                    completed.submit(() -> {
                        verifyConsole(folder, classes, List.of(), commands, "\n", true);
                        return null;
                    });
                    submitted++;
                }
            }
            for (int count = 1; count <= submitted; count++) {
                completed.take().get();
                if (count % 100 == 0 || count == submitted) {
                    report.println("Verified " + count + "/" + submitted + " real CLI matrix sessions.");
                }
            }
        }
    }

    /** Checks platform line endings, Unicode, long input, missing final newlines, and restarts. */
    private static void testConsoleBoundaries(Path root, String classes) throws Exception {
        Path folder = root.resolve("console-boundaries");
        List<Operation> commands = List.of(operation(Kind.INVALID, 1),
                invalid("bye ", UNKNOWN), invalid("BYE", UNKNOWN), invalid("bye extra", UNKNOWN),
                add("todo 中文 😀 café | \\n", task('T', "中文 😀 café | \\n", "", "", false)),
                operation(Kind.MARK, 1), find("find 中文", "中文"), operation(Kind.LIST, 1),
                operation(Kind.BYE, 1), add("todo ignored", task('T', "ignored", "", "", false)));
        for (String newline : List.of("\n", "\r\n", "\r")) {
            for (boolean trailing : List.of(false, true)) {
                verifyConsole(folder, classes, List.of(), commands, newline, trailing);
            }
        }
        for (List<Operation> eof : List.of(List.<Operation>of(), List.of(operation(Kind.LIST, 1)),
                List.of(operation(Kind.TODO, 1)), List.of(operation(Kind.BYE, 1)),
                List.of(operation(Kind.INVALID, 1)))) {
            verifyConsole(folder, classes, List.of(), eof, "\n", false);
        }
        String longText = "large 中文 | \\" + "x".repeat(65536);
        verifyConsole(folder, classes, List.of(), List.of(add("todo " + longText,
                task('T', longText, "", "", false)), operation(Kind.MARK, 1), operation(Kind.LIST, 1)), "\n", false);
        List<ExpectedTask> seed = List.of(task('T', "unrelated", "", "", true),
                task('D', "match", "2000-02-29", "", false), task('E', "match", "4pm", "2pm", true));
        verifyConsole(folder, classes, seed, List.of(find("find match", "match"), operation(Kind.DELETE, 1),
                operation(Kind.MARK, 1), operation(Kind.UNMARK, 2), operation(Kind.LIST, 1)), "\n", false);
        Model reloaded = new Model();
        reloaded.tasks.addAll(List.of(seed.get(1).withDone(true), seed.get(2).withDone(false)));
        equal(WELCOME + DIVIDER + reloaded.apply(operation(Kind.LIST, 1)) + DIVIDER + DIVIDER + GOODBYE,
                runNova(folder, classes, "list\nbye\n"), "Real process restart");
        Path corrupt = folder.resolve("data/nova.txt");
        Files.writeString(corrupt, "T|0|keep\nE|0|missing end|2pm\n");
        byte[] original = Files.readAllBytes(corrupt);
        String output = runNova(folder, classes, "todo ignored\nbye\n");
        check(output.contains("line 2:") && output.contains("Your file was not changed."), "Explain corrupt startup.");
        check(!output.contains("I've added") && !output.contains(GOODBYE), "Corrupt startup must stop before commands.");
        check(Arrays.equals(original, Files.readAllBytes(corrupt)), "Protect corrupt data on startup.");
    }

    /** Compares a complete real CLI transcript and final file to the independent model. */
    private static void verifyConsole(Path folder, String classes, List<ExpectedTask> seed,
            List<Operation> commands, String newline, boolean trailing) throws Exception {
        Path file = folder.resolve("data/nova.txt");
        Files.createDirectories(file.getParent());
        Files.deleteIfExists(file);
        Model model = new Model();
        model.tasks.addAll(seed);
        boolean saved = !seed.isEmpty();
        if (saved) {
            Files.writeString(file, model.saved());
        }
        StringBuilder expected = new StringBuilder(WELCOME);
        for (Operation op : commands) {
            expected.append(DIVIDER);
            if (op.kind == Kind.BYE) {
                break;
            }
            expected.append(model.apply(op)).append(DIVIDER);
            saved |= model.changed;
        }
        expected.append(GOODBYE);
        String input = String.join(newline, commands.stream().map(Operation::input).toList());
        if (trailing && !commands.isEmpty()) {
            input += newline;
        }
        equal(expected.toString(), runNova(folder, classes, input), "CLI transcript for " + printable(input));
        check(Files.exists(file) == saved, "Only changes should create a save file.");
        if (saved) {
            equal(model.saved(), Files.readString(file), "CLI saved state");
            assertTasks(model.tasks, new Storage(file).load());
        }
    }

    /** Redirects both input and output to avoid pipe deadlocks, and enforces a child-process timeout. */
    private static String runNova(Path folder, String classes, String input) throws Exception {
        Files.createDirectories(folder);
        Path inputFile = folder.resolve("input.txt");
        Path outputFile = folder.resolve("output.txt");
        Files.writeString(inputFile, input);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-cp", classes, "nova.Nova").directory(folder.toFile())
                .redirectInput(inputFile.toFile()).redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        try {
            check(process.waitFor(10, TimeUnit.SECONDS), "Nova did not exit within 10 seconds.");
            String output = Files.readString(outputFile);
            check(process.exitValue() == 0, "Nova crashed for " + printable(input) + "\n" + output);
            synchronized (SystematicTest.class) {
                processes++;
            }
            return output;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
        }
    }

    /** Constructs a representative input for each family without parsing a command string. */
    private static Operation operation(Kind kind, int number) {
        return switch (kind) {
        case TODO -> add("todo book", task('T', "book", "", "", false));
        case DEADLINE -> add("deadline book /by 2024-02-29", task('D', "book", "2024-02-29", "", false));
        case EVENT -> add("event book /from 2pm /to 4pm", task('E', "book", "2pm", "4pm", false));
        case FIND -> find("find book", "book");
        case INVALID -> invalid("event meeting /from /to 4pm", " OOPS! The event start cannot be empty.\n");
        default -> new Operation(kind.name().toLowerCase(java.util.Locale.ROOT)
                + (kind == Kind.MARK || kind == Kind.UNMARK || kind == Kind.DELETE ? " " + number : ""),
                kind, null, number, null, null);
        };
    }

    private static ExpectedTask task(char type, String description, String from, String to, boolean done) {
        return new ExpectedTask(type, description, from, to, done);
    }

    private static Operation add(String input, ExpectedTask task) {
        return new Operation(input, switch (task.type) {
        case 'T' -> Kind.TODO;
        case 'D' -> Kind.DEADLINE;
        default -> Kind.EVENT;
        }, task, 0, null, null);
    }

    private static Operation invalid(String input, String error) {
        return new Operation(input, Kind.INVALID, null, 0, null, error);
    }

    private static Operation find(String input, String keyword) {
        return new Operation(input, Kind.FIND, null, 0, keyword, null);
    }

    /** Captures the real UI output, while allowing only expected application exceptions. */
    private static String invoke(CommandHandler handler, String input) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            try {
                handler.handleCommand(input);
            } catch (NovaException e) {
                System.out.println(e.getMessage());
            } catch (RuntimeException e) {
                throw new AssertionError("Unhandled exception for " + printable(input), e);
            }
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** Checks raw task fields as well as display, order, type, status, and count. */
    private static void assertTasks(List<ExpectedTask> expected, TaskList actual) throws Exception {
        check(expected.size() == actual.getTaskCount(), "Task count differs.");
        for (int i = 0; i < expected.size(); i++) {
            ExpectedTask value = expected.get(i);
            Task task = actual.getTask(i + 1);
            check(value.description.equals(task.getDescription()) && value.done == task.isDone(), "Task fields differ.");
            equal(value.display(), task.toString(), "Task display");
            check(task.getClass().getSimpleName().equals(switch (value.type) {
            case 'T' -> "Todo";
            case 'D' -> "Deadline";
            default -> "Event";
            }), "Task type differs.");
            if (task instanceof Deadline deadline) {
                check(value.from.equals(deadline.getBy().toString()), "Deadline date differs.");
            } else if (task instanceof Event event) {
                check(value.from.equals(event.getFrom()) && value.to.equals(event.getTo()), "Event fields differ.");
            }
        }
    }

    /** Escapes each character independently to provide a storage oracle. */
    private static String escape(String text) {
        StringBuilder result = new StringBuilder();
        for (char ch : text.toCharArray()) {
            result.append(switch (ch) {
            case '\\' -> "\\\\";
            case '|' -> "\\|";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            default -> String.valueOf(ch);
            });
        }
        return result.toString();
    }

    private static String printable(String text) {
        return text == null ? "null" : text.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static void equal(String expected, String actual, String label) {
        check(expected.replace("\r\n", "\n").equals(actual.replace("\r\n", "\n")),
                label + "\nExpected: " + printable(expected) + "\nActual: " + printable(actual));
    }

    private static synchronized void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** Deletes only the dedicated temporary tree created by this suite. */
    private static void deleteTestFolder(Path folder) throws IOException {
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
