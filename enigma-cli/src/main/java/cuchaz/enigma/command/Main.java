package cuchaz.enigma.command;

import cuchaz.enigma.Enigma;
import org.tinylog.Logger;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class Main {

	private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

	public static void main(String... args) throws Exception {
		try {
			// process the command
			if (args.length < 1)
				throw new IllegalArgumentException("Requires a command");
			String command = args[0].toLowerCase(Locale.ROOT);

			Command cmd = COMMANDS.get(command);
			if (cmd == null)
				throw new IllegalArgumentException("Command not recognized: " + command);

			if (!cmd.isValidArgument(args.length - 1)) {
				throw new CommandHelpException(cmd);
			}

			String[] cmdArgs = new String[args.length - 1];
			System.arraycopy(args, 1, cmdArgs, 0, args.length - 1);

			try {
				cmd.run(cmdArgs);
			} catch (Exception ex) {
				throw new CommandHelpException(cmd, ex);
			}
		} catch (CommandHelpException ex) {
			Logger.error(ex);
			logEnigmaInfo();
			Logger.info("Command {} has encountered an error! Usage:", ex.command.name);
			printHelp(ex.command);
			System.exit(1);
		} catch (IllegalArgumentException ex) {
			Logger.error(ex);
			printHelp();
			System.exit(1);
		}
	}

	private static void printHelp() {
		logEnigmaInfo();
		Logger.info("""
				Usage:
				\tjava -cp enigma.jar cuchaz.enigma.command.CommandMain <command> <args>
				\twhere <command> is one of:""");

		for (Command command : COMMANDS.values()) {
			printHelp(command);
		}
	}

	private static void printHelp(Command command) {
		Logger.info("\t\t{} {}", command.name, command.getUsage());
	}

	private static void register(Command command) {
		Command old = COMMANDS.put(command.name, command);
		if (old != null) {
			Logger.warn("Command {} with name {} has been substituted by {}", old, command.name, command);
		}
	}

	private static void logEnigmaInfo() {
		Logger.info("{} - {}", Enigma.NAME, Enigma.VERSION);
	}

	static {
		register(new DeobfuscateCommand());
		register(new DecompileCommand());
		register(new ConvertMappingsCommand());
		register(new ComposeMappingsCommand());
		register(new InvertMappingsCommand());
		register(new CheckMappingsCommand());
		register(new MapSpecializedMethodsCommand());
		register(new InsertProposedMappingsCommand());
		register(new DropInvalidMappingsCommand());
		register(new FillClassMappingsCommand());
	}

	private static final class CommandHelpException extends IllegalArgumentException {

		final Command command;

		CommandHelpException(Command command) {
			this.command = command;
		}

		CommandHelpException(Command command, Throwable cause) {
			super(cause);
			this.command = command;
		}
	}
}
