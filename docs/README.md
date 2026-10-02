# Nova User Guide

Nova is a text-based chatbot for keeping track of your todos, deadlines, and
events. Type a command, press **Enter**, and Nova handles the rest.

## Quick start

1. Install **JDK 25**. Check that `java -version` and `javac -version` both show 25.
2. Download the source from the [Nova repository](https://github.com/itsDoodlez/ip)
   using **Code > Download ZIP**, then extract it. You can also use an existing
   clone of the repository.
3. Open a terminal in the extracted project folder (the folder containing `src` and `docs`).
   Compile and start Nova:

   ```text
   javac -d out -sourcepath src/main/java src/main/java/nova/Nova.java
   java -cp out nova.Nova
   ```

4. When Nova greets you, try `todo read book`, then `list`.

For later sessions, run `java -cp out nova.Nova` from the same project folder.

The download also includes `nova.jar`. To use the packaged application with
Java 25, run `java -jar nova.jar` from that folder. Use a terminal to run Nova so
you can enter commands and see its responses.

## Features

Enter one command per line, using lowercase command names and the spaces shown.
Start directly with the command name. Replace UPPERCASE placeholders below with
your own text; all fields are required. Descriptions can contain spaces without
quotation marks.

### Adding tasks: `todo`, `deadline`, `event`

Each addition creates an incomplete task. Nova confirms the task and the updated
number of tasks.

| Task type | Format | Example |
| --- | --- | --- |
| Todo: something to do | `todo DESCRIPTION` | `todo read book` |
| Deadline: something due on a date | `deadline DESCRIPTION /by YYYY-MM-DD` | `deadline return book /by 2026-10-15` |
| Event: something with a start and end | `event DESCRIPTION /from START /to END` | `event study group /from 2pm /to 4pm` |

Deadline dates must be real calendar dates in `yyyy-MM-dd` format, such as
`2026-10-15`, which displays as `Oct 15 2026`. Dates like `2026-02-30`, free text
like `tomorrow`, and times of day are not accepted for deadlines.

Event start and end values are free text, such as `Oct 16 2pm`. Nova displays them
as entered and does not check their dates or chronological order. Keep `/from`
before `/to`, with spaces around both separators.

In deadline and event commands, `/by`, `/from`, and `/to` with surrounding spaces
act as separators. Avoid those separator phrases inside the description or event
start text so Nova can identify the fields correctly. Todo descriptions do not
have this restriction.

### Viewing all tasks: `list`

Type `list` to see every task, including completed ones. After adding the three
examples above to an empty list, you will see:

```text
 Here are the tasks in your list:
 1.[T][ ] read book
 2.[D][ ] return book (by: Oct 15 2026)
 3.[E][ ] study group (from: 2pm to: 4pm)
```

`[T]`, `[D]`, and `[E]` identify todos, deadlines, and events. `[ ]` means
incomplete; `[X]` means done. An empty list shows just the heading.

### Finding tasks: `find`

Format: `find KEYWORD`

Example: `find book` finds both `read book` and `return book`.

The search checks descriptions only. It is case-sensitive and matches parts of
words: `book` also matches `notebook`, but not `Book`. Multiple words are searched
as one phrase, so `find read book` looks for that exact phrase. If nothing matches,
Nova displays `No matching tasks found.`

Matches keep their original order and are numbered from 1. **Use `list` to get
the task numbers for `mark`, `unmark`, or `delete`; search-result numbers do not
change those commands.**

### Updating completion: `mark` and `unmark`

Use `mark NUMBER` to mark a task as done, or `unmark NUMBER` to make it incomplete
again. For example, `mark 1` marks the first task in `list` as done; `unmark 1`
reverses that change. Nova displays the updated task.

Numbers must be whole numbers from 1 to the number of tasks in the full list.

### Removing a task: `delete`

Format: `delete NUMBER`

Example: `delete 2` removes the second task in `list`. Nova confirms the removal
and the remaining task count. Deletion is immediate and has no undo command.
Later task numbers shift up, so run `list` again before removing another task.

### Exiting: `bye`

Type `bye` to close Nova. Tasks are saved as you change them; no save command is
needed.

## Saving and troubleshooting

Nova automatically saves additions, completion changes, and deletions to
`data/nova.txt` in the folder where you launched it, and reloads them next time.
It creates the file on your first change. Always start from the same project
folder to use the same task list. You can copy the file elsewhere as a backup.

- **Command rejected?** Follow the format shown above, supply all required text,
  and use a valid task number or date. Invalid commands leave your tasks unchanged.
- **Saving failed?** Your change is still visible for this session. Fix access to
  the reported file or folder, then make a task change to retry saving before
  exiting. `bye` does not retry a failed save.
- **Loading failed?** Nova stops and leaves the saved file untouched. Correct the
  reported file or line, or restore a backup, then restart.
