package cuchaz.enigma.command;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.serde.*;
import cuchaz.enigma.translation.mapping.serde.enigma.EnigmaMappingsReader;
import cuchaz.enigma.translation.mapping.serde.enigma.EnigmaMappingsWriter;
import cuchaz.enigma.translation.mapping.serde.tiny.TinyMappingsReader;
import cuchaz.enigma.translation.mapping.serde.tiny.TinyMappingsWriter;
import cuchaz.enigma.translation.mapping.serde.tinyv2.TinyV2Writer;
import cuchaz.enigma.translation.mapping.tree.EntryTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MappingCommandsUtil {
	private MappingCommandsUtil() {}

	// TODO: Merge with methods in Command
	public static EntryTree<EntryMapping> read(String type, Path path, MappingSaveParameters saveParameters) throws MappingParseException, IOException {
		if (type.equals("enigma")) {
			return (Files.isDirectory(path) ? EnigmaMappingsReader.DIRECTORY : EnigmaMappingsReader.ZIP).read(path, ProgressListener.none(), saveParameters);
		}

		if (type.equals("tiny")) {
			return TinyMappingsReader.INSTANCE.read(path, ProgressListener.none(), saveParameters);
		}

		MappingFormat format = null;
		try {
			format = MappingFormat.valueOf(type.toUpperCase());
		} catch (IllegalArgumentException ignored) {
			if (type.equals("tinyv2")) {
				format = MappingFormat.TINY_V2;
			}
		}

		if (format != null) {
			return format.getReader().read(path, ProgressListener.none(), saveParameters);
		}

		throw new IllegalArgumentException("no reader for " + type);
	}

	public static void write(EntryTree<EntryMapping> mappings, String type, Path path, MappingSaveParameters saveParameters) {
		if (type.equals("enigma")) {
			EnigmaMappingsWriter.DIRECTORY.write(mappings, path, ProgressListener.none(), saveParameters);
			return;
		}

		if (type.startsWith("tinyv2:") || type.startsWith("tiny_v2:")) {
			String[] split = type.split(":");

			if (split.length != 3) {
				throw new IllegalArgumentException("specify column names as 'tinyv2:from_namespace:to_namespace'");
			}

			new TinyV2Writer(split[1], split[2]).write(mappings, path, ProgressListener.none(), saveParameters);
			return;
		}

		if (type.startsWith("tiny:")) {
			String[] split = type.split(":");

			if (split.length != 3) {
				throw new IllegalArgumentException("specify column names as 'tiny:from_column:to_column'");
			}

			new TinyMappingsWriter(split[1], split[2]).write(mappings, path, ProgressListener.none(), saveParameters);
			return;
		}

		MappingFormat format = null;
		try {
			format = MappingFormat.valueOf(type.toUpperCase());
		} catch (IllegalArgumentException ignored) {}

		if (format != null) {
			format.getWriter().write(mappings, path, ProgressListener.none(), saveParameters);
			return;
		}

		throw new IllegalArgumentException("no writer for " + type);
	}
}
