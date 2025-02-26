package cuchaz.enigma.analysis;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.analysis.index.BridgeMethodIndex;
import cuchaz.enigma.api.EnigmaPlugin;
import cuchaz.enigma.api.EnigmaPluginContext;
import cuchaz.enigma.api.service.JarIndexerService;
import cuchaz.enigma.api.service.NameProposalService;
import cuchaz.enigma.source.DecompilerService;
import cuchaz.enigma.source.Decompilers;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.translation.representation.entry.ParentedEntry;
import org.jetbrains.java.decompiler.util.Pair;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BuiltinPlugin implements EnigmaPlugin {
	@Override
	public void init(EnigmaPluginContext ctx) {
		this.registerEnumNamingService(ctx);
		this.registerSpecializedMethodNamingService(ctx);
		this.registerDecompilerServices(ctx);
	}

	private void registerEnumNamingService(EnigmaPluginContext ctx) {
		final Map<Entry<?>, String> names = new HashMap<>();
		final EnumFieldNameFindingVisitor visitor = new EnumFieldNameFindingVisitor(names);

		ctx.registerService("enigma:enum_initializer_indexer", JarIndexerService.TYPE, ctx1 -> JarIndexerService.fromVisitor(visitor));
		ctx.registerService("enigma:enum_name_proposer", NameProposalService.TYPE, ctx1 -> (obfEntry, remapper) -> Optional.ofNullable(names.get(obfEntry)));
	}

	private void registerSpecializedMethodNamingService(EnigmaPluginContext ctx) {
		ctx.registerService("enigma:specialized_method_name_proposer", NameProposalService.TYPE, ctx1 -> (obfEntry, remapper) -> {
			BridgeMethodIndex bridgeMethodIndex = remapper.getJarIndex().getBridgeMethodIndex();
			if (obfEntry instanceof MethodEntry obfMethod) {
				if (bridgeMethodIndex.isSpecializedMethod(obfMethod)) {
					return Optional.ofNullable(bridgeMethodIndex.getBridgeFromSpecialized(obfMethod)).map(ParentedEntry::getName);
				} else if (bridgeMethodIndex.isBridgeMethod(obfMethod)) {
					// IndexEntryResolver#resolveEntry can return the bridge method, so we can just use the name
					return Optional.of(obfEntry.getName());
				}
			}

			return Optional.empty();
		});
	}

	private void registerDecompilerServices(EnigmaPluginContext ctx) {
		ctx.registerService("enigma:quiltflower", DecompilerService.TYPE, ctx1 -> Decompilers.QUILTFLOWER);
		ctx.registerService("enigma:procyon", DecompilerService.TYPE, ctx1 -> Decompilers.PROCYON);
		ctx.registerService("enigma:cfr", DecompilerService.TYPE, ctx1 -> Decompilers.CFR);
		ctx.registerService("enigma:bytecode", DecompilerService.TYPE, ctx1 -> Decompilers.BYTECODE);
	}

	private static final class EnumFieldNameFindingVisitor extends ClassVisitor {
		private ClassEntry clazz;
		private String className;
		private final Map<Entry<?>, String> mappings;
		private final Set<org.jetbrains.java.decompiler.util.Pair<String, String>> enumFields = new HashSet<>();
		private final List<MethodNode> classInits = new ArrayList<>();

		EnumFieldNameFindingVisitor(Map<Entry<?>, String> mappings) {
			super(Enigma.ASM_VERSION);
			this.mappings = mappings;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			super.visit(version, access, name, signature, superName, interfaces);
			this.className = name;
			this.clazz = new ClassEntry(name);
			this.enumFields.clear();
			this.classInits.clear();
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			if ((access & Opcodes.ACC_ENUM) != 0
					&& !this.enumFields.add(Pair.of(name, descriptor))) {
				throw new IllegalArgumentException("Found two enum fields with the same name \"" + name + "\" and desc \"" + descriptor + "\"!");
			}

			return super.visitField(access, name, descriptor, signature, value);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			if ("<clinit>".equals(name)) {
				MethodNode node = new MethodNode(this.api, access, name, descriptor, signature, exceptions);
				this.classInits.add(node);
				return node;
			}
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}

		@Override
		public void visitEnd() {
			super.visitEnd();
			try {
				this.collectResults();
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		private void collectResults() throws Exception {
			String owner = this.className;
			Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());

			for (MethodNode mn : this.classInits) {
				Frame<SourceValue>[] frames = analyzer.analyze(this.className, mn);

				InsnList instrs = mn.instructions;
				for (int i = 1; i < instrs.size(); i++) {
					AbstractInsnNode instr1 = instrs.get(i - 1);
					AbstractInsnNode instr2 = instrs.get(i);
					String s = null;

					if (instr2.getOpcode() == Opcodes.PUTSTATIC
							&& ((FieldInsnNode) instr2).owner.equals(owner)
							&& this.enumFields.contains(Pair.of(((FieldInsnNode) instr2).name, ((FieldInsnNode) instr2).desc))
							&& instr1.getOpcode() == Opcodes.INVOKESPECIAL
							&& "<init>".equals(((MethodInsnNode) instr1).name)) {
						for (int j = 0; j < frames[i - 1].getStackSize(); j++) {
							SourceValue sv = frames[i - 1].getStack(j);
							for (AbstractInsnNode ci : sv.insns) {
								if (ci instanceof LdcInsnNode insnNode && insnNode.cst instanceof String && s == null) {
									s = (String) (insnNode.cst);
								}
							}
						}
					}

					if (s != null) {
						this.mappings.put(new FieldEntry(this.clazz, ((FieldInsnNode) instr2).name, new TypeDescriptor(((FieldInsnNode) instr2).desc)), s);
					}
					// report otherwise?
				}
			}
		}
	}
}
