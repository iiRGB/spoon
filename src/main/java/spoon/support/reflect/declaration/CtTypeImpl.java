/**
 * Copyright (C) 2006-2017 INRIA and contributors
 * Spoon - http://spoon.gforge.inria.fr/
 *
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify
 * and/or redistribute the software under the terms of the CeCILL-C license as
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */
package spoon.support.reflect.declaration;

import spoon.SpoonException;
import spoon.reflect.annotations.MetamodelPropertyField;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtAnnotationType;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtFormalTypeDeclarer;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtModifiable;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtShadowable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.declaration.ParentNotInitializedException;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtIntersectionTypeReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.EarlyTerminatingScanner;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.chain.CtConsumer;
import spoon.reflect.visitor.filter.AllTypeMembersFunction;
import spoon.reflect.visitor.filter.NameFilter;
import spoon.reflect.visitor.filter.ReferenceTypeFilter;
import spoon.support.UnsettableProperty;
import spoon.support.comparator.CtLineElementComparator;
import spoon.support.compiler.SnippetCompilationHelper;
import spoon.support.util.QualifiedNameBasedSortedSet;
import spoon.support.util.SignatureBasedSortedSet;
import spoon.support.util.SortedList;
import spoon.support.visitor.ClassTypingContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static spoon.reflect.ModelElementContainerDefaultCapacities.TYPE_TYPE_PARAMETERS_CONTAINER_DEFAULT_CAPACITY;
import static spoon.reflect.path.CtRole.CONSTRUCTOR;
import static spoon.reflect.path.CtRole.EXECUTABLE;
import static spoon.reflect.path.CtRole.FIELD;
import static spoon.reflect.path.CtRole.INTERFACE;
import static spoon.reflect.path.CtRole.IS_SHADOW;
import static spoon.reflect.path.CtRole.MODIFIER;
import static spoon.reflect.path.CtRole.NESTED_TYPE;
import static spoon.reflect.path.CtRole.TYPE_PARAMETER;

/**
 * The implementation for {@link spoon.reflect.declaration.CtType}.
 */
public abstract class CtTypeImpl<T> extends CtNamedElementImpl implements CtType<T> {

	private static final long serialVersionUID = 1L;

	@MetamodelPropertyField(role = CtRole.TYPE_PARAMETER)
	List<CtTypeParameter> formalCtTypeParameters = emptyList();

	@MetamodelPropertyField(role = CtRole.INTERFACE)
	Set<CtTypeReference<?>> interfaces = emptySet();

	@MetamodelPropertyField(role = CtRole.MODIFIER)
	Set<ModifierKind> modifiers = emptySet();

	@MetamodelPropertyField(role = {CtRole.FIELD, CtRole.EXECUTABLE, CtRole.NESTED_TYPE})
	List<CtTypeMember> typeMembers = emptyList();

	public CtTypeImpl() {
		super();
	}

	@Override
	public List<CtTypeMember> getTypeMembers() {
		return Collections.unmodifiableList(typeMembers);
	}

	@Override
	public <C extends CtType<T>> C addTypeMember(CtTypeMember member) {
		if (member == null) {
			return (C) this;
		}
		return addTypeMemberAt(typeMembers.size(), member);
	}

	@Override
	public <C extends CtType<T>> C addTypeMemberAt(int position, CtTypeMember member) {
		if (member == null) {
			return (C) this;
		}
		if (this.typeMembers == CtElementImpl.<CtTypeMember>emptyList()) {
			this.typeMembers = new SortedList<>(new CtLineElementComparator());
		}
		if (!this.typeMembers.contains(member)) {
			member.setParent(this);
			CtRole role;
			if (member instanceof CtMethod) {
				role = EXECUTABLE;
			} else if (member instanceof CtConstructor) {
				role = CONSTRUCTOR;
			} else if (member instanceof CtField) {
				role = FIELD;
			} else {
				role = NESTED_TYPE;
			}
			getFactory().getEnvironment().getModelChangeListener().onListAdd(this, role, this.typeMembers, position, member);
			this.typeMembers.add(position, member);
		}
		return (C) this;
	}

	@Override
	public boolean removeTypeMember(CtTypeMember member) {
		CtRole role;
		if (member instanceof CtMethod) {
			role = EXECUTABLE;
		} else if (member instanceof CtConstructor) {
			role = CONSTRUCTOR;
		} else if (member instanceof CtField) {
			role = FIELD;
		} else {
			role = NESTED_TYPE;
		}
		if (typeMembers.size() == 1) {
			if (typeMembers.contains(member)) {
				getFactory().getEnvironment().getModelChangeListener().onListDelete(this, role, this.typeMembers, this.typeMembers.indexOf(member), member);
				typeMembers = emptyList();
				return true;
			} else {
				return false;
			}
		}
		if (typeMembers.contains(member)) {
			getFactory().getEnvironment().getModelChangeListener().onListDelete(this, role, this.typeMembers, this.typeMembers.indexOf(member), member);
			return typeMembers.remove(member);
		}
		return false;
	}

	@Override
	public <C extends CtType<T>> C setTypeMembers(List<CtTypeMember> members) {
		for (CtTypeMember typeMember : new ArrayList<>(typeMembers)) {
			removeTypeMember(typeMember);
		}
		if (members == null || members.isEmpty()) {
			this.typeMembers = emptyList();
			return (C) this;
		}
		typeMembers.clear();
		for (CtTypeMember typeMember : members) {
			addTypeMember(typeMember);
		}
		return (C) this;
	}

	@Override
	public <F, C extends CtType<T>> C addFieldAtTop(CtField<F> field) {
		return addTypeMemberAt(0, field);
	}

	@Override
	public <F, C extends CtType<T>> C addField(CtField<F> field) {
		return addTypeMember(field);
	}

	@Override
	public <F, C extends CtType<T>> C addField(int index, CtField<F> field) {
		return addTypeMemberAt(index, field);
	}

	@Override
	public <C extends CtType<T>> C setFields(List<CtField<?>> fields) {
		List<CtField<?>> oldFields = getFields();
		if (fields == null || fields.isEmpty()) {
			this.typeMembers.removeAll(oldFields);
			return (C) this;
		}
		getFactory().getEnvironment().getModelChangeListener().onListDelete(this, FIELD, this.typeMembers, new ArrayList<>(oldFields));
		typeMembers.removeAll(oldFields);
		for (CtField<?> field : fields) {
			addField(field);
		}
		return (C) this;
	}

	@Override
	public <F> boolean removeField(CtField<F> field) {
		return removeTypeMember(field);
	}

	@Override
	public CtField<?> getField(String name) {
		for (CtTypeMember typeMember : typeMembers) {
			if (typeMember instanceof CtField && ((CtField) typeMember).getSimpleName().equals(name)) {
				return (CtField<?>) typeMember;
			}
		}
		return null;
	}

	@Override
	public CtFieldReference<?> getDeclaredField(String name) {
		CtField<?> field = getField(name);
		return field != null ? getFactory().Field().createReference(field) : null;
	}

	@Override
	public CtFieldReference<?> getDeclaredOrInheritedField(String fieldName) {
		CtField<?> field = map(new AllTypeMembersFunction(CtField.class)).select(new NameFilter<>(fieldName)).first();
		return field == null ? null : field.getReference();
	}


	@Override
	public List<CtField<?>> getFields() {
		List<CtField<?>> fields = new ArrayList<>();
		for (CtTypeMember typeMember : typeMembers) {
			if (typeMember instanceof CtField) {
				fields.add((CtField<?>) typeMember);
			}
		}
		return fields;
	}

	@Override
	public <N, C extends CtType<T>> C addNestedType(CtType<N> nestedType) {
		return addTypeMember(nestedType);
	}

	@Override
	public <N> boolean removeNestedType(CtType<N> nestedType) {
		return removeTypeMember(nestedType);
	}

	@Override
	public <C extends CtType<T>> C setNestedTypes(Set<CtType<?>> nestedTypes) {
		Set<CtType<?>> oldNestedTypes = getNestedTypes();
		getFactory().getEnvironment().getModelChangeListener().onListDelete(this, NESTED_TYPE, typeMembers, oldNestedTypes);
		if (nestedTypes == null || nestedTypes.isEmpty()) {
			this.typeMembers.removeAll(oldNestedTypes);
			return (C) this;
		}
		typeMembers.removeAll(oldNestedTypes);
		for (CtType<?> nestedType : nestedTypes) {
			addNestedType(nestedType);
		}
		return (C) this;
	}

	@Override
	public Set<CtTypeReference<?>> getUsedTypes(boolean includeSamePackage) {
		Set<CtTypeReference<?>> typeRefs = new QualifiedNameBasedSortedSet<>();
		for (CtTypeReference<?> typeRef : Query.getReferences(this, new ReferenceTypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
			if (isValidTypeReference(typeRef) && shouldIncludeSamePackage(includeSamePackage, typeRef)) {
				typeRefs.add(typeRef);
			}
		}
		return typeRefs;
	}

	private boolean shouldIncludeSamePackage(boolean includeSamePackage, CtTypeReference<?> typeRef) {
		return includeSamePackage || (getPackage() != null && !getPackageReference(typeRef).equals(getPackage().getReference()));
	}

	private boolean isValidTypeReference(CtTypeReference<?> typeRef) {
		return !(isFromJavaLang(typeRef) || typeRef.isPrimitive() || typeRef instanceof CtArrayTypeReference || CtTypeReference.NULL_TYPE_NAME.equals(typeRef.toString()));
	}

	private boolean isFromJavaLang(CtTypeReference<?> typeRef) {
		return typeRef.getPackage() != null && "java.lang".equals(typeRef.getPackage().toString());
	}

	/**
	 * Return the package reference for the corresponding type reference. For
	 * inner type, return the package reference of the top-most enclosing type.
	 * This helper method is meant to deal with package references that are
	 * <code>null</code> for inner types.
	 *
	 * @param tref the type reference
	 * @return the corresponding package reference
	 * @see CtTypeReference#getPackage()
	 * @since 4.0
	 */
	private static CtPackageReference getPackageReference(CtTypeReference<?> tref) {
		CtPackageReference pref = tref.getPackage();
		while (pref == null) {
			tref = tref.getDeclaringType();
			pref = tref.getPackage();
		}
		return pref;
	}

	@Override
	public Class<T> getActualClass() {
		return getFactory().Type().createReference(this).getActualClass();
	}

	@Override
	public CtType<?> getDeclaringType() {
		try {
			return getParent(CtType.class);
		} catch (ParentNotInitializedException ex) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> CtType<T> getTopLevelType() {
		CtType<?> top = this;

		while (true) {
			CtType<?> nextTop = top.getDeclaringType();
			if (nextTop == null) {
				return (CtType<T>) top;
			}
			top = nextTop;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <N extends CtType<?>> N getNestedType(final String name) {
		class NestedTypeScanner extends EarlyTerminatingScanner<CtType<?>> {

			private boolean checkType(CtType<?> type) {
				if (type.getSimpleName().equals(name) && CtTypeImpl.this.equals(type.getDeclaringType())) {
					setResult(type);
					terminate();
					return true;
				}
				return false;
			}

			@Override
			public <U> void visitCtClass(spoon.reflect.declaration.CtClass<U> ctClass) {
				if (!checkType(ctClass)) {
					final List<CtTypeMember> typeMembers = new ArrayList<>();
					for (CtTypeMember typeMember : ctClass.getTypeMembers()) {
						if (typeMember instanceof CtType || typeMember instanceof CtConstructor || typeMember instanceof CtMethod) {
							typeMembers.add(typeMember);
						}
					}
					scan(typeMembers);
				}
			}

			@Override
			public <U> void visitCtInterface(spoon.reflect.declaration.CtInterface<U> intrface) {
				if (!checkType(intrface)) {
					final List<CtTypeMember> typeMembers = new ArrayList<>();
					for (CtTypeMember typeMember : intrface.getTypeMembers()) {
						if (typeMember instanceof CtType || typeMember instanceof CtMethod) {
							typeMembers.add(typeMember);
						}
					}
					scan(typeMembers);
				}
			}

			@Override
			public <U extends java.lang.Enum<?>> void visitCtEnum(spoon.reflect.declaration.CtEnum<U> ctEnum) {
				if (!checkType(ctEnum)) {
					final List<CtTypeMember> typeMembers = new ArrayList<>();
					for (CtTypeMember typeMember : ctEnum.getTypeMembers()) {
						if (typeMember instanceof CtType || typeMember instanceof CtConstructor || typeMember instanceof CtMethod) {
							typeMembers.add(typeMember);
						}
					}
					scan(typeMembers);
				}
			}

			@Override
			public <A extends Annotation> void visitCtAnnotationType(CtAnnotationType<A> annotationType) {
				if (!checkType(annotationType)) {
					scan(annotationType.getNestedTypes());
				}
			}
		}
		NestedTypeScanner scanner = new NestedTypeScanner();
		scanner.scan(this);
		return (N) scanner.getResult();
	}

	@Override
	public Set<CtType<?>> getNestedTypes() {
		Set<CtType<?>> nestedTypes = new QualifiedNameBasedSortedSet<>();
		for (CtTypeMember typeMember : typeMembers) {
			if (typeMember instanceof CtType) {
				nestedTypes.add((CtType<?>) typeMember);
			}
		}
		return nestedTypes;
	}

	@Override
	public CtPackage getPackage() {
		if (parent instanceof CtPackage) {
			return (CtPackage) getParent();
		} else if (parent instanceof CtType) {
			return ((CtType<?>) parent).getPackage();
		} else {
			return null;
		}
	}

	@Override
	public CtTypeReference<T> getReference() {
		return getFactory().Type().createReference(this);
	}

	@Override
	public boolean isTopLevel() {
		return (getDeclaringType() == null) && (getPackage() != null);
	}

	@Override
	public void compileAndReplaceSnippets() {
		SnippetCompilationHelper.compileAndReplaceSnippetsIn(this);
	}

	@Override
	public Set<ModifierKind> getModifiers() {
		return modifiers;
	}

	@Override
	public boolean hasModifier(ModifierKind modifier) {
		return getModifiers().contains(modifier);
	}

	@Override
	public <C extends CtModifiable> C setModifiers(Set<ModifierKind> modifiers) {
		if (modifiers.size() > 0) {
			getFactory().getEnvironment().getModelChangeListener().onSetDeleteAll(this, MODIFIER, this.modifiers, new HashSet<>(this.modifiers));
			this.modifiers.clear();
			for (ModifierKind modifier : modifiers) {
				addModifier(modifier);
			}
		}
		return (C) this;
	}

	@Override
	public <C extends CtModifiable> C addModifier(ModifierKind modifier) {
		if (modifiers == CtElementImpl.<ModifierKind>emptySet()) {
			this.modifiers = EnumSet.of(modifier);
		}
		getFactory().getEnvironment().getModelChangeListener().onSetAdd(this, MODIFIER, this.modifiers, modifier);
		modifiers.add(modifier);
		return (C) this;
	}

	@Override
	public boolean removeModifier(ModifierKind modifier) {
		if (modifiers == CtElementImpl.<ModifierKind>emptySet()) {
			return false;
		}
		getFactory().getEnvironment().getModelChangeListener().onSetDelete(this, MODIFIER, modifiers, modifier);
		return modifiers.remove(modifier);
	}

	@Override
	public <C extends CtModifiable> C setVisibility(ModifierKind visibility) {
		if (modifiers == CtElementImpl.<ModifierKind>emptySet()) {
			this.modifiers = EnumSet.noneOf(ModifierKind.class);
		}
		removeModifier(ModifierKind.PUBLIC);
		removeModifier(ModifierKind.PROTECTED);
		removeModifier(ModifierKind.PRIVATE);
		addModifier(visibility);
		return (C) this;
	}

	@Override
	public ModifierKind getVisibility() {
		if (getModifiers().contains(ModifierKind.PUBLIC)) {
			return ModifierKind.PUBLIC;
		}
		if (getModifiers().contains(ModifierKind.PROTECTED)) {
			return ModifierKind.PROTECTED;
		}
		if (getModifiers().contains(ModifierKind.PRIVATE)) {
			return ModifierKind.PRIVATE;
		}
		return null;
	}

	@Override
	public boolean isPrimitive() {
		return false;
	}

	@Override
	public boolean isAnonymous() {
		return false;
	}

	@Override
	public boolean isLocalType() {
		return isParentInitialized() && getParent() instanceof CtBlock;
	}

	@Override
	public CtTypeReference<?> getSuperclass() {
		// overridden in subclasses.
		return null;
	}

	@Override
	public boolean isClass() {
		return false;
	}

	@Override
	public boolean isInterface() {
		return false;
	}

	@Override
	public boolean isAnnotationType() {
		return false;
	}

	@Override
	public boolean isEnum() {
		return false;
	}

	@Override
	public boolean isGenerics() {
		return false;
	}

	@Override
	public List<CtFieldReference<?>> getAllFields() {
		final List<CtFieldReference<?>> fields = new ArrayList<>();
		map(new AllTypeMembersFunction(CtField.class)).forEach(new CtConsumer<CtField<?>>() {
			@Override
			public void accept(CtField<?> field) {
				fields.add(field.getReference());
			}
		});
		return fields;
	}

	@Override
	public List<CtFieldReference<?>> getDeclaredFields() {
		if (typeMembers.isEmpty()) {
			return Collections.emptyList();
		}
		final List<CtFieldReference<?>> fields = new ArrayList<>(typeMembers.size());
		for (CtTypeMember typeMember : typeMembers) {
			if (typeMember instanceof CtField) {
				fields.add(((CtField) typeMember).getReference());
			}
		}
		return fields;
	}

	@Override
	public <M, C extends CtType<T>> C addMethod(CtMethod<M> method) {
		if (method != null) {
			for (CtTypeMember typeMember : new ArrayList<>(typeMembers)) {
				if (!(typeMember instanceof CtMethod)) {
					continue;
				}
				CtMethod<?> m = (CtMethod<?>) typeMember;
				if (m.getSignature().equals(method.getSignature())) {
					// replace old method by new one (based on signature and not equality)
					// we have to do it by hand
					removeTypeMember(m);
				} else {
					// checking contract signature implies equal
					if (!factory.getEnvironment().checksAreSkipped() && m.equals(method)) {
						throw new AssertionError("violation of core contract! different signature but same equal");
					}
				}
			}
		}
		return addTypeMember(method);
	}

	@Override
	public <M> boolean removeMethod(CtMethod<M> method) {
		return removeTypeMember(method);
	}

	@Override
	public <S, C extends CtType<T>> C addSuperInterface(CtTypeReference<S> interfac) {
		if (interfac == null) {
			return (C) this;
		}
		if (interfaces == CtElementImpl.<CtTypeReference<?>>emptySet()) {
			interfaces = new QualifiedNameBasedSortedSet<>();
		}
		interfac.setParent(this);
		getFactory().getEnvironment().getModelChangeListener().onSetAdd(this, INTERFACE, this.interfaces, interfac);
		interfaces.add(interfac);
		return (C) this;
	}

	@Override
	public <S> boolean removeSuperInterface(CtTypeReference<S> interfac) {
		getFactory().getEnvironment().getModelChangeListener().onSetDelete(this, INTERFACE, interfaces, interfac);
		if (interfaces == CtElementImpl.<CtTypeReference<?>>emptySet()) {
			return false;
		} else if (interfaces.size() == 1) {
			if (interfaces.contains(interfac)) {
				interfaces = CtElementImpl.<CtTypeReference<?>>emptySet();
				return true;
			} else {
				return false;
			}
		} else {
			// contains() not needed. see comment in removeMethod()
			return interfaces.contains(interfac) && interfaces.remove(interfac);
		}
	}

	@Override
	public List<CtTypeParameter> getFormalCtTypeParameters() {
		return formalCtTypeParameters;
	}

	@Override
	public <C extends CtFormalTypeDeclarer> C setFormalCtTypeParameters(List<CtTypeParameter> formalTypeParameters) {
		getFactory().getEnvironment().getModelChangeListener().onListDeleteAll(this, TYPE_PARAMETER, formalCtTypeParameters, new ArrayList<>(formalCtTypeParameters));
		if (formalTypeParameters == null || formalTypeParameters.isEmpty()) {
			this.formalCtTypeParameters = CtElementImpl.emptyList();
			return (C) this;
		}
		if (this.formalCtTypeParameters == CtElementImpl.<CtTypeParameter>emptyList()) {
			this.formalCtTypeParameters = new ArrayList<>(TYPE_TYPE_PARAMETERS_CONTAINER_DEFAULT_CAPACITY);
		}
		this.formalCtTypeParameters.clear();
		for (CtTypeParameter formalTypeParameter : formalTypeParameters) {
			addFormalCtTypeParameter(formalTypeParameter);
		}
		return (C) this;
	}

	@Override
	public <C extends CtFormalTypeDeclarer> C addFormalCtTypeParameter(CtTypeParameter formalTypeParameter) {
		if (formalTypeParameter == null) {
			return (C) this;
		}
		if (formalCtTypeParameters == CtElementImpl.<CtTypeParameter>emptyList()) {
			formalCtTypeParameters = new ArrayList<>(TYPE_TYPE_PARAMETERS_CONTAINER_DEFAULT_CAPACITY);
		}
		formalTypeParameter.setParent(this);
		getFactory().getEnvironment().getModelChangeListener().onListAdd(this, TYPE_PARAMETER, this.formalCtTypeParameters, formalTypeParameter);
		formalCtTypeParameters.add(formalTypeParameter);
		return (C) this;
	}

	@Override
	public boolean removeFormalCtTypeParameter(CtTypeParameter formalTypeParameter) {
		if (formalCtTypeParameters == CtElementImpl.<CtTypeParameter>emptyList()) {
			return false;
		}
		getFactory().getEnvironment().getModelChangeListener().onListDelete(this, TYPE_PARAMETER, formalCtTypeParameters, formalCtTypeParameters.indexOf(formalTypeParameter), formalTypeParameter);
		return formalCtTypeParameters.remove(formalTypeParameter);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <R> CtMethod<R> getMethod(CtTypeReference<R> returnType, String name, CtTypeReference<?>... parameterTypes) {
		for (CtTypeMember typeMember : typeMembers) {
			if (!(typeMember instanceof CtMethod)) {
				continue;
			}
			CtMethod<R> m = (CtMethod<R>) typeMember;
			if (m.getSimpleName().equals(name)) {
				if (!m.getType().equals(returnType)) {
					continue;
				}
				boolean cont = m.getParameters().size() == parameterTypes.length;
				for (int i = 0; cont && (i < m.getParameters().size()) && (i < parameterTypes.length); i++) {
					if (!m.getParameters().get(i).getType().getQualifiedName().equals(parameterTypes[i].getQualifiedName())) {
						cont = false;
					}
				}
				if (cont) {
					return m;
				}
			}
		}
		return null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <R> CtMethod<R> getMethod(String name, CtTypeReference<?>... parameterTypes) {
		if (name == null) {
			return null;
		}

		for (CtMethod<?> candidate : getMethodsByName(name)) {
			boolean cont = candidate.getParameters().size() == parameterTypes.length;
			for (int i = 0; cont && (i < candidate.getParameters().size()) && (i < parameterTypes.length); i++) {
				final CtTypeReference<?> ctParameterType = candidate.getParameters().get(i).getType();
				final CtTypeReference<?> parameterType = parameterTypes[i];
				if (parameterType instanceof CtArrayTypeReference) {
					if (ctParameterType instanceof CtArrayTypeReference) {
						if (!isSameParameter(candidate, ((CtArrayTypeReference) ctParameterType).getComponentType(), ((CtArrayTypeReference) parameterType).getComponentType())) {
							cont = false;
						} else {
							if (!(((CtArrayTypeReference) ctParameterType).getDimensionCount() == ((CtArrayTypeReference) parameterType).getDimensionCount())) {
								cont = false;
							}
						}
					} else {
						cont = false;
					}
				} else if (!isSameParameter(candidate, ctParameterType, parameterType)) {
					cont = false;
				}
			}
			if (cont) {
				return (CtMethod<R>) candidate;
			}
		}
		return null;
	}

	private boolean isSameParameter(CtMethod<?> method, CtTypeReference<?> ctParameterType, CtTypeReference<?> expectedType) {
		if (expectedType instanceof CtTypeParameterReference) {
			/*
			 * the expectedType is a generic parameter whose declaration should be searched in scope of method
			 * (not in scope of it's parent, where it can found another/wrong type parameter declaration of same name.
			 */
			CtTypeParameterReference tpr = (CtTypeParameterReference) expectedType;
			expectedType = tpr.clone();
			expectedType.setParent(method);
			if (expectedType.getDeclaration() == null) {
				return false;
			}
		}
		if (expectedType instanceof CtTypeParameterReference && ctParameterType instanceof CtTypeParameterReference) {
			// Check if Object or extended.
			if (!ctParameterType.equals(expectedType)) {
				return false;
			}
		} else if (expectedType instanceof CtTypeParameterReference) {
			if (!ctParameterType.isSubtypeOf(factory.Type().createReference(expectedType.getActualClass()))) {
				return false;
			}
		} else if (ctParameterType instanceof CtTypeParameterReference) {
			CtTypeParameter declaration = (CtTypeParameter) ctParameterType.getDeclaration();
			if (declaration.getSuperclass() instanceof CtIntersectionTypeReference) {
				for (CtTypeReference<?> ctTypeReference : declaration.getSuperclass().asCtIntersectionTypeReference().getBounds()) {
					if (ctTypeReference.equals(expectedType)) {
						return true;
					}
				}
			} else if (declaration.getSuperclass() != null) {
				return declaration.getSuperclass().equals(expectedType);
			} else {
				return getFactory().Type().objectType().equals(expectedType);
			}
		} else if (!expectedType.getQualifiedName().equals(ctParameterType.getQualifiedName())) {
			return false;
		}
		return true;
	}

	@Override
	public Set<CtMethod<?>> getMethods() {
		Set<CtMethod<?>> methods = new SignatureBasedSortedSet<>();
		for (CtTypeMember typeMember : typeMembers) {
			if (typeMember instanceof CtMethod) {
				methods.add((CtMethod<?>) typeMember);
			}
		}
		return methods;
	}

	@Override
	public Set<CtMethod<?>> getMethodsAnnotatedWith(CtTypeReference<?>... annotationTypes) {
		Set<CtMethod<?>> result = new SignatureBasedSortedSet<>();
		for (CtTypeMember typeMember : typeMembers) {
			if (!(typeMember instanceof CtMethod)) {
				continue;
			}
			CtMethod<?> m = (CtMethod<?>) typeMember;
			for (CtAnnotation<?> a : m.getAnnotations()) {
				if (Arrays.asList(annotationTypes).contains(a.getAnnotationType())) {
					result.add(m);
				}
			}
		}
		return result;
	}

	@Override
	public List<CtMethod<?>> getMethodsByName(String name) {
		List<CtMethod<?>> result = new ArrayList<>(1);
		for (CtTypeMember typeMember : typeMembers) {
			if (!(typeMember instanceof CtMethod)) {
				continue;
			}
			CtMethod<?> m = (CtMethod<?>) typeMember;
			if (name.equals(m.getSimpleName())) {
				result.add(m);
			}
		}
		return result;
	}


	@Override
	public boolean hasMethod(CtMethod<?> method) {
		if (method == null) {
			return false;
		}

		final String over = method.getSignature();
		for (CtMethod<?> m : getMethods()) {
			if (m.getSignature().equals(over)) {
				return true;
			}
		}

		// Checking whether a super class has the method.
		final CtTypeReference<?> superCl = getSuperclass();
		try {
			if (superCl != null && superCl.getTypeDeclaration().hasMethod(method)) {
				return true;
			}
		} catch (SpoonException ex) {
			// No matter, trying something else.
		}

		// Finally, checking whether an interface has the method.
		for (CtTypeReference<?> interf : getSuperInterfaces()) {
			try {
				if (interf.getTypeDeclaration().hasMethod(method)) {
					return true;
				}
			} catch (SpoonException ex) {
				// No matter, trying something else.
			}
		}

		return false;
	}


	@Override
	public String getQualifiedName() {
		if (isTopLevel()) {
			if (getPackage() != null && !getPackage().isUnnamedPackage()) {
				return getPackage().getQualifiedName() + "." + getSimpleName();
			} else {
				return getSimpleName();
			}
		} else if (getDeclaringType() != null) {
			return getDeclaringType().getQualifiedName() + INNERTTYPE_SEPARATOR + getSimpleName();
		} else {
			return getSimpleName();
		}
	}

	@Override
	public Set<CtTypeReference<?>> getSuperInterfaces() {
		return interfaces;
	}

	@Override
	public <C extends CtType<T>> C setMethods(Set<CtMethod<?>> methods) {
		Set<CtMethod<?>> allMethods = getMethods();
		getFactory().getEnvironment().getModelChangeListener().onListDelete(this, EXECUTABLE, this.typeMembers, new ArrayList(allMethods));
		typeMembers.removeAll(allMethods);
		if (methods == null || methods.isEmpty()) {
			return (C) this;
		}
		for (CtMethod<?> meth : methods) {
			addMethod(meth);
		}
		return (C) this;
	}

	@Override
	@UnsettableProperty
	public <C extends CtType<T>> C setSuperclass(CtTypeReference<?> superClass) {
		// overridden in subclasses.
		return (C) this;
	}

	@Override
	public <C extends CtType<T>> C setSuperInterfaces(Set<CtTypeReference<?>> interfaces) {
		if (interfaces == null || interfaces.isEmpty()) {
			this.interfaces = CtElementImpl.emptySet();
			return (C) this;
		}
		if (this.interfaces == CtElementImpl.<CtTypeReference<?>>emptySet()) {
			this.interfaces = new QualifiedNameBasedSortedSet<>();
		}
		getFactory().getEnvironment().getModelChangeListener().onSetDeleteAll(this, INTERFACE, this.interfaces, new HashSet<>(this.interfaces));
		this.interfaces.clear();
		for (CtTypeReference<?> anInterface : interfaces) {
			addSuperInterface(anInterface);
		}
		return (C) this;
	}

	@Override
	public Collection<CtExecutableReference<?>> getDeclaredExecutables() {
		if (getMethods().isEmpty()) {
			return Collections.emptyList();
		}
		List<CtExecutableReference<?>> l = new ArrayList<>(getMethods().size());
		for (CtExecutable<?> m : getMethods()) {
			l.add(m.getReference());
		}
		return Collections.unmodifiableList(l);
	}

	@Override
	public Collection<CtExecutableReference<?>> getAllExecutables() {
		Set<CtExecutableReference<?>> l = new SignatureBasedSortedSet();
		for (CtMethod<?> m : getAllMethods()) {
			l.add((CtExecutableReference<?>) m.getReference());
		}
		return l;
	}

	@Override
	public Set<CtMethod<?>> getAllMethods() {
		final Set<CtMethod<?>> l = new HashSet<>();
		final ClassTypingContext ctc = new ClassTypingContext(this);
		map(new AllTypeMembersFunction(CtMethod.class)).forEach(new CtConsumer<CtMethod<?>>() {
			@Override
			public void accept(CtMethod<?> currentMethod) {
				for (CtMethod<?> alreadyVisitedMethod : l) {
					if (ctc.isSameSignature(currentMethod, alreadyVisitedMethod)) {
						return;
					}
				}

				l.add(currentMethod);
			}
		});
		return Collections.unmodifiableSet(l);
	}

	@Override
	public CtTypeReference<?> getTypeErasure() {
		return getReference();
	}

	@MetamodelPropertyField(role = CtRole.IS_SHADOW)
	boolean isShadow;

	@Override
	public boolean isShadow() {
		return isShadow;
	}

	@Override
	public <E extends CtShadowable> E setShadow(boolean isShadow) {
		getFactory().getEnvironment().getModelChangeListener().onObjectUpdate(this, IS_SHADOW, isShadow, this.isShadow);
		this.isShadow = isShadow;
		return (E) this;
	}

	@Override
	public CtType<T> clone() {
		return (CtType<T>) super.clone();
	}

}
