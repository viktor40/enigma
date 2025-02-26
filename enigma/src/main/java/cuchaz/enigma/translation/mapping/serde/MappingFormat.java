package cuchaz.enigma.translation.mapping.serde;

import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.MappingDelta;
import cuchaz.enigma.translation.mapping.serde.enigma.EnigmaMappingsReader;
import cuchaz.enigma.translation.mapping.serde.enigma.EnigmaMappingsWriter;
import cuchaz.enigma.translation.mapping.serde.proguard.ProguardMappingsReader;
import cuchaz.enigma.translation.mapping.serde.recaf.RecafMappingsReader;
import cuchaz.enigma.translation.mapping.serde.recaf.RecafMappingsWriter;
import cuchaz.enigma.translation.mapping.serde.srg.SrgMappingsWriter;
import cuchaz.enigma.translation.mapping.serde.tiny.TinyMappingsReader;
import cuchaz.enigma.translation.mapping.serde.tiny.TinyMappingsWriter;
import cuchaz.enigma.translation.mapping.serde.tinyv2.TinyV2Reader;
import cuchaz.enigma.translation.mapping.serde.tinyv2.TinyV2Writer;
import cuchaz.enigma.translation.mapping.tree.EntryTree;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

public enum MappingFormat {
	ENIGMA_FILE(EnigmaMappingsWriter.FILE, EnigmaMappingsReader.FILE),
	ENIGMA_DIRECTORY(EnigmaMappingsWriter.DIRECTORY, EnigmaMappingsReader.DIRECTORY),
	ENIGMA_ZIP(EnigmaMappingsWriter.ZIP, EnigmaMappingsReader.ZIP),
	TINY_V2(new TinyV2Writer("calamus", "named"), new TinyV2Reader()),
	TINY_FILE(TinyMappingsWriter.INSTANCE, TinyMappingsReader.INSTANCE),
	SRG_FILE(SrgMappingsWriter.INSTANCE, null),
	PROGUARD(null, ProguardMappingsReader.INSTANCE),
	RECAF(RecafMappingsWriter.INSTANCE, RecafMappingsReader.INSTANCE);

	private final MappingsWriter writer;
	private final MappingsReader reader;

	MappingFormat(MappingsWriter writer, MappingsReader reader) {
		this.writer = writer;
		this.reader = reader;
	}

	public void write(EntryTree<EntryMapping> mappings, Path path, ProgressListener progressListener, MappingSaveParameters saveParameters)  {
		this.write(mappings, MappingDelta.added(mappings), path, progressListener, saveParameters);
	}

	public void write(EntryTree<EntryMapping> mappings, MappingDelta<EntryMapping> delta, Path path, ProgressListener progressListener, MappingSaveParameters saveParameters)  {
		if (this.writer == null) {
			throw new IllegalStateException(this.name() + " does not support writing");
		}
		this.writer.write(mappings, delta, path, progressListener, saveParameters);
	}

	public EntryTree<EntryMapping> read(Path path, ProgressListener progressListener, MappingSaveParameters saveParameters) throws IOException, MappingParseException {
		if (this.reader == null) {
			throw new IllegalStateException(this.name() + " does not support reading");
		}
		return this.reader.read(path, progressListener, saveParameters);
	}

	@Nullable
	public MappingsWriter getWriter() {
		return this.writer;
	}

	@Nullable
	public MappingsReader getReader() {
		return this.reader;
	}

	public static MappingFormat[] getReadableFormats() {
		return new MappingFormat[] {
				ENIGMA_DIRECTORY,
				ENIGMA_FILE,
				TINY_V2,
				TINY_FILE,
				ENIGMA_ZIP,
				PROGUARD
		};
	}

	public static MappingFormat[] getWritableFormats() {
		return new MappingFormat[] {
				ENIGMA_DIRECTORY,
				ENIGMA_FILE,
				TINY_V2,
				TINY_FILE,
				ENIGMA_ZIP,
				SRG_FILE
		};
	}
}
