package cuchaz.enigma.translation.mapping.serde.tiny;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.serde.MappingParseException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingPair;
import cuchaz.enigma.translation.mapping.serde.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.serde.MappingsReader;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.I18n;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public enum TinyMappingsReader implements MappingsReader {
	INSTANCE;

	@Override
	public EntryTree<EntryMapping> read(Path path, ProgressListener progress, MappingSaveParameters saveParameters) throws IOException, MappingParseException {
		return this.read(path, Files.readAllLines(path, StandardCharsets.UTF_8), progress);
	}

	private EntryTree<EntryMapping> read(Path path, List<String> lines, ProgressListener progress) throws MappingParseException {
		EntryTree<EntryMapping> mappings = new HashEntryTree<>();
		lines.remove(0);

		progress.init(lines.size(), I18n.translate("progress.mappings.tiny_file.loading"));

		for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
			progress.step(lineNumber, "");

			String line = lines.get(lineNumber);

			if (line.trim().startsWith("#")) {
				continue;
			}

			try {
				MappingPair<?, EntryMapping> mapping = this.parseLine(line);
				mappings.insert(mapping.getEntry(), mapping.getMapping());
			} catch (Exception e) {
				throw new MappingParseException(path, lineNumber, e);
			}
		}

		return mappings;
	}

	private MappingPair<?, EntryMapping> parseLine(String line) {
		String[] tokens = line.split("\t");

		String key = tokens[0];
		return switch (key) {
			case "CLASS" -> this.parseClass(tokens);
			case "FIELD" -> this.parseField(tokens);
			case "METHOD" -> this.parseMethod(tokens);
			case "MTH-ARG" -> this.parseArgument(tokens);
			default -> throw new RuntimeException("Unknown token '" + key + "'!");
		};
	}

	private MappingPair<ClassEntry, EntryMapping> parseClass(String[] tokens) {
		ClassEntry obfuscatedEntry = new ClassEntry(tokens[1]);
		String mapping = tokens[2];
		if (mapping.indexOf('$') > 0) {
			// inner classes should map to only the final part
			mapping = mapping.substring(mapping.lastIndexOf('$') + 1);
		}
		return new MappingPair<>(obfuscatedEntry, new EntryMapping(mapping));
	}

	private MappingPair<FieldEntry, EntryMapping> parseField(String[] tokens) {
		ClassEntry ownerClass = new ClassEntry(tokens[1]);
		TypeDescriptor descriptor = new TypeDescriptor(tokens[2]);

		FieldEntry obfuscatedEntry = new FieldEntry(ownerClass, tokens[3], descriptor);
		String mapping = tokens[4];
		return new MappingPair<>(obfuscatedEntry, new EntryMapping(mapping));
	}

	private MappingPair<MethodEntry, EntryMapping> parseMethod(String[] tokens) {
		ClassEntry ownerClass = new ClassEntry(tokens[1]);
		MethodDescriptor descriptor = new MethodDescriptor(tokens[2]);

		MethodEntry obfuscatedEntry = new MethodEntry(ownerClass, tokens[3], descriptor);
		String mapping = tokens[4];
		return new MappingPair<>(obfuscatedEntry, new EntryMapping(mapping));
	}

	private MappingPair<LocalVariableEntry, EntryMapping> parseArgument(String[] tokens) {
		ClassEntry ownerClass = new ClassEntry(tokens[1]);
		MethodDescriptor ownerDescriptor = new MethodDescriptor(tokens[2]);
		MethodEntry ownerMethod = new MethodEntry(ownerClass, tokens[3], ownerDescriptor);
		int variableIndex = Integer.parseInt(tokens[4]);

		String mapping = tokens[5];
		LocalVariableEntry obfuscatedEntry = new LocalVariableEntry(ownerMethod, variableIndex, "", true, null);
		return new MappingPair<>(obfuscatedEntry, new EntryMapping(mapping));
	}
}
