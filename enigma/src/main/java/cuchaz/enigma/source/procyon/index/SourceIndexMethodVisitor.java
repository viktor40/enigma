package cuchaz.enigma.source.procyon.index;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.strobel.assembler.metadata.*;
import com.strobel.decompiler.ast.Variable;
import com.strobel.decompiler.languages.TextLocation;
import com.strobel.decompiler.languages.java.ast.*;
import cuchaz.enigma.source.SourceIndex;
import cuchaz.enigma.source.procyon.EntryParser;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.*;

import java.lang.Error;
import java.util.HashMap;
import java.util.Map;

public class SourceIndexMethodVisitor extends SourceIndexVisitor {
	private final MethodDefEntry methodEntry;

	private final Multimap<String, Identifier> unmatchedIdentifier = HashMultimap.create();
	private final Map<String, Entry<?>> identifierEntryCache = new HashMap<>();

	public SourceIndexMethodVisitor(MethodDefEntry methodEntry) {
		this.methodEntry = methodEntry;
	}

	@Override
	public Void visitInvocationExpression(InvocationExpression node, SourceIndex index) {
		MemberReference ref = node.getUserData(Keys.MEMBER_REFERENCE);

		if (ref != null) {
			// get the behavior entry
			ClassEntry classEntry = new ClassEntry(ref.getDeclaringType().getInternalName());
			MethodEntry methodEntry = null;
			if (ref instanceof MethodReference) {
				methodEntry = new MethodEntry(classEntry, ref.getName(), new MethodDescriptor(ref.getErasedSignature()));
			}
			if (methodEntry != null) {
				// get the node for the token
				AstNode tokenNode = null;
				if (node.getTarget() instanceof MemberReferenceExpression) {
					tokenNode = ((MemberReferenceExpression) node.getTarget()).getMemberNameToken();
				} else if (node.getTarget() instanceof SuperReferenceExpression) {
					tokenNode = node.getTarget();
				} else if (node.getTarget() instanceof ThisReferenceExpression) {
					tokenNode = node.getTarget();
				}
				if (tokenNode != null) {
					index.addReference(TokenFactory.createToken(index, tokenNode), methodEntry, this.methodEntry);
				}
			}
		}

		// Check for identifier
		node.getArguments().stream().filter(expression -> expression instanceof IdentifierExpression)
				.forEach(expression -> this.checkIdentifier((IdentifierExpression) expression, index));
		return this.visitChildren(node, index);
	}

	@Override
	public Void visitMemberReferenceExpression(MemberReferenceExpression node, SourceIndex index) {
		MemberReference ref = node.getUserData(Keys.MEMBER_REFERENCE);
		if (ref instanceof FieldReference) {
			// make sure this is actually a field
			String erasedSignature = ref.getErasedSignature();
			if (erasedSignature.indexOf('(') >= 0) {
				throw new Error("Expected a field here! got " + ref);
			}

			ClassEntry classEntry = new ClassEntry(ref.getDeclaringType().getInternalName());
			FieldEntry fieldEntry = new FieldEntry(classEntry, ref.getName(), new TypeDescriptor(erasedSignature));
			index.addReference(TokenFactory.createToken(index, node.getMemberNameToken()), fieldEntry, this.methodEntry);
		}

		return this.visitChildren(node, index);
	}

	@Override
	public Void visitSimpleType(SimpleType node, SourceIndex index) {
		TypeReference ref = node.getUserData(Keys.TYPE_REFERENCE);
		if (node.getIdentifierToken().getStartLocation() != TextLocation.EMPTY) {
			ClassEntry classEntry = new ClassEntry(ref.getInternalName());
			index.addReference(TokenFactory.createToken(index, node.getIdentifierToken()), classEntry, this.methodEntry);
		}

		return this.visitChildren(node, index);
	}

	@Override
	public Void visitParameterDeclaration(ParameterDeclaration node, SourceIndex index) {
		ParameterDefinition def = node.getUserData(Keys.PARAMETER_DEFINITION);
		int parameterIndex = def.getSlot();

		if (parameterIndex >= 0) {
			MethodDefEntry ownerMethod = this.methodEntry;
			if (def.getMethod() instanceof MethodDefinition) {
				ownerMethod = EntryParser.parse((MethodDefinition) def.getMethod());
			}

			TypeDescriptor parameterType = EntryParser.parseTypeDescriptor(def.getParameterType());
			LocalVariableDefEntry localVariableEntry = new LocalVariableDefEntry(ownerMethod, parameterIndex, node.getName(), true, parameterType, null);
			Identifier identifier = node.getNameToken();
			// cache the argument entry and the identifier
			this.identifierEntryCache.put(identifier.getName(), localVariableEntry);
			index.addDeclaration(TokenFactory.createToken(index, identifier), localVariableEntry);
		}

		return this.visitChildren(node, index);
	}

	@Override
	public Void visitIdentifierExpression(IdentifierExpression node, SourceIndex index) {
		MemberReference ref = node.getUserData(Keys.MEMBER_REFERENCE);
		if (ref != null) {
			ClassEntry classEntry = new ClassEntry(ref.getDeclaringType().getInternalName());
			FieldEntry fieldEntry = new FieldEntry(classEntry, ref.getName(), new TypeDescriptor(ref.getErasedSignature()));
			index.addReference(TokenFactory.createToken(index, node.getIdentifierToken()), fieldEntry, this.methodEntry);
		} else
			this.checkIdentifier(node, index);
		return this.visitChildren(node, index);
	}

	private void checkIdentifier(IdentifierExpression node, SourceIndex index) {
		if (this.identifierEntryCache.containsKey(node.getIdentifier())) // If it's in the argument cache, create a token!
			index.addDeclaration(TokenFactory.createToken(index, node.getIdentifierToken()), this.identifierEntryCache.get(node.getIdentifier()));
		else
			this.unmatchedIdentifier.put(node.getIdentifier(), node.getIdentifierToken()); // Not matched actually, put it!
	}

	private void addDeclarationToUnmatched(String key, SourceIndex index) {
		Entry<?> entry = this.identifierEntryCache.get(key);

		// This cannot happened in theory
		if (entry == null)
			return;
		for (Identifier identifier : this.unmatchedIdentifier.get(key))
			index.addDeclaration(TokenFactory.createToken(index, identifier), entry);
		this.unmatchedIdentifier.removeAll(key);
	}

	@Override
	public Void visitObjectCreationExpression(ObjectCreationExpression node, SourceIndex index) {
		MemberReference ref = node.getUserData(Keys.MEMBER_REFERENCE);
		if (ref != null && node.getType() instanceof SimpleType simpleTypeNode) {
			ClassEntry classEntry = new ClassEntry(ref.getDeclaringType().getInternalName());
			MethodEntry constructorEntry = new MethodEntry(classEntry, "<init>", new MethodDescriptor(ref.getErasedSignature()));
			index.addReference(TokenFactory.createToken(index, simpleTypeNode.getIdentifierToken()), constructorEntry, this.methodEntry);
		}

		return this.visitChildren(node, index);
	}

	@Override
	public Void visitVariableDeclaration(VariableDeclarationStatement node, SourceIndex index) {
		AstNodeCollection<VariableInitializer> variables = node.getVariables();

		// Single assignation
		if (variables.size() == 1) {
			VariableInitializer initializer = variables.firstOrNullObject();
			if (initializer != null && node.getType() instanceof SimpleType) {
				Identifier identifier = initializer.getNameToken();
				Variable variable = initializer.getUserData(Keys.VARIABLE);
				if (variable != null) {
					VariableDefinition originalVariable = variable.getOriginalVariable();
					if (originalVariable != null) {
						int variableIndex = originalVariable.getSlot();
						if (variableIndex >= 0) {
							MethodDefEntry ownerMethod = EntryParser.parse(originalVariable.getDeclaringMethod());
							TypeDescriptor variableType = EntryParser.parseTypeDescriptor(originalVariable.getVariableType());
							LocalVariableDefEntry localVariableEntry = new LocalVariableDefEntry(ownerMethod, variableIndex, initializer.getName(), false, variableType, null);
							this.identifierEntryCache.put(identifier.getName(), localVariableEntry);
							this.addDeclarationToUnmatched(identifier.getName(), index);
							index.addDeclaration(TokenFactory.createToken(index, identifier), localVariableEntry);
						}
					}
				}
			}
		}
		return this.visitChildren(node, index);
	}

	@Override
	public Void visitMethodGroupExpression(MethodGroupExpression node, SourceIndex index) {
		MemberReference ref = node.getUserData(Keys.MEMBER_REFERENCE);

		if (ref instanceof MethodReference) {
			// get the behavior entry
			ClassEntry classEntry = new ClassEntry(ref.getDeclaringType().getInternalName());
			MethodEntry methodEntry = new MethodEntry(classEntry, ref.getName(), new MethodDescriptor(ref.getErasedSignature()));

			// get the node for the token
			AstNode methodNameToken = node.getMethodNameToken();
			AstNode targetToken = node.getTarget();

			if (methodNameToken != null) {
				index.addReference(TokenFactory.createToken(index, methodNameToken), methodEntry, this.methodEntry);
			}

			if (targetToken != null && !(targetToken instanceof ThisReferenceExpression)) {
				index.addReference(TokenFactory.createToken(index, targetToken), methodEntry.getParent(), this.methodEntry);
			}
		}

		return this.visitChildren(node, index);
	}
}
