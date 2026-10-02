# Nova

Nova is a command-line task manager for keeping track of todos, deadlines, and
events. Add tasks, search their descriptions, mark them as done, and keep your
list between sessions with automatic saving.

Start with the [Nova User Guide](docs/README.md) for setup instructions, examples
of every command, and troubleshooting. The sections below cover development,
storage details, and regression tests.

## Finding tasks

Use `find <keyword>` to search task descriptions:

```text
find book
```

Nova lists matching todos, deadlines, and events in their original order, with
result numbers starting at 1. The search is case-sensitive and matches any part
of the description: `book` matches both `read book` and `notebook`, but not `Book`.
Text after `find` is searched as one phrase, with surrounding spaces ignored.
Dates, event times, and status icons are not searched.

An empty keyword produces an error; a search with no matches displays
`No matching tasks found.` Searching does not change or save tasks.
Use the full `list` to get task numbers for `mark`, `unmark`, and `delete`.

## Saving tasks

Run Nova with the project root as the working directory. Tasks are loaded from
`data/nova.txt` at startup and saved automatically after every successful `todo`,
`deadline`, `event`, `mark`, `unmark`, or `delete` command. The folder and file are
created on the first save if they do not exist.
Local task data is ignored by Git.

The UTF-8 file contains one task per line, with pipe-separated fields (no padding
spaces). `0` means not done and `1` means done. Events store both their start and end:

```text
T|1|read book
D|0|return book|2019-10-15
E|0|project meeting|Aug 6th 2pm|Aug 6th 4pm
```

Within text fields, `\|` represents a literal pipe, `\\` a backslash, `\n` a newline,
and `\r` a carriage return. Nova writes these escapes automatically.
If the file contains a malformed task, Nova reports
the affected line and stops without changing the file. Correct the file and restart.
If saving fails, Nova reports the error; changes remain in memory and the next
successful change attempts to save the complete list again.

File handling is in `nova.storage.Storage`; `nova.Nova` loads the tasks at startup,
and modifying commands save them after execution. Additions save through
`nova.command.AddCommand`; other changes currently save through `CommandHandler`.

To run the persistence tests from the project root with JDK 25:

```text
javac -Xlint:all -d out/level7 -sourcepath src/main/java src/main/java/nova/Nova.java src/test/java/nova/storage/StorageTest.java
java -cp out/level7 nova.storage.StorageTest
```

The tests use temporary folders, so they do not modify your saved tasks.

## Deadline dates

Enter deadline dates in `yyyy-MM-dd` format:

```text
deadline return book /by 2019-10-15
```

Nova stores the deadline as a `java.time.LocalDate` and displays it as
`[D][ ] return book (by: Oct 15 2019)`. Month names are always in English.
Saved files keep the ISO date (`2019-10-15`) so the value reloads reliably.
Dates are validated against the calendar: `2024-02-29` is valid, but
`2019-02-29` is rejected without adding or saving a task.

This increment supports dates without a time of day. Older free-text deadline
values such as `June 6th` need to be changed to complete ISO dates in the saved
file. Nova identifies the affected line and stops without altering the file;
it does not guess a missing year. Event start and end values remain text.

## Class responsibilities and console regression tests

`nova.ui.Ui` owns console input and output, including task listings, confirmations,
and error display. `Nova` coordinates startup and the command loop.
`CommandHandler` interprets commands. For `todo`, `deadline`, and `event`, it creates
an `AddCommand` that adds the parsed task, displays confirmation through `Ui`, and
saves through `Storage`. `AddCommand` extends the abstract `Command` class, whose
shared API is `execute(tasks, ui, storage)` and `isExit()` (false by default).
Creating a command prepares the action; calling `execute` performs it.

`TaskList` stores and validates tasks without printing them. Other operations still
run in `CommandHandler`. Later increments will extract more command subclasses and
move parsing into `Parser`, allowing the main loop to execute commands uniformly.

To compile all sources and run the regression suites in PowerShell with JDK 25:

```powershell
$sources = Get-ChildItem src/main/java, src/test/java -Recurse -Filter *.java
javac -Xlint:all -d out/tests $sources.FullName
java -cp out/tests nova.storage.StorageTest
java -cp out/tests nova.ConsoleTest
java -cp out/tests nova.SystematicTest
```

The console suite launches Nova in temporary folders and checks exact output and
saved data for task commands, invalid input, restarts, end-of-input, and corrupt
saves, plus deadline validation and date persistence. It also accepts a
compiled-classes folder as an argument to check another build against the same
expectations.

`SystematicTest` adds an independent task model and checks exact responses, task
fields and order, save counts, saved contents, and reloads. Its coverage includes:

- All ordered pairs and triples of the eight task commands, including repeats,
  across six starting states and first/last task selection: 6,912 scenarios.
- All 81 pairs and 729 triples of the nine console commands, including `bye`,
  in separate Java processes. Commands after `bye` must be ignored.
- 10,000 reproducible mixed operations and 13,456 generated input checks,
  including malformed separators, invalid commands, and recovery after errors.
- Number overflow, missing fields, case and whitespace rules, leap centuries,
  Unicode, long descriptions, duplicates, search scope, and more than 100 tasks.
- Corrupt files, escaping, line endings, end-of-input, restarts, and failed saves
  followed by recovery for every modifying command.

The console matrix uses up to eight child processes at once. Tests use isolated
temporary folders and leave your `data/nova.txt` untouched. Redirected console
output is explicitly UTF-8 so Unicode checks are consistent across platforms.
The suite reports assertion counts and stops on a failure. These are exhaustive
pairs/triples of command **families**, not all possible arguments or sequences
of arbitrary length.

To run either part separately or verify the packaged program:

```powershell
java -cp out/tests nova.SystematicTest core
java -cp out/tests nova.SystematicTest console
java -cp out/tests nova.SystematicTest console nova.jar
```

The `boundaries` option runs just the additional console boundary/restart cases.

## Building the JAR

After changing production code, rebuild the packaged application with JDK 25 so
`nova.jar` matches the source. From the project root:

```text
javac -Xlint:all -d out/release -sourcepath src/main/java src/main/java/nova/Nova.java
jar --create --file nova.jar --main-class nova.Nova -C out/release nova
java -jar nova.jar
```

Only production classes belong in the JAR; the regression suites above stay in
`out/tests`. Run the packaged-program tests above before distributing a new JAR.

## Setting up in IntelliJ IDEA

Prerequisites: JDK 25 and IntelliJ IDEA with Java 25 support.

1. Open IntelliJ IDEA (if you are not in the welcome screen, click `File` > `Close Project` to close the existing project first).
1. Open the project into IntelliJ IDEA as follows:
   1. Click `Open`.
   1. Select the project directory, and click `OK`.
   1. If there are any further prompts, accept the defaults.
1. Configure the project to use **JDK 25** (not other versions) as explained in [here](https://www.jetbrains.com/help/idea/sdk.html#set-up-jdk).<br>
   In the same dialog, set the **Project language level** field to the `SDK default` option.
1. After that, locate the `src/main/java/nova/Nova.java` file, right-click it, and choose `Run 'Nova.main()'` (if the code editor is showing compile errors, check the project SDK and source root). If the setup is correct, Nova displays its banner followed by:
   ```text
   Hello! I'm Nova.
   What can I do for you?
   ```

**Warning:** Keep the `src\main\java` folder as the root folder for Java files (i.e., don't rename those folders or move Java files to another folder outside of this folder path), as this is the default location some tools (e.g., Gradle) expect to find Java files.
