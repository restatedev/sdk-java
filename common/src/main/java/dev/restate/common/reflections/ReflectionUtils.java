// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.common.reflections;

import dev.restate.sdk.annotation.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class ReflectionUtils {

  private static final @Nullable Class<? extends Annotation> RESTATE_SPRING_SERVICE_ANNOTATION =
      tryLoadClass("dev.restate.sdk.springboot.RestateService");
  private static final @Nullable Class<? extends Annotation>
      RESTATE_SPRING_VIRTUAL_OBJECT_ANNOTATION =
          tryLoadClass("dev.restate.sdk.springboot.RestateVirtualObject");
  private static final @Nullable Class<? extends Annotation> RESTATE_SPRING_WORKFLOW_ANNOTATION =
      tryLoadClass("dev.restate.sdk.springboot.RestateWorkflow");

  /** Record containing handler information extracted from annotations. */
  public record HandlerInfo(String name, boolean shared) {}

  /**
   * Find a single {@link Annotation} of {@code annotationType} on the supplied {@link Class},
   * traversing its interfaces, annotations, and superclasses if the annotation is not <em>directly
   * present</em> on the given class itself.
   *
   * <p>This method explicitly handles class-level annotations which are not declared as {@linkplain
   * java.lang.annotation.Inherited inherited} <em>as well as meta-annotations and annotations on
   * interfaces</em>.
   *
   * <p>The algorithm operates as follows:
   *
   * <ol>
   *   <li>Search for the annotation on the given class and return it if found.
   *   <li>Recursively search through all interfaces that the given class declares.
   *   <li>Recursively search through the superclass hierarchy of the given class.
   * </ol>
   *
   * <p>Note: in this context, the term <em>recursively</em> means that the search process continues
   * by returning to step #1 with the current interface, annotation, or superclass as the class to
   * look for annotations on.
   *
   * @param clazz the class to look for annotations on
   * @param annotationType the type of annotation to look for
   * @return the first matching annotation, or {@code null} if not found
   */
  @Nullable
  public static <A extends Annotation> A findAnnotation(
      Class<?> clazz, @Nullable Class<A> annotationType) {
    if (annotationType == null) {
      return null;
    }
    return findAnnotation(clazz, annotationType, new java.util.HashSet<>());
  }

  @Nullable
  private static <A extends Annotation> A findAnnotation(
      Class<?> clazz, Class<A> annotationType, java.util.Set<Annotation> visited) {

    if (clazz == null || clazz == Object.class) {
      return null;
    }

    // Check if the annotation is directly present on the class
    A annotation = clazz.getDeclaredAnnotation(annotationType);
    if (annotation != null) {
      return annotation;
    }

    // Search on interfaces
    for (Class<?> ifc : clazz.getInterfaces()) {
      annotation = findAnnotation(ifc, annotationType, visited);
      if (annotation != null) {
        return annotation;
      }
    }

    // Search on superclass
    return findAnnotation(clazz.getSuperclass(), annotationType, visited);
  }

  /**
   * Find a single {@link Annotation} of {@code annotationType} on the supplied {@link Method},
   * traversing its super methods if the annotation is not <em>directly present</em> on the given
   * method itself.
   *
   * <p>Annotations on methods are not inherited by default, so we need to handle this explicitly.
   *
   * @param method the method to look for annotations on
   * @param annotationType the type of annotation to look for
   * @return the first matching annotation, or {@code null} if not found
   */
  @Nullable
  public static <A extends Annotation> A findAnnotation(
      Method method, @Nullable Class<A> annotationType) {
    if (annotationType == null) {
      return null;
    }

    // Check if the annotation is directly present on the method
    A annotation = method.getDeclaredAnnotation(annotationType);
    if (annotation != null) {
      return annotation;
    }

    // Search through the type hierarchy
    Class<?> clazz = method.getDeclaringClass();
    return findAnnotationInTypeHierarchy(clazz, method, annotationType, new java.util.HashSet<>());
  }

  @Nullable
  private static <A extends Annotation> A findAnnotationInTypeHierarchy(
      Class<?> clazz, Method method, Class<A> annotationType, java.util.Set<Class<?>> visited) {

    if (clazz == null || clazz == Object.class || !visited.add(clazz)) {
      return null;
    }

    // Try to find an equivalent method in this class/interface
    Method equivalentMethod = null;
    try {
      equivalentMethod = clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
    } catch (NoSuchMethodException ex) {
      // No such method in this class, continue searching
    }

    if (equivalentMethod != null) {
      A annotation = equivalentMethod.getDeclaredAnnotation(annotationType);
      if (annotation != null) {
        return annotation;
      }
    }

    // Search in interfaces
    for (Class<?> ifc : clazz.getInterfaces()) {
      A annotation = findAnnotationInTypeHierarchy(ifc, method, annotationType, visited);
      if (annotation != null) {
        return annotation;
      }
    }

    // Search in superclass
    return findAnnotationInTypeHierarchy(clazz.getSuperclass(), method, annotationType, visited);
  }

  public static String extractServiceName(Class<?> clazz) {
    // Fallback: infer from hierarchy against known Restate markers
    String inferred = inferRestateNameFromHierarchy(clazz);
    if (inferred != null) {
      return inferred;
    }

    throw new IllegalArgumentException("Cannot infer Restate name from type: " + clazz.getName());
  }

  private static String inferRestateNameFromHierarchy(Class<?> type) {
    if (type == null || Object.class.equals(type)) {
      return null;
    }

    // Check if the type has any of the Restate component annotations
    var isRestateAnnotated =
        type.getAnnotation(Service.class) != null
            || type.getAnnotation(VirtualObject.class) != null
            || type.getAnnotation(Workflow.class) != null
            || (RESTATE_SPRING_SERVICE_ANNOTATION != null
                && type.getAnnotation(RESTATE_SPRING_SERVICE_ANNOTATION) != null)
            || (RESTATE_SPRING_VIRTUAL_OBJECT_ANNOTATION != null
                && type.getAnnotation(RESTATE_SPRING_VIRTUAL_OBJECT_ANNOTATION) != null)
            || (RESTATE_SPRING_WORKFLOW_ANNOTATION != null
                && type.getAnnotation(RESTATE_SPRING_WORKFLOW_ANNOTATION) != null);
    if (isRestateAnnotated) {
      return extractNameFromAnnotations(type);
    }

    // Check parent interfaces
    for (Class<?> parent : type.getInterfaces()) {
      String res = inferRestateNameFromHierarchy(parent);
      if (res != null) {
        return res;
      }
    }

    // Recurse into superclass
    return inferRestateNameFromHierarchy(type.getSuperclass());
  }

  private static String extractNameFromAnnotations(Class<?> type) {
    // Check for @Name annotation first
    var nameAnnotation = type.getAnnotation(Name.class);
    if (nameAnnotation != null
        && nameAnnotation.value() != null
        && !nameAnnotation.value().isEmpty()) {
      return nameAnnotation.value();
    }
    // Default to simple class name
    return type.getSimpleName();
  }

  public static boolean hasServiceAnnotation(Class<?> clazz) {
    return findAnnotation(clazz, Service.class) != null
        || (RESTATE_SPRING_SERVICE_ANNOTATION != null
            && findAnnotation(clazz, RESTATE_SPRING_SERVICE_ANNOTATION) != null);
  }

  public static void mustHaveServiceAnnotation(Class<?> clazz) {
    if (!hasServiceAnnotation(clazz)) {
      throw new IllegalArgumentException(
          "The given class "
              + clazz.getName()
              + " is not annotated with the Restate service annotation");
    }
  }

  public static boolean hasVirtualObjectAnnotation(Class<?> clazz) {
    return findAnnotation(clazz, VirtualObject.class) != null
        || (RESTATE_SPRING_VIRTUAL_OBJECT_ANNOTATION != null
            && findAnnotation(clazz, RESTATE_SPRING_VIRTUAL_OBJECT_ANNOTATION) != null);
  }

  public static void mustHaveVirtualObjectAnnotation(Class<?> clazz) {
    if (!hasVirtualObjectAnnotation(clazz)) {
      throw new IllegalArgumentException(
          "The given class "
              + clazz.getName()
              + " is not annotated with the Restate virtualObject annotation");
    }
  }

  public static boolean hasWorkflowAnnotation(Class<?> clazz) {
    return findAnnotation(clazz, Workflow.class) != null
        || (RESTATE_SPRING_WORKFLOW_ANNOTATION != null
            && findAnnotation(clazz, RESTATE_SPRING_WORKFLOW_ANNOTATION) != null);
  }

  public static void mustHaveWorkflowAnnotation(Class<?> clazz) {
    if (!hasWorkflowAnnotation(clazz)) {
      throw new IllegalArgumentException(
          "The given class "
              + clazz.getName()
              + " is not annotated with the Restate workflow annotation");
    }
  }

  public static HandlerInfo mustHaveHandlerAnnotation(@NonNull Method method) {
    // Check for @Handler or @Shared annotation (Shared implies Handler)
    var handlerAnnotation = findAnnotation(method, Handler.class);
    var sharedAnnotation = findAnnotation(method, Shared.class);
    var exclusiveAnnotation = findAnnotation(method, Exclusive.class);
    var workflowAnnotation = findAnnotation(method, Workflow.class);

    if (handlerAnnotation == null
        && sharedAnnotation == null
        && workflowAnnotation == null
        && exclusiveAnnotation == null) {
      throw new IllegalArgumentException(
          "The invoked method '"
              + method.getName()
              + "' is not annotated with @"
              + Handler.class.getSimpleName()
              + " or @"
              + Shared.class.getSimpleName()
              + " or @"
              + Exclusive.class.getSimpleName()
              + " or @"
              + Workflow.class.getSimpleName());
    }

    // Extract the name from @Name annotation, or default to method name
    var nameAnnotation = findAnnotation(method, Name.class);
    String handlerName;
    if (nameAnnotation != null
        && nameAnnotation.value() != null
        && !nameAnnotation.value().isEmpty()) {
      handlerName = nameAnnotation.value();
    } else {
      handlerName = method.getName();
    }

    // Determine if it's shared
    boolean isShared = sharedAnnotation != null;

    return new HandlerInfo(handlerName, isShared);
  }

  /**
   * Walks the type hierarchy to find where the given rawType interface was parameterized. This
   * handles inheritance chains and multiple interfaces correctly.
   *
   * @param concreteClass The concrete class to start searching from
   * @param rawType The raw interface type to find (e.g., Function.class)
   * @return The ParameterizedType with resolved type arguments, or null if not found
   */
  public static ParameterizedType findParameterizedType(Class<?> concreteClass, Class<?> rawType) {
    if (concreteClass == null || Object.class.equals(concreteClass)) {
      return null;
    }

    // Check direct interfaces
    for (Type genericInterface : concreteClass.getGenericInterfaces()) {
      ParameterizedType result = findParameterizedTypeInType(genericInterface, rawType);
      if (result != null) {
        return result;
      }
    }

    // Check superclass
    Type genericSuperclass = concreteClass.getGenericSuperclass();
    if (genericSuperclass != null) {
      ParameterizedType result = findParameterizedTypeInType(genericSuperclass, rawType);
      if (result != null) {
        return result;
      }
    }

    // Recurse up the hierarchy
    return findParameterizedType(concreteClass.getSuperclass(), rawType);
  }

  private static ParameterizedType findParameterizedTypeInType(Type type, Class<?> rawType) {
    if (type instanceof ParameterizedType paramType) {
      if (paramType.getRawType().equals(rawType)) {
        return paramType;
      }
      // Check if this parameterized type extends/implements the target
      if (paramType.getRawType() instanceof Class<?> clazz) {
        return findParameterizedType(clazz, rawType);
      }
    } else if (type instanceof Class<?> clazz) {
      return findParameterizedType(clazz, rawType);
    }
    return null;
  }

  public static boolean isKotlinClass(Class<?> clazz) {
    return Arrays.stream(clazz.getDeclaredAnnotations())
        .anyMatch(annotation -> annotation.annotationType().getName().equals("kotlin.Metadata"));
  }

  @SuppressWarnings("unchecked")
  private static @Nullable <T> Class<T> tryLoadClass(String className) {
    try {
      return (Class<T>) Class.forName(className);
    } catch (ClassNotFoundException e) {
      return null;
    }
  }

  // From Spring's ReflectionUtils
  // License Apache 2.0

  /**
   * Pre-built {@link MethodFilter} that matches all non-bridge non-synthetic methods which are not
   * declared on {@code java.lang.Object}.
   *
   * @since 3.0.5
   */
  public static final MethodFilter USER_DECLARED_METHODS =
      (method ->
          !method.isBridge()
              && !method.isSynthetic()
              && (method.getDeclaringClass() != Object.class));

  /** Pre-built FieldFilter that matches all non-static, non-final fields. */
  public static final FieldFilter COPYABLE_FIELDS =
      (field ->
          !(Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())));

  /**
   * Naming prefix for CGLIB-renamed methods.
   *
   * @see #isCglibRenamedMethod
   */
  private static final String CGLIB_RENAMED_METHOD_PREFIX = "CGLIB$";

  private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];

  private static final Method[] EMPTY_METHOD_ARRAY = new Method[0];

  private static final Field[] EMPTY_FIELD_ARRAY = new Field[0];

  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

  /**
   * Cache for {@link Class#getDeclaredMethods()} plus equivalent default methods from Java 8 based
   * interfaces, allowing for fast iteration.
   */
  private static final Map<Class<?>, Method[]> declaredMethodsCache =
      new ConcurrentReferenceHashMap<>(256);

  /** Cache for {@link Class#getDeclaredFields()}, allowing for fast iteration. */
  private static final Map<Class<?>, Field[]> declaredFieldsCache =
      new ConcurrentReferenceHashMap<>(256);

  // Exception handling

  /**
   * Handle the given reflection exception.
   *
   * <p>Should only be called if no checked exception is expected to be thrown by a target method,
   * or if an error occurs while accessing a method or field.
   *
   * <p>Throws the underlying RuntimeException or Error in case of an InvocationTargetException with
   * such a root cause. Throws an IllegalStateException with an appropriate message or
   * UndeclaredThrowableException otherwise.
   *
   * @param ex the reflection exception to handle
   */
  public static void handleReflectionException(Exception ex) {
    if (ex instanceof NoSuchMethodException) {
      throw new IllegalStateException("Method not found: " + ex.getMessage());
    }
    if (ex instanceof IllegalAccessException) {
      throw new IllegalStateException("Could not access method or field: " + ex.getMessage());
    }
    if (ex instanceof InvocationTargetException invocationTargetException) {
      handleInvocationTargetException(invocationTargetException);
    }
    if (ex instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    throw new UndeclaredThrowableException(ex);
  }

  /**
   * Handle the given invocation target exception. Should only be called if no checked exception is
   * expected to be thrown by the target method.
   *
   * <p>Throws the underlying RuntimeException or Error in case of such a root cause. Throws an
   * UndeclaredThrowableException otherwise.
   *
   * @param ex the invocation target exception to handle
   */
  public static void handleInvocationTargetException(InvocationTargetException ex) {
    rethrowRuntimeException(ex.getTargetException());
  }

  /**
   * Rethrow the given {@link Throwable exception}, which is presumably the <em>target
   * exception</em> of an {@link InvocationTargetException}. Should only be called if no checked
   * exception is expected to be thrown by the target method.
   *
   * <p>Rethrows the underlying exception cast to a {@link RuntimeException} or {@link Error} if
   * appropriate; otherwise, throws an {@link UndeclaredThrowableException}.
   *
   * @param ex the exception to rethrow
   * @throws RuntimeException the rethrown exception
   */
  public static void rethrowRuntimeException(@Nullable Throwable ex) {
    if (ex instanceof RuntimeException runtimeException) {
      throw runtimeException;
    }
    if (ex instanceof Error error) {
      throw error;
    }
    throw new UndeclaredThrowableException(ex);
  }

  /**
   * Rethrow the given {@link Throwable exception}, which is presumably the <em>target
   * exception</em> of an {@link InvocationTargetException}. Should only be called if no checked
   * exception is expected to be thrown by the target method.
   *
   * <p>Rethrows the underlying exception cast to an {@link Exception} or {@link Error} if
   * appropriate; otherwise, throws an {@link UndeclaredThrowableException}.
   *
   * @param throwable the exception to rethrow
   * @throws Exception the rethrown exception (in case of a checked exception)
   */
  public static void rethrowException(@Nullable Throwable throwable) throws Exception {
    if (throwable instanceof Exception exception) {
      throw exception;
    }
    if (throwable instanceof Error error) {
      throw error;
    }
    throw new UndeclaredThrowableException(throwable);
  }

  // Constructor handling

  /**
   * Obtain an accessible constructor for the given class and parameters.
   *
   * @param clazz the clazz to check
   * @param parameterTypes the parameter types of the desired constructor
   * @return the constructor reference
   * @throws NoSuchMethodException if no such constructor exists
   * @since 5.0
   */
  public static <T> Constructor<T> accessibleConstructor(Class<T> clazz, Class<?>... parameterTypes)
      throws NoSuchMethodException {

    Constructor<T> ctor = clazz.getDeclaredConstructor(parameterTypes);
    makeAccessible(ctor);
    return ctor;
  }

  /**
   * Make the given constructor accessible, explicitly setting it accessible if necessary. The
   * {@code setAccessible(true)} method is only called when actually necessary, to avoid unnecessary
   * conflicts.
   *
   * @param ctor the constructor to make accessible
   * @see Constructor#setAccessible
   */
  @SuppressWarnings("deprecation")
  public static void makeAccessible(Constructor<?> ctor) {
    if ((!Modifier.isPublic(ctor.getModifiers())
            || !Modifier.isPublic(ctor.getDeclaringClass().getModifiers()))
        && !ctor.isAccessible()) {
      ctor.setAccessible(true);
    }
  }

  // Method handling

  /**
   * Attempt to find a {@link Method} on the supplied class with the supplied name and no
   * parameters. Searches all superclasses up to {@code Object}.
   *
   * <p>Returns {@code null} if no {@link Method} can be found.
   *
   * @param clazz the class to introspect
   * @param name the name of the method
   * @return the Method object, or {@code null} if none found
   */
  public static @Nullable Method findMethod(Class<?> clazz, String name) {
    return findMethod(clazz, name, EMPTY_CLASS_ARRAY);
  }

  /**
   * Attempt to find a {@link Method} on the supplied class with the supplied name and parameter
   * types. Searches all superclasses up to {@code Object}.
   *
   * <p>Returns {@code null} if no {@link Method} can be found.
   *
   * @param clazz the class to introspect
   * @param name the name of the method
   * @param paramTypes the parameter types of the method (may be {@code null} to indicate any
   *     signature)
   * @return the Method object, or {@code null} if none found
   */
  public static @Nullable Method findMethod(
      Class<?> clazz, String name, Class<?> @Nullable ... paramTypes) {
    Class<?> searchType = clazz;
    while (searchType != null) {
      Method[] methods =
          (searchType.isInterface()
              ? searchType.getMethods()
              : getDeclaredMethods(searchType, false));
      for (Method method : methods) {
        if (name.equals(method.getName())
            && (paramTypes == null || hasSameParams(method, paramTypes))) {
          return method;
        }
      }
      searchType = searchType.getSuperclass();
    }
    return null;
  }

  private static boolean hasSameParams(Method method, Class<?>[] paramTypes) {
    return (paramTypes.length == method.getParameterCount()
        && Arrays.equals(paramTypes, method.getParameterTypes()));
  }

  /**
   * Invoke the specified {@link Method} against the supplied target object with no arguments. The
   * target object can be {@code null} when invoking a static {@link Method}.
   *
   * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException}.
   *
   * @param method the method to invoke
   * @param target the target object to invoke the method on
   * @return the invocation result, if any
   * @see #invokeMethod(Method, Object, Object[])
   */
  public static @Nullable Object invokeMethod(Method method, @Nullable Object target) {
    return invokeMethod(method, target, EMPTY_OBJECT_ARRAY);
  }

  /**
   * Invoke the specified {@link Method} against the supplied target object with the supplied
   * arguments. The target object can be {@code null} when invoking a static {@link Method}.
   *
   * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException}.
   *
   * @param method the method to invoke
   * @param target the target object to invoke the method on
   * @param args the invocation arguments (may be {@code null})
   * @return the invocation result, if any
   */
  public static @Nullable Object invokeMethod(
      Method method, @Nullable Object target, @Nullable Object... args) {
    try {
      return method.invoke(target, args);
    } catch (Exception ex) {
      handleReflectionException(ex);
    }
    throw new IllegalStateException("Should never get here");
  }

  /**
   * Determine whether the given method explicitly declares the given exception or one of its
   * superclasses, which means that an exception of that type can be propagated as-is within a
   * reflective invocation.
   *
   * @param method the declaring method
   * @param exceptionType the exception to throw
   * @return {@code true} if the exception can be thrown as-is; {@code false} if it needs to be
   *     wrapped
   */
  public static boolean declaresException(Method method, Class<?> exceptionType) {
    Class<?>[] declaredExceptions = method.getExceptionTypes();
    for (Class<?> declaredException : declaredExceptions) {
      if (declaredException.isAssignableFrom(exceptionType)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Perform the given callback operation on all matching methods of the given class, as locally
   * declared or equivalent thereof (such as default methods on Java 8 based interfaces that the
   * given class implements).
   *
   * @param clazz the class to introspect
   * @param mc the callback to invoke for each method
   * @throws IllegalStateException if introspection fails
   * @see #doWithMethods
   * @since 4.2
   */
  public static void doWithLocalMethods(Class<?> clazz, MethodCallback mc) {
    Method[] methods = getDeclaredMethods(clazz, false);
    for (Method method : methods) {
      try {
        mc.doWith(method);
      } catch (IllegalAccessException ex) {
        throw new IllegalStateException(
            "Not allowed to access method '" + method.getName() + "': " + ex);
      }
    }
  }

  /**
   * Perform the given callback operation on all matching methods of the given class and
   * superclasses.
   *
   * <p>The same named method occurring on subclass and superclass will appear twice, unless
   * excluded by a {@link MethodFilter}.
   *
   * @param clazz the class to introspect
   * @param mc the callback to invoke for each method
   * @throws IllegalStateException if introspection fails
   * @see #doWithMethods(Class, MethodCallback, MethodFilter)
   */
  public static void doWithMethods(Class<?> clazz, MethodCallback mc) {
    doWithMethods(clazz, mc, null);
  }

  /**
   * Perform the given callback operation on all matching methods of the given class and
   * superclasses (or given interface and super-interfaces).
   *
   * <p>The same named method occurring on subclass and superclass will appear twice, unless
   * excluded by the specified {@link MethodFilter}.
   *
   * @param clazz the class to introspect
   * @param mc the callback to invoke for each method
   * @param mf the filter that determines the methods to apply the callback to
   * @throws IllegalStateException if introspection fails
   */
  public static void doWithMethods(Class<?> clazz, MethodCallback mc, @Nullable MethodFilter mf) {
    if (mf == USER_DECLARED_METHODS && clazz == Object.class) {
      // nothing to introspect
      return;
    }
    Method[] methods = getDeclaredMethods(clazz, false);
    for (Method method : methods) {
      if (mf != null && !mf.matches(method)) {
        continue;
      }
      try {
        mc.doWith(method);
      } catch (IllegalAccessException ex) {
        throw new IllegalStateException(
            "Not allowed to access method '" + method.getName() + "': " + ex);
      }
    }
    // Keep backing up the inheritance hierarchy.
    if (clazz.getSuperclass() != null
        && (mf != USER_DECLARED_METHODS || clazz.getSuperclass() != Object.class)) {
      doWithMethods(clazz.getSuperclass(), mc, mf);
    } else if (clazz.isInterface()) {
      for (Class<?> superIfc : clazz.getInterfaces()) {
        doWithMethods(superIfc, mc, mf);
      }
    }
  }

  /**
   * Get all declared methods on the leaf class and all superclasses. Leaf class methods are
   * included first.
   *
   * @param leafClass the class to introspect
   * @throws IllegalStateException if introspection fails
   */
  public static Method[] getAllDeclaredMethods(Class<?> leafClass) {
    final List<Method> methods = new ArrayList<>(20);
    doWithMethods(leafClass, methods::add);
    return methods.toArray(EMPTY_METHOD_ARRAY);
  }

  /**
   * Get the unique set of declared methods on the leaf class and all superclasses. Leaf class
   * methods are included first and while traversing the superclass hierarchy any methods found with
   * signatures matching a method already included are filtered out.
   *
   * @param leafClass the class to introspect
   * @throws IllegalStateException if introspection fails
   */
  public static Method[] getUniqueDeclaredMethods(Class<?> leafClass) {
    return getUniqueDeclaredMethods(leafClass, null);
  }

  /**
   * Get the unique set of declared methods on the leaf class and all superclasses. Leaf class
   * methods are included first and while traversing the superclass hierarchy any methods found with
   * signatures matching a method already included are filtered out.
   *
   * @param leafClass the class to introspect
   * @param mf the filter that determines the methods to take into account
   * @throws IllegalStateException if introspection fails
   * @since 5.2
   */
  public static Method[] getUniqueDeclaredMethods(Class<?> leafClass, @Nullable MethodFilter mf) {
    final List<Method> methods = new ArrayList<>(20);
    doWithMethods(
        leafClass,
        method -> {
          boolean knownSignature = false;
          Method methodBeingOverriddenWithCovariantReturnType = null;
          for (Method existingMethod : methods) {
            if (method.getName().equals(existingMethod.getName())
                && method.getParameterCount() == existingMethod.getParameterCount()
                && Arrays.equals(method.getParameterTypes(), existingMethod.getParameterTypes())) {
              // Is this a covariant return type situation?
              if (existingMethod.getReturnType() != method.getReturnType()
                  && existingMethod.getReturnType().isAssignableFrom(method.getReturnType())) {
                methodBeingOverriddenWithCovariantReturnType = existingMethod;
              } else {
                knownSignature = true;
              }
              break;
            }
          }
          if (methodBeingOverriddenWithCovariantReturnType != null) {
            methods.remove(methodBeingOverriddenWithCovariantReturnType);
          }
          if (!knownSignature && !isCglibRenamedMethod(method)) {
            methods.add(method);
          }
        },
        mf);
    return methods.toArray(EMPTY_METHOD_ARRAY);
  }

  /**
   * Variant of {@link Class#getDeclaredMethods()} that uses a local cache in order to avoid new
   * Method instances. In addition, it also includes Java 8 default methods from locally implemented
   * interfaces, since those are effectively to be treated just like declared methods.
   *
   * @param clazz the class to introspect
   * @return the cached array of methods
   * @throws IllegalStateException if introspection fails
   * @see Class#getDeclaredMethods()
   * @since 5.2
   */
  public static Method[] getDeclaredMethods(Class<?> clazz) {
    return getDeclaredMethods(clazz, true);
  }

  private static Method[] getDeclaredMethods(Class<?> clazz, boolean defensive) {
    Method[] result = declaredMethodsCache.get(clazz);
    if (result == null) {
      try {
        Method[] declaredMethods = clazz.getDeclaredMethods();
        List<Method> defaultMethods = findDefaultMethodsOnInterfaces(clazz);
        if (defaultMethods != null) {
          result = new Method[declaredMethods.length + defaultMethods.size()];
          System.arraycopy(declaredMethods, 0, result, 0, declaredMethods.length);
          int index = declaredMethods.length;
          for (Method defaultMethod : defaultMethods) {
            result[index] = defaultMethod;
            index++;
          }
        } else {
          result = declaredMethods;
        }
        declaredMethodsCache.put(clazz, (result.length == 0 ? EMPTY_METHOD_ARRAY : result));
      } catch (Throwable ex) {
        throw new IllegalStateException(
            "Failed to introspect Class ["
                + clazz.getName()
                + "] from ClassLoader ["
                + clazz.getClassLoader()
                + "]",
            ex);
      }
    }
    return (result.length == 0 || !defensive) ? result : result.clone();
  }

  private static @Nullable List<Method> findDefaultMethodsOnInterfaces(Class<?> clazz) {
    List<Method> result = null;
    for (Class<?> ifc : clazz.getInterfaces()) {
      for (Method method : ifc.getMethods()) {
        if (method.isDefault()) {
          if (result == null) {
            result = new ArrayList<>();
          }
          result.add(method);
        }
      }
    }
    return result;
  }

  /**
   * Determine whether the given method is an "equals" method.
   *
   * @see Object#equals(Object)
   */
  public static boolean isEqualsMethod(@Nullable Method method) {
    return (method != null
        && method.getParameterCount() == 1
        && method.getName().equals("equals")
        && method.getParameterTypes()[0] == Object.class);
  }

  /**
   * Determine whether the given method is a "hashCode" method.
   *
   * @see Object#hashCode()
   */
  public static boolean isHashCodeMethod(@Nullable Method method) {
    return (method != null
        && method.getParameterCount() == 0
        && method.getName().equals("hashCode"));
  }

  /**
   * Determine whether the given method is a "toString" method.
   *
   * @see Object#toString()
   */
  public static boolean isToStringMethod(@Nullable Method method) {
    return (method != null
        && method.getParameterCount() == 0
        && method.getName().equals("toString"));
  }

  /** Determine whether the given method is originally declared by {@link Object}. */
  public static boolean isObjectMethod(@Nullable Method method) {
    return (method != null
        && (method.getDeclaringClass() == Object.class
            || isEqualsMethod(method)
            || isHashCodeMethod(method)
            || isToStringMethod(method)));
  }

  /**
   * Determine whether the given method is a CGLIB 'renamed' method, following the pattern
   * "CGLIB$methodName$0".
   *
   * @param renamedMethod the method to check
   */
  public static boolean isCglibRenamedMethod(Method renamedMethod) {
    String name = renamedMethod.getName();
    if (name.startsWith(CGLIB_RENAMED_METHOD_PREFIX)) {
      int i = name.length() - 1;
      while (i >= 0 && Character.isDigit(name.charAt(i))) {
        i--;
      }
      return (i > CGLIB_RENAMED_METHOD_PREFIX.length()
          && (i < name.length() - 1)
          && name.charAt(i) == '$');
    }
    return false;
  }

  /**
   * Make the given method accessible, explicitly setting it accessible if necessary. The {@code
   * setAccessible(true)} method is only called when actually necessary, to avoid unnecessary
   * conflicts.
   *
   * @param method the method to make accessible
   * @see Method#setAccessible
   */
  @SuppressWarnings("deprecation")
  public static void makeAccessible(Method method) {
    if ((!Modifier.isPublic(method.getModifiers())
            || !Modifier.isPublic(method.getDeclaringClass().getModifiers()))
        && !method.isAccessible()) {
      method.setAccessible(true);
    }
  }

  // Field handling

  /**
   * Attempt to find a {@link Field field} on the supplied {@link Class} with the supplied {@code
   * name}. Searches all superclasses up to {@link Object}.
   *
   * @param clazz the class to introspect
   * @param name the name of the field
   * @return the corresponding Field object, or {@code null} if not found
   */
  public static @Nullable Field findField(Class<?> clazz, String name) {
    return findField(clazz, name, null);
  }

  /**
   * Attempt to find a {@link Field field} on the supplied {@link Class} with the supplied {@code
   * name} and/or {@link Class type}. Searches all superclasses up to {@link Object}.
   *
   * @param clazz the class to introspect
   * @param name the name of the field (may be {@code null} if type is specified)
   * @param type the type of the field (may be {@code null} if name is specified)
   * @return the corresponding Field object, or {@code null} if not found
   */
  public static @Nullable Field findField(
      Class<?> clazz, @Nullable String name, @Nullable Class<?> type) {
    Class<?> searchType = clazz;
    while (Object.class != searchType && searchType != null) {
      Field[] fields = getDeclaredFields(searchType);
      for (Field field : fields) {
        if ((name == null || name.equals(field.getName()))
            && (type == null || type.equals(field.getType()))) {
          return field;
        }
      }
      searchType = searchType.getSuperclass();
    }
    return null;
  }

  /**
   * Attempt to find a {@link Field field} on the supplied {@link Class} with the supplied {@code
   * name}. Searches all superclasses up to {@link Object}.
   *
   * @param clazz the class to introspect
   * @param name the name of the field (with upper/lower case to be ignored)
   * @return the corresponding Field object, or {@code null} if not found
   * @since 6.1
   */
  public static @Nullable Field findFieldIgnoreCase(Class<?> clazz, String name) {
    Class<?> searchType = clazz;
    while (Object.class != searchType && searchType != null) {
      Field[] fields = getDeclaredFields(searchType);
      for (Field field : fields) {
        if (name.equalsIgnoreCase(field.getName())) {
          return field;
        }
      }
      searchType = searchType.getSuperclass();
    }
    return null;
  }

  /**
   * Set the field represented by the supplied {@linkplain Field field object} on the specified
   * {@linkplain Object target object} to the specified {@code value}.
   *
   * <p>In accordance with {@link Field#set(Object, Object)} semantics, the new value is
   * automatically unwrapped if the underlying field has a primitive type.
   *
   * <p>This method does not support setting {@code static final} fields.
   *
   * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException(Exception)}.
   *
   * @param field the field to set
   * @param target the target object on which to set the field (or {@code null} for a static field)
   * @param value the value to set (may be {@code null})
   */
  public static void setField(Field field, @Nullable Object target, @Nullable Object value) {
    try {
      field.set(target, value);
    } catch (IllegalAccessException ex) {
      handleReflectionException(ex);
    }
  }

  /**
   * Get the field represented by the supplied {@link Field field object} on the specified {@link
   * Object target object}. In accordance with {@link Field#get(Object)} semantics, the returned
   * value is automatically wrapped if the underlying field has a primitive type.
   *
   * <p>Thrown exceptions are handled via a call to {@link #handleReflectionException(Exception)}.
   *
   * @param field the field to get
   * @param target the target object from which to get the field (or {@code null} for a static
   *     field)
   * @return the field's current value
   */
  public static @Nullable Object getField(Field field, @Nullable Object target) {
    try {
      return field.get(target);
    } catch (IllegalAccessException ex) {
      handleReflectionException(ex);
    }
    throw new IllegalStateException("Should never get here");
  }

  /**
   * Invoke the given callback on all locally declared fields in the given class.
   *
   * @param clazz the target class to analyze
   * @param fc the callback to invoke for each field
   * @throws IllegalStateException if introspection fails
   * @see #doWithFields
   * @since 4.2
   */
  public static void doWithLocalFields(Class<?> clazz, FieldCallback fc) {
    for (Field field : getDeclaredFields(clazz)) {
      try {
        fc.doWith(field);
      } catch (IllegalAccessException ex) {
        throw new IllegalStateException(
            "Not allowed to access field '" + field.getName() + "': " + ex);
      }
    }
  }

  /**
   * Invoke the given callback on all fields in the target class, going up the class hierarchy to
   * get all declared fields.
   *
   * @param clazz the target class to analyze
   * @param fc the callback to invoke for each field
   * @throws IllegalStateException if introspection fails
   */
  public static void doWithFields(Class<?> clazz, FieldCallback fc) {
    doWithFields(clazz, fc, null);
  }

  /**
   * Invoke the given callback on all fields in the target class, going up the class hierarchy to
   * get all declared fields.
   *
   * @param clazz the target class to analyze
   * @param fc the callback to invoke for each field
   * @param ff the filter that determines the fields to apply the callback to
   * @throws IllegalStateException if introspection fails
   */
  public static void doWithFields(Class<?> clazz, FieldCallback fc, @Nullable FieldFilter ff) {
    // Keep backing up the inheritance hierarchy.
    Class<?> targetClass = clazz;
    do {
      for (Field field : getDeclaredFields(targetClass)) {
        if (ff != null && !ff.matches(field)) {
          continue;
        }
        try {
          fc.doWith(field);
        } catch (IllegalAccessException ex) {
          throw new IllegalStateException(
              "Not allowed to access field '" + field.getName() + "': " + ex);
        }
      }
      targetClass = targetClass.getSuperclass();
    } while (targetClass != null && targetClass != Object.class);
  }

  /**
   * This variant retrieves {@link Class#getDeclaredFields()} from a local cache in order to avoid
   * defensive array copying.
   *
   * @param clazz the class to introspect
   * @return the cached array of fields
   * @throws IllegalStateException if introspection fails
   * @see Class#getDeclaredFields()
   */
  private static Field[] getDeclaredFields(Class<?> clazz) {
    Field[] result = declaredFieldsCache.get(clazz);
    if (result == null) {
      try {
        result = clazz.getDeclaredFields();
        declaredFieldsCache.put(clazz, (result.length == 0 ? EMPTY_FIELD_ARRAY : result));
      } catch (Throwable ex) {
        throw new IllegalStateException(
            "Failed to introspect Class ["
                + clazz.getName()
                + "] from ClassLoader ["
                + clazz.getClassLoader()
                + "]",
            ex);
      }
    }
    return result;
  }

  /**
   * Given the source object and the destination, which must be the same class or a subclass, copy
   * all fields, including inherited fields. Designed to work on objects with public no-arg
   * constructors.
   *
   * @throws IllegalStateException if introspection fails
   */
  public static void shallowCopyFieldState(final Object src, final Object dest) {
    if (!src.getClass().isAssignableFrom(dest.getClass())) {
      throw new IllegalArgumentException(
          "Destination class ["
              + dest.getClass().getName()
              + "] must be same or subclass as source class ["
              + src.getClass().getName()
              + "]");
    }
    doWithFields(
        src.getClass(),
        field -> {
          makeAccessible(field);
          Object srcValue = field.get(src);
          field.set(dest, srcValue);
        },
        COPYABLE_FIELDS);
  }

  /**
   * Determine whether the given field is a "public static final" constant.
   *
   * @param field the field to check
   */
  public static boolean isPublicStaticFinal(Field field) {
    int modifiers = field.getModifiers();
    return (Modifier.isPublic(modifiers)
        && Modifier.isStatic(modifiers)
        && Modifier.isFinal(modifiers));
  }

  /**
   * Make the given field accessible, explicitly setting it accessible if necessary. The {@code
   * setAccessible(true)} method is only called when actually necessary, to avoid unnecessary
   * conflicts.
   *
   * @param field the field to make accessible
   * @see Field#setAccessible
   */
  @SuppressWarnings("deprecation")
  public static void makeAccessible(Field field) {
    if ((!Modifier.isPublic(field.getModifiers())
            || !Modifier.isPublic(field.getDeclaringClass().getModifiers())
            || Modifier.isFinal(field.getModifiers()))
        && !field.isAccessible()) {
      field.setAccessible(true);
    }
  }

  // Cache handling

  /**
   * Clear the internal method/field cache.
   *
   * @since 4.2.4
   */
  public static void clearCache() {
    declaredMethodsCache.clear();
    declaredFieldsCache.clear();
  }

  /** Action to take on each method. */
  @FunctionalInterface
  public interface MethodCallback {

    /**
     * Perform an operation using the given method.
     *
     * @param method the method to operate on
     */
    void doWith(Method method) throws IllegalArgumentException, IllegalAccessException;
  }

  /** Callback optionally used to filter methods to be operated on by a method callback. */
  @FunctionalInterface
  public interface MethodFilter {

    /**
     * Determine whether the given method matches.
     *
     * @param method the method to check
     */
    boolean matches(Method method);

    /**
     * Create a composite filter based on this filter <em>and</em> the provided filter.
     *
     * <p>If this filter does not match, the next filter will not be applied.
     *
     * @param next the next {@code MethodFilter}
     * @return a composite {@code MethodFilter}
     * @throws IllegalArgumentException if the MethodFilter argument is {@code null}
     * @since 5.3.2
     */
    default MethodFilter and(MethodFilter next) {
      return method -> matches(method) && next.matches(method);
    }
  }

  /** Callback interface invoked on each field in the hierarchy. */
  @FunctionalInterface
  public interface FieldCallback {

    /**
     * Perform an operation using the given field.
     *
     * @param field the field to operate on
     */
    void doWith(Field field) throws IllegalArgumentException, IllegalAccessException;
  }

  /** Callback optionally used to filter fields to be operated on by a field callback. */
  @FunctionalInterface
  public interface FieldFilter {

    /**
     * Determine whether the given field matches.
     *
     * @param field the field to check
     */
    boolean matches(Field field);

    /**
     * Create a composite filter based on this filter <em>and</em> the provided filter.
     *
     * <p>If this filter does not match, the next filter will not be applied.
     *
     * @param next the next {@code FieldFilter}
     * @return a composite {@code FieldFilter}
     * @throws IllegalArgumentException if the FieldFilter argument is {@code null}
     * @since 5.3.2
     */
    default FieldFilter and(FieldFilter next) {
      return field -> matches(field) && next.matches(field);
    }
  }
}
