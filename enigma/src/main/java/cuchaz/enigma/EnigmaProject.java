package cuchaz.enigma;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;

import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.analysis.index.EnclosingMethodIndex;
import cuchaz.enigma.analysis.index.JarIndex;
import cuchaz.enigma.api.service.NameProposalService;
import cuchaz.enigma.api.service.ObfuscationTestService;
import cuchaz.enigma.bytecode.translators.TranslationClassVisitor;
import cuchaz.enigma.classprovider.ClassProvider;
import cuchaz.enigma.classprovider.ObfuscationFixClassProvider;
import cuchaz.enigma.source.Decompiler;
import cuchaz.enigma.source.DecompilerService;
import cuchaz.enigma.source.SourceSettings;
import cuchaz.enigma.translation.ProposingTranslator;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.EntryRemapper;
import cuchaz.enigma.translation.mapping.MappingsChecker;
import cuchaz.enigma.translation.mapping.tree.DeltaTrackingTree;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.I18n;

public class EnigmaProject {
	private final Enigma enigma;

	private final Path jarPath;
	private final ClassProvider classProvider;
	private final JarIndex jarIndex;
	private final byte[] jarChecksum;

	private EntryRemapper mapper;

	public EnigmaProject(Enigma enigma, Path jarPath, ClassProvider classProvider, JarIndex jarIndex, byte[] jarChecksum) {
		Preconditions.checkArgument(jarChecksum.length == 20);
		this.enigma = enigma;
		this.jarPath = jarPath;
		this.classProvider = classProvider;
		this.jarIndex = jarIndex;
		this.jarChecksum = jarChecksum;

		this.mapper = EntryRemapper.empty(jarIndex);
	}

	public void setMappings(EntryTree<EntryMapping> mappings) {
		if (mappings != null) {
			mapper = EntryRemapper.mapped(jarIndex, mappings);
		} else {
			mapper = EntryRemapper.empty(jarIndex);
		}
	}

	public Enigma getEnigma() {
		return enigma;
	}

	public Path getJarPath() {
		return jarPath;
	}

	public ClassProvider getClassProvider() {
		return classProvider;
	}

	public JarIndex getJarIndex() {
		return jarIndex;
	}

	public byte[] getJarChecksum() {
		return jarChecksum;
	}

	public EntryRemapper getMapper() {
		return mapper;
	}

	public void dropMappings(ProgressListener progress) {
		DeltaTrackingTree<EntryMapping> mappings = mapper.getObfToDeobf();

		Collection<Entry<?>> dropped = dropMappings(mappings, progress);
		for (Entry<?> entry : dropped) {
			mappings.trackChange(entry);
		}
	}

	private Collection<Entry<?>> dropMappings(EntryTree<EntryMapping> mappings, ProgressListener progress) {
		// drop mappings that don't match the jar
		MappingsChecker checker = new MappingsChecker(jarIndex, mappings);
		MappingsChecker.Dropped droppedBroken = checker.dropBrokenMappings(progress);

		Map<Entry<?>, String> droppedBrokenMappings = droppedBroken.getDroppedMappings();
		for (Map.Entry<Entry<?>, String> mapping : droppedBrokenMappings.entrySet()) {
			System.out.println("WARNING: Couldn't find " + mapping.getKey() + " (" + mapping.getValue() + ") in jar. Mapping was dropped.");
		}

		MappingsChecker.Dropped droppedEmpty = checker.dropEmptyMappings(progress);

		Map<Entry<?>, String> droppedEmptyMappings = droppedEmpty.getDroppedMappings();
		for (Map.Entry<Entry<?>, String> mapping : droppedEmptyMappings.entrySet()) {
			System.out.println("WARNING: " + mapping.getKey() + " (" + mapping.getValue() + ") was empty. Mapping was dropped.");
		}

		Collection<Entry<?>> droppedMappings = new HashSet<>();
		droppedMappings.addAll(droppedBrokenMappings.keySet());
		droppedMappings.addAll(droppedEmptyMappings.keySet());
		return droppedMappings;
	}

	public boolean isNavigable(Entry<?> obfEntry) {
		if (obfEntry instanceof ClassEntry classEntry) {
			if (isAnonymousOrLocal(classEntry)) {
				return false;
			}
		}

		return this.jarIndex.getEntryIndex().hasEntry(obfEntry);
	}

	public boolean isRenamable(Entry<?> obfEntry) {
		if (obfEntry instanceof MethodEntry method) {
			// HACKHACK: hardcoded obfuscation format
			return method.getName().startsWith("m_");
		}
		if (obfEntry instanceof LocalVariableEntry variable) {
			return enigma.mapLocals() || variable.isArgument();
		} else if (obfEntry instanceof ClassEntry classEntry) {
			if (isAnonymousOrLocal(classEntry)) {
				return false;
			}
		}
		return this.jarIndex.getEntryIndex().hasEntry(obfEntry);
	}

	public boolean isRenamable(EntryReference<Entry<?>, Entry<?>> obfReference) {
		return obfReference.isNamed() && isRenamable(obfReference.getNameableEntry());
	}

	public boolean isObfuscated(Entry<?> entry) {
		String name = entry.getName();

		List<ObfuscationTestService> obfuscationTestServices = this.getEnigma().getServices().get(ObfuscationTestService.TYPE);
		if (!obfuscationTestServices.isEmpty()) {
			for (ObfuscationTestService service : obfuscationTestServices) {
				if (service.testDeobfuscated(entry)) {
					return false;
				}
			}
		}

		List<NameProposalService> nameProposalServices = this.getEnigma().getServices().get(NameProposalService.TYPE);
		if (!nameProposalServices.isEmpty()) {
			for (NameProposalService service : nameProposalServices) {
				if (service.proposeName(entry, mapper).isPresent()) {
					return false;
				}
			}
		}

		String mappedName = mapper.deobfuscate(entry).getName();
		return mappedName == null || mappedName.isEmpty() || mappedName.equals(name);
	}

	public boolean isSynthetic(Entry<?> entry) {
		return jarIndex.getEntryIndex().hasEntry(entry) && jarIndex.getEntryIndex().getEntryAccess(entry).isSynthetic();
	}

	public boolean isAnonymousOrLocal(ClassEntry classEntry) {
		EnclosingMethodIndex enclosingMethodIndex = jarIndex.getEnclosingMethodIndex();
		// Only local and anonymous classes may have the EnclosingMethod attribute
		return enclosingMethodIndex.hasEnclosingMethod(classEntry);
	}

	public JarExport exportRemappedJar(ProgressListener progress) {
		Collection<ClassEntry> classEntries = jarIndex.getEntryIndex().getClasses();
		ClassProvider fixingClassProvider = new ObfuscationFixClassProvider(classProvider, jarIndex);

		NameProposalService[] nameProposalServices = getEnigma().getServices().get(NameProposalService.TYPE).toArray(new NameProposalService[0]);
		Translator deobfuscator = nameProposalServices.length == 0 ? mapper.getDeobfuscator() : new ProposingTranslator(mapper, nameProposalServices);

		AtomicInteger count = new AtomicInteger();
		progress.init(classEntries.size(), I18n.translate("progress.classes.deobfuscating"));

		Map<String, ClassNode> compiled = classEntries.parallelStream()
				.map(entry -> {
					ClassEntry translatedEntry = deobfuscator.translate(entry);
					progress.step(count.getAndIncrement(), translatedEntry.toString());

					ClassNode node = fixingClassProvider.get(entry.getFullName());
					if (node != null) {
						ClassNode translatedNode = new ClassNode();
						node.accept(new TranslationClassVisitor(deobfuscator, Enigma.ASM_VERSION, translatedNode));
						return translatedNode;
					}

					return null;
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(n -> n.name, Functions.identity()));

		return new JarExport(mapper, compiled);
	}

	public static final class JarExport {
		private final EntryRemapper mapper;
		private final Map<String, ClassNode> compiled;

		JarExport(EntryRemapper mapper, Map<String, ClassNode> compiled) {
			this.mapper = mapper;
			this.compiled = compiled;
		}

		public void write(Path path, ProgressListener progress) throws IOException {
			progress.init(this.compiled.size(), I18n.translate("progress.jar.writing"));

			try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path))) {
				AtomicInteger count = new AtomicInteger();

				for (ClassNode node : this.compiled.values()) {
					progress.step(count.getAndIncrement(), node.name);

					String entryName = node.name.replace('.', '/') + ".class";

					ClassWriter writer = new ClassWriter(0);
					node.accept(writer);

					out.putNextEntry(new JarEntry(entryName));
					out.write(writer.toByteArray());
					out.closeEntry();
				}
			}
		}

		public SourceExport decompile(ProgressListener progress, DecompilerService decompilerService, DecompileErrorStrategy errorStrategy) {
			List<ClassSource> decompiled = this.decompileStream(progress, decompilerService, errorStrategy).toList();
			return new SourceExport(decompiled);
		}

		public Stream<ClassSource> decompileStream(ProgressListener progress, DecompilerService decompilerService, DecompileErrorStrategy errorStrategy) {
			Collection<ClassNode> classes = this.compiled.values().stream()
					.filter(classNode -> classNode.name.indexOf('$') == -1)
					.toList();

			progress.init(classes.size(), I18n.translate("progress.classes.decompiling"));

			//create a common instance outside the loop as mappings shouldn't be changing while this is happening
			Decompiler decompiler = decompilerService.create(ClassProvider.fromMap(compiled), new SourceSettings(false, false));

			AtomicInteger count = new AtomicInteger();

			return classes.parallelStream()
					.map(translatedNode -> {
						progress.step(count.getAndIncrement(), translatedNode.name);

						String source = null;
						try {
							source = decompileClass(translatedNode, decompiler);
						} catch (Throwable throwable) {
							switch (errorStrategy) {
								case PROPAGATE: throw throwable;
								case IGNORE: break;
								case TRACE_AS_SOURCE: {
									StringWriter writer = new StringWriter();
									throwable.printStackTrace(new PrintWriter(writer));
									source = writer.toString();
									break;
								}
							}
						}

						if (source == null) {
							return null;
						}

						return new ClassSource(translatedNode.name, source);
					})
					.filter(Objects::nonNull);
		}

		private String decompileClass(ClassNode translatedNode, Decompiler decompiler) {
			return decompiler.getSource(translatedNode.name, mapper).asString();
		}
	}

	public static final class SourceExport {
		public final Collection<ClassSource> decompiled;

		SourceExport(Collection<ClassSource> decompiled) {
			this.decompiled = decompiled;
		}

		public void write(Path path, ProgressListener progress) throws IOException {
			progress.init(decompiled.size(), I18n.translate("progress.sources.writing"));

			int count = 0;
			for (ClassSource source : decompiled) {
				progress.step(count++, source.name);

				Path sourcePath = source.resolvePath(path);
				source.writeTo(sourcePath);
			}
		}
	}

	public static class ClassSource {
		public final String name;
		public final String source;

		ClassSource(String name, String source) {
			this.name = name;
			this.source = source;
		}

		public void writeTo(Path path) throws IOException {
			Files.createDirectories(path.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(path)) {
				writer.write(source);
			}
		}

		public Path resolvePath(Path root) {
			return root.resolve(name.replace('.', '/') + ".java");
		}
	}

	public enum DecompileErrorStrategy {
		PROPAGATE,
		TRACE_AS_SOURCE,
		IGNORE
	}
}
