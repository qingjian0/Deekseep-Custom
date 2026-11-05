package ds.dsh.DeepSeek;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded, metadata-only inspection of a Class object already observed by a
 * trusted module-owned callback. This class never loads or initializes a host
 * class, invokes host methods, reads field values, or changes accessibility.
 */
final class RoiLoadedClassInspector {
    static final int MAX_METHODS = 128;
    static final int MAX_FIELDS = 128;
    static final int MAX_INTERFACES = 32;

    static final class Snapshot {
        final String className;
        final String classDescriptor;
        final String loaderScope;
        final String superclassName;
        final List<String> interfaces;
        final List<String> methods;
        final List<String> fields;
        final int methodCountReported;
        final int fieldCountReported;
        final boolean methodsTruncated;
        final boolean fieldsTruncated;
        final String inspectionStatus;

        Snapshot(String className, String classDescriptor, String loaderScope,
                 String superclassName, List<String> interfaces,
                 List<String> methods, List<String> fields,
                 int methodCountReported, int fieldCountReported,
                 boolean methodsTruncated, boolean fieldsTruncated,
                 String inspectionStatus) {
            this.className = className;
            this.classDescriptor = classDescriptor;
            this.loaderScope = loaderScope;
            this.superclassName = superclassName;
            this.interfaces = immutable(interfaces);
            this.methods = immutable(methods);
            this.fields = immutable(fields);
            this.methodCountReported = methodCountReported;
            this.fieldCountReported = fieldCountReported;
            this.methodsTruncated = methodsTruncated;
            this.fieldsTruncated = fieldsTruncated;
            this.inspectionStatus = inspectionStatus;
        }
    }

    private RoiLoadedClassInspector() {}

    static Snapshot inspect(Class<?> type) {
        if (type == null) {
            return new Snapshot("", "", "loader:none", "", Collections.<String>emptyList(),
                    Collections.<String>emptyList(), Collections.<String>emptyList(),
                    0, 0, false, false, "rejected_null_class");
        }

        ClassLoader loader = safeClassLoader(type);
        String scope = RoiCandidateIdentity.forClassLoader(loader);
        String name = safeName(type);
        String descriptor = typeDescriptor(type);
        String parent = "";
        List<String> interfaces = new ArrayList<String>();
        List<String> methods = new ArrayList<String>();
        List<String> fields = new ArrayList<String>();
        int methodCount = 0;
        int fieldCount = 0;
        boolean methodsTruncated = false;
        boolean fieldsTruncated = false;
        String status = "metadata_only";

        try {
            Class<?> superclass = type.getSuperclass();
            parent = superclass == null ? "" : safeName(superclass);
        } catch (LinkageError | SecurityException error) {
            status = "partial_superclass_unavailable";
        }

        try {
            Class<?>[] values = type.getInterfaces();
            int limit = Math.min(values.length, MAX_INTERFACES);
            for (int i = 0; i < limit; i++) interfaces.add(safeName(values[i]));
            if (values.length > limit) status = "partial_interfaces_truncated";
        } catch (LinkageError | SecurityException error) {
            status = "partial_interfaces_unavailable";
        }

        try {
            Method[] values = type.getDeclaredMethods();
            methodCount = values.length;
            int limit = Math.min(values.length, MAX_METHODS);
            for (int i = 0; i < limit; i++) methods.add(methodDescriptor(values[i]));
            methodsTruncated = values.length > limit;
        } catch (LinkageError | SecurityException error) {
            status = "partial_methods_unavailable";
        }

        try {
            Field[] values = type.getDeclaredFields();
            fieldCount = values.length;
            int limit = Math.min(values.length, MAX_FIELDS);
            for (int i = 0; i < limit; i++) fields.add(fieldDescriptor(values[i]));
            fieldsTruncated = values.length > limit;
        } catch (LinkageError | SecurityException error) {
            status = "partial_fields_unavailable";
        }

        return new Snapshot(name, descriptor, scope, parent, interfaces, methods, fields,
                methodCount, fieldCount, methodsTruncated, fieldsTruncated, status);
    }

    private static ClassLoader safeClassLoader(Class<?> type) {
        try {
            return type.getClassLoader();
        } catch (SecurityException denied) {
            return null;
        }
    }

    private static String safeName(Class<?> type) {
        try {
            return type.getName();
        } catch (LinkageError | SecurityException error) {
            return "unavailable";
        }
    }

    private static String methodDescriptor(Method method) {
        StringBuilder out = new StringBuilder();
        out.append(Modifier.toString(method.getModifiers())).append(' ')
                .append(typeDescriptor(method.getReturnType())).append(' ')
                .append(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) out.append(',');
            out.append(typeDescriptor(params[i]));
        }
        return out.append(')').toString();
    }

    private static String fieldDescriptor(Field field) {
        return Modifier.toString(field.getModifiers()) + " "
                + typeDescriptor(field.getType()) + " " + field.getName();
    }

    private static String typeDescriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        return "?";
    }

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
