package org.fuin.refmopp;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.lang.model.type.PrimitiveType;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.emftext.language.java.annotations.AnnotationInstance;
import org.emftext.language.java.classifiers.AnonymousClass;
import org.emftext.language.java.classifiers.Class;
import org.emftext.language.java.classifiers.Classifier;
import org.emftext.language.java.classifiers.Interface;
import org.emftext.language.java.members.Field;
import org.emftext.language.java.members.Method;
import org.emftext.language.java.modifiers.Abstract;
import org.emftext.language.java.modifiers.AnnotableAndModifiable;
import org.emftext.language.java.modifiers.AnnotationInstanceOrModifier;
import org.emftext.language.java.modifiers.Final;
import org.emftext.language.java.modifiers.Modifier;
import org.emftext.language.java.modifiers.ModifiersFactory;
import org.emftext.language.java.modifiers.Native;
import org.emftext.language.java.modifiers.Private;
import org.emftext.language.java.modifiers.Protected;
import org.emftext.language.java.modifiers.Public;
import org.emftext.language.java.modifiers.Static;
import org.emftext.language.java.modifiers.Strictfp;
import org.emftext.language.java.modifiers.Synchronized;
import org.emftext.language.java.parameters.Parameter;
import org.emftext.language.java.resource.JavaSourceOrClassFileResource;
import org.emftext.language.java.types.Type;
import org.emftext.language.java.types.TypeReference;
import org.reflections.adapters.MetadataAdapter;
import org.reflections.vfs.Vfs;
import org.reflections.vfs.Vfs.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;

/**
 * Inspects source or class files using JaMoPP.
 */
public class JamoppAdapter implements MetadataAdapter<Class, Field, Method> {

    private static final Logger LOG = LoggerFactory.getLogger(JamoppAdapter.class);

    private final ResourceSet resourceSet;

    @Nullable
    private Cache<Vfs.File, Class> classFileCache;

    /**
     * Constructor with resource set to use.
     * 
     * @param resourceSet
     *            Resource set that has all dependencies.
     */
    public JamoppAdapter(final ResourceSet resourceSet) {
        super();
        this.resourceSet = resourceSet;
        try {
            classFileCache = CacheBuilder.newBuilder().softValues().weakKeys().maximumSize(16)
                    .expireAfterWrite(500, TimeUnit.MILLISECONDS)
                    .build(new CacheLoader<Vfs.File, Class>() {
                        @Override
                        public Class load(Vfs.File key) throws Exception {
                            return createClassObject(key);
                        }
                    });
        } catch (Error e) {
            classFileCache = null;
        }
    }

    @Override
    public boolean acceptsInput(String file) {
        return file.endsWith(".java") || file.endsWith(".class");
    }

    @Override
    public String getClassName(final Class cls) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getClassName(" + cls + ")");
        }
        return getFullQualifiedName(cls);
    }

    @Override
    public String getSuperclassName(final Class cls) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getSuperclassName(" + cls + ")");
        }
        return getFullQualifiedName(cls.getParentConcreteClassifier());
    }

    @Override
    public List<String> getInterfacesNames(final Class cls) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getInterfacesNames(" + cls + ")");
        }
        final List<String> names = Lists.newArrayList();
        final EList<TypeReference> implementedInterfaces = cls.getImplements();
        for (TypeReference implementedInterface : implementedInterfaces) {
            if (implementedInterface.getTarget() instanceof Interface) {
                final Interface intf = (Interface) implementedInterface.getTarget();
                names.add(getFullQualifiedName(intf));
            }
        }
        return names;
    }

    @Override
    public List<Field> getFields(final Class cls) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getFields(" + cls + ")");
        }
        return cls.getFields();
    }

    @Override
    public List<Method> getMethods(final Class cls) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getMethods(" + cls + ")");
        }
        return cls.getMethods();
    }

    @Override
    public String getMethodName(final Method method) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getMethodName(" + method + ")");
        }
        return method.getName();
    }

    @Override
    public List<String> getParameterNames(final Method method) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getParameterNames(" + method + ")");
        }
        final List<String> names = Lists.newArrayList();
        final EList<Parameter> parameters = method.getParameters();
        for (final Parameter parameter : parameters) {
            names.add(parameter.getName());
        }
        return names;
    }

    @Override
    public List<String> getClassAnnotationNames(final Class clasz) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getClassAnnotationNames(" + clasz + ")");
        }
        return getAnnotationNames(clasz);
    }

    @Override
    public List<String> getFieldAnnotationNames(final Field field) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getFieldAnnotationNames(" + field + ")");
        }
        return getAnnotationNames(field);
    }

    @Override
    public List<String> getMethodAnnotationNames(final Method method) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getMethodAnnotationNames(" + method + ")");
        }
        return getAnnotationNames(method);
    }

    @Override
    public List<String> getParameterAnnotationNames(final Method method, final int parameterIndex) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getParameterAnnotationNames(" + method + ", " + parameterIndex + ")");
        }
        final List<Parameter> parameters = method.getParameters();
        if (parameterIndex < parameters.size()) {
            throw new IllegalArgumentException("parameterIndex >= " + parameters.size());
        }
        final Parameter param = parameters.get(parameterIndex);
        return getAnnotationNames(param);
    }

    @Override
    public String getReturnTypeName(final Method method) {
        return getTypeName(method.getTypeReference().getTarget());
    }

    @Override
    public String getFieldName(final Field field) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getFieldName(" + field + ")");
        }
        return field.getName();
    }

    @Override
    public Class getOfCreateClassObject(final File file) throws Exception {
        LOG.info("getOfCreateClassObject(" + file + ")");
        try {
            if (classFileCache != null) {
                return ((LoadingCache<Vfs.File, Class>) classFileCache).get(file);
            }
        } catch (Exception e) {
            // fallback
        }
        return createClassObject(file);
    }

    @Override
    public String getMethodModifier(final Method method) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getMethodModifier(" + method + ")");
        }
        final StringBuilder sb = new StringBuilder();
        final EList<Modifier> modifiers = method.getModifiers();
        for (final Modifier modifier : modifiers) {
            if (modifier instanceof Public) {
                sb.append("public ");
            }
            if (modifier instanceof Protected) {
                sb.append("protected ");
            }
            if (modifier instanceof Private) {
                sb.append("private ");
            }
            if (modifier instanceof Abstract) {
                sb.append("abstract ");
            }
            if (modifier instanceof Static) {
                sb.append("static ");
            }
            if (modifier instanceof Final) {
                sb.append("final ");
            }
            if (modifier instanceof Synchronized) {
                sb.append("synchronized ");
            }
            if (modifier instanceof Native) {
                sb.append("native ");
            }
            if (modifier instanceof Strictfp) {
                sb.append("strictfp ");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public String getMethodKey(final Class cls, final Method method) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getMethodKey(" + cls + ", " + method + ")");
        }
        return getMethodName(method) + "(" + Joiner.on(", ").join(getParameterNames(method)) + ")";
    }

    @Override
    public String getMethodFullKey(final Class cls, final Method method) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("getMethodFullKey(" + cls + ", " + method + ")");
        }
        return getClassName(cls) + "." + getMethodKey(cls, method);
    }

    @Override
    public boolean isPublic(final Object o) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("isPublic(" + o + ")");
        }
        final Modifier pub = ModifiersFactory.eINSTANCE.createPublic();
        if (o instanceof Class) {
            return hasModifier((Class) o, pub);
        }
        if (o instanceof Field) {
            return hasModifier((Field) o, pub);
        }
        if (o instanceof Method) {
            return hasModifier((Method) o, pub);
        }
        return false;
    }

    private boolean hasModifier(final AnnotableAndModifiable modifiable, final Modifier modifier) {
        for (final Modifier m : modifiable.getModifiers()) {
            if (m.equals(modifier)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getAnnotationNames(final AnnotableAndModifiable clasz) {
        final List<String> names = Lists.newArrayList();
        final EList<AnnotationInstanceOrModifier> aams = clasz.getAnnotationsAndModifiers();
        for (final AnnotationInstanceOrModifier aam : aams) {
            if (aam instanceof AnnotationInstance) {
                final AnnotationInstance ai = (AnnotationInstance) aam;
                final Classifier annotation = ai.getAnnotation();
                if (annotation == null) {
                    throw new IllegalStateException("Annotation is null: " + ai);
                }
                if (annotation.eIsProxy()) {
                    throw new IllegalStateException("Annotation is unresolved: " + annotation);
                }
                names.add(getFullQualifiedName(annotation));
            }
        }
        return names;
    }

    private String getFullQualifiedName(final Classifier classifier) {
        return classifier.getContainingCompilationUnit().getNamespacesAsString()
                + classifier.getName();
    }

    private Class findClass(final JavaSourceOrClassFileResource jres) {
        final TreeIterator<EObject> it = jres.getAllContents();
        while (it.hasNext()) {
            final Notifier notifier = it.next();
            if (notifier instanceof Class) {
                return (Class) notifier;
            }
        }
        return null;
    }

    private Class createClassObject(final Vfs.File file) {
        final Resource res = resourceSet.getResource(URI.createFileURI(file.toString()), true);
        if (res instanceof JavaSourceOrClassFileResource) {
            final JavaSourceOrClassFileResource jres = (JavaSourceOrClassFileResource) res;
            final Class clasz = findClass(jres);
            if (clasz == null) {
                throw new IllegalArgumentException("Class is not present in resource set: " + jres);
            }
            return clasz;
        }
        throw new IllegalStateException("No JavaSourceOrClassFileResource: " + file);
    }

    private String getTypeName(final Type type) {
        if (type instanceof PrimitiveType) {
            if (type instanceof org.emftext.language.java.types.Boolean) {
                return "boolean";
            } else if (type instanceof org.emftext.language.java.types.Byte) {
                return "byte";
            } else if (type instanceof org.emftext.language.java.types.Char) {
                return "char";
            } else if (type instanceof org.emftext.language.java.types.Double) {
                return "double";
            } else if (type instanceof org.emftext.language.java.types.Float) {
                return "float";
            } else if (type instanceof org.emftext.language.java.types.Int) {
                return "int";
            } else if (type instanceof org.emftext.language.java.types.Long) {
                return "long";
            } else if (type instanceof org.emftext.language.java.types.Short) {
                return "short";
            } else if (type instanceof org.emftext.language.java.types.Void) {
                return "void";
            }
        } else if (type instanceof Classifier) {
            final Classifier classifier = (Classifier) type;
            final String name = getFullQualifiedName(classifier);
            if (name.equals("java.lang.String")) {
                return "String";
            }
            return name;
        } else if (type instanceof AnonymousClass) {
            final AnonymousClass clasz = (AnonymousClass) type;
            // TODO Find better way to display anonymous class
            return clasz.toString();
        }
        throw new IllegalStateException("Unknown type: " + type);
    }
    
}
