package nova.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import nova.exception.NovaException;
import nova.task.Deadline;
import nova.task.Event;
import nova.task.Task;
import nova.task.TaskList;
import nova.task.Todo;

/**
 * Reads and writes Nova's tasks as UTF-8 text, with one task per line.
 * Fields are separated by pipes; backslashes escape pipes and line breaks in text.
 */
public class Storage {
    private final Path filePath;

    public Storage(Path filePath) {
        this.filePath = filePath;
    }

    /**
     * Loads saved tasks, or returns an empty list when the file does not exist yet.
     * Rejects malformed files and files exceeding the task list's capacity, so Nova
     * cannot overwrite them with a partially loaded list.
     *
     * @return tasks in their saved order, including their completion status
     * @throws NovaException if the file cannot be read or fully loaded
     */
    public TaskList load() throws NovaException {
        List<String> lines;
        try {
            lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return new TaskList();
        } catch (IOException e) {
            throw new NovaException(" OOPS! Could not read tasks from " + filePath + ": " + e.getMessage());
        }

        TaskList taskList = new TaskList();
        for (int i = 0; i < lines.size(); i++) {
            try {
                taskList.addTask(parseTask(lines.get(i)));
            } catch (IllegalArgumentException | NovaException e) {
                throw new NovaException(" OOPS! Could not load saved task in " + filePath + " at line " + (i + 1)
                        + ": " + e.getMessage());
            }
        }
        return taskList;
    }

    /**
     * Saves the entire list, creating its parent folder if necessary.
     * Writes a temporary file first to avoid truncating the existing save on a write failure.
     *
     * @param taskList the current tasks to save
     * @throws NovaException if the tasks cannot be saved
     */
    public void save(TaskList taskList) throws NovaException {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= taskList.getTaskCount(); i++) {
            lines.add(formatTask(taskList.getTask(i)));
        }

        Path folder = filePath.getParent();
        if (folder == null) {
            folder = Path.of(".");
        }
        try {
            Files.createDirectories(folder);
            Path temporaryFile = Files.createTempFile(folder, "nova-", ".tmp");
            try {
                Files.write(temporaryFile, lines, StandardCharsets.UTF_8);
                try {
                    Files.move(temporaryFile, filePath,
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        } catch (IOException e) {
            throw new NovaException(" OOPS! Your change is in memory, but Nova could not save tasks to "
                    + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Converts a task to type, status, description, and any date/time fields.
     */
    private String formatTask(Task task) throws NovaException {
        String details = "|" + (task.isDone() ? "1" : "0") + "|" + escape(task.getDescription());
        if (task instanceof Todo) {
            return "T" + details;
        } else if (task instanceof Deadline deadline) {
            return "D" + details + "|" + escape(deadline.getBy());
        } else if (task instanceof Event event) {
            return "E" + details + "|" + escape(event.getFrom()) + "|" + escape(event.getTo());
        }
        throw new NovaException(" OOPS! Cannot save an unsupported task type.");
    }

    /**
     * Reconstructs a task after validating its type, status, and required fields.
     */
    private Task parseTask(String line) {
        List<String> fields = splitFields(line);
        if (fields.size() < 3) {
            throw new IllegalArgumentException("Expected a task type, status, and description.");
        }
        String type = fields.get(0);
        int expectedFields = switch (type) {
        case "T" -> 3;
        case "D" -> 4;
        case "E" -> 5;
        default -> throw new IllegalArgumentException("Unknown task type: " + type);
        };
        if (fields.size() != expectedFields) {
            throw new IllegalArgumentException("Expected " + expectedFields + " fields for task type " + type + ".");
        }
        String status = fields.get(1);
        if (!status.equals("0") && !status.equals("1")) {
            throw new IllegalArgumentException("Completion status must be 0 or 1.");
        }
        for (int i = 2; i < fields.size(); i++) {
            if (fields.get(i).isBlank()) {
                throw new IllegalArgumentException("Task descriptions and dates/times must not be blank.");
            }
        }

        Task task = switch (type) {
        case "T" -> new Todo(fields.get(2));
        case "D" -> new Deadline(fields.get(2), fields.get(3));
        case "E" -> new Event(fields.get(2), fields.get(3), fields.get(4));
        default -> throw new IllegalArgumentException("Unknown task type: " + type);
        };
        if (status.equals("1")) {
            task.markAsDone();
        }
        return task;
    }

    /**
     * Escapes characters that would otherwise be mistaken for file separators.
     */
    private String escape(String text) {
        return text.replace("\\", "\\\\").replace("|", "\\|")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * Splits a saved line while decoding escaped pipes, backslashes, and line breaks.
     * Rejects invalid escapes instead of silently changing the saved text.
     */
    private List<String> splitFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (escaped) {
                field.append(switch (character) {
                case '\\' -> '\\';
                case '|' -> '|';
                case 'n' -> '\n';
                case 'r' -> '\r';
                default -> throw new IllegalArgumentException("Invalid escape sequence: \\" + character);
                });
                escaped = false;
            } else if (character == '\\') {
                escaped = true;
            } else if (character == '|') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (escaped) {
            throw new IllegalArgumentException("Incomplete escape sequence at the end of the line.");
        }
        fields.add(field.toString());
        return fields;
    }
}
