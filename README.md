# Nova project template

This is a project template for a greenfield Java project. It's named after the Java mascot _Duke_. Given below are instructions on how to use it.

## Saving tasks

Run Nova with the project root as the working directory. Tasks are loaded from
`data/nova.txt` at startup and saved automatically after every successful `todo`,
`deadline`, `event`, `mark`, or `unmark` command. The folder and file are created
on the first save if they do not exist. Local task data is ignored by Git.

The UTF-8 file contains one task per line, with pipe-separated fields (no padding
spaces). `0` means not done and `1` means done. Events store both their start and end:

```text
T|1|read book
D|0|return book|June 6th
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

To compile all sources and run both regression suites in PowerShell with JDK 25:

```powershell
$sources = Get-ChildItem src/main/java, src/test/java -Recurse -Filter *.java
javac -Xlint:all -d out/tests $sources.FullName
java -cp out/tests nova.storage.StorageTest
java -cp out/tests nova.ConsoleTest
```

The console suite launches Nova in temporary folders and checks exact output and
saved data for task commands, invalid input, restarts, end-of-input, and corrupt
saves. It also accepts a compiled-classes folder as an argument to check an earlier
build against the same expectations.

## Setting up in Intellij

Prerequisites: JDK 25, update Intellij to the most recent version.

1. Open Intellij (if you are not in the welcome screen, click `File` > `Close Project` to close the existing project first)
1. Open the project into Intellij as follows:
   1. Click `Open`.
   1. Select the project directory, and click `OK`.
   1. If there are any further prompts, accept the defaults.
1. Configure the project to use **JDK 25** (not other versions) as explained in [here](https://www.jetbrains.com/help/idea/sdk.html#set-up-jdk).<br>
   In the same dialog, set the **Project language level** field to the `SDK default` option.
1. After that, locate the `src/main/java/nova/Nova.java` file, right-click it, and choose `Run 'Nova.main()'` (if the code editor is showing compile errors, try restarting the IDE). If the setup is correct, you should see something like the below as the output:
   ```
    ____        _        
   |  _ \ _   _| | _____ 
   | | | | | | | |/ / _ \
   | |_| | |_| |   <  __/
   |____/ \__,_|_|\_\___|
   ```

**Warning:** Keep the `src\main\java` folder as the root folder for Java files (i.e., don't rename those folders or move Java files to another folder outside of this folder path), as this is the default location some tools (e.g., Gradle) expect to find Java files.
