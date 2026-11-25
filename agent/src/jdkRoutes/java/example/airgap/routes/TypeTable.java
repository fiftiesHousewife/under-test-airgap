package example.airgap.routes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.jar.asm.Opcodes;

// Every class read from the JDK: what each declares, its supertypes, and which of its methods are the JDK's public
// API. A method is named owner.name(descriptor), with the owner in internal form (java/net/Socket). JDK 8 has no
// modules, so there the public API is the Java SE packages and the few JDK ones it supports (JDK_8_API).
final class TypeTable {

    private static final List<String> JDK_8_API = Collections.unmodifiableList(Arrays.asList("java/", "javax/",
            "org/ietf/", "org/omg/", "com/sun/net/httpserver", "com/sun/nio/sctp", "jdk/net"));

    private final Map<String, Type> types = new HashMap<>();
    private final Set<String> exportedPackages = new HashSet<>();
    private final Map<String, List<String>> ancestorsOf = new HashMap<>();

    // What one class declares: its methods, by name and descriptor, with their access flags.
    static final class Type {
        final String name;
        final String superName;
        final List<String> interfaces = new ArrayList<>();
        final boolean isPublic;
        final Map<String, Integer> methods = new HashMap<>();

        Type(final String name, final String superName, final boolean isPublic) {
            this.name = name;
            this.superName = superName;
            this.isPublic = isPublic;
        }
    }

    void add(final Type type) {
        types.put(type.name, type);
    }

    void export(final String packageName) {
        exportedPackages.add(packageName);
    }

    Iterable<Type> all() {
        return types.values();
    }

    Type get(final String name) {
        return types.get(name);
    }

    // Returns the method a call on owner reaches, found up its supertypes, or null
    String resolve(final String owner, final String method) {
        if (declares(owner, method)) {
            return owner + "." + method;
        }
        for (final String ancestor : ancestors(owner)) {
            if (declares(ancestor, method)) {
                return ancestor + "." + method;
            }
        }
        return null;
    }

    boolean declares(final String owner, final String method) {
        final Type type = types.get(owner);
        return type != null && type.methods.containsKey(method);
    }

    List<String> ancestors(final String name) {
        return ancestorsOf.computeIfAbsent(name, this::findAncestors);
    }

    private List<String> findAncestors(final String name) {
        final List<String> found = new ArrayList<>();
        final List<String> queue = new ArrayList<>(Collections.singletonList(name));
        while (!queue.isEmpty()) {
            for (final String parent : parentsOf(queue.remove(0))) {
                if (!found.contains(parent)) {
                    found.add(parent);
                    queue.add(parent);
                }
            }
        }
        return found;
    }

    private List<String> parentsOf(final String name) {
        final Type type = types.get(name);
        if (type == null) {
            return Collections.emptyList();
        }
        final List<String> parents = new ArrayList<>(type.interfaces);
        if (type.superName != null) {
            parents.add(type.superName);
        }
        return parents;
    }

    List<String> methodsNamed(final String owner, final String name) {
        final List<String> found = new ArrayList<>();
        final Type type = types.get(owner);
        for (final String method : type == null ? Collections.<String>emptySet() : type.methods.keySet()) {
            if (method.startsWith(name + "(")) {
                found.add(owner + "." + method);
            }
        }
        return found;
    }

    // Returns whether a method is abstract: an interface's or an abstract class's, run by whatever implements it
    boolean isAbstract(final String method) {
        final Integer access = accessOf(method);
        return access != null && (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    private Integer accessOf(final String method) {
        final int dot = method.indexOf('.');
        final Type type = types.get(method.substring(0, dot));
        return type == null ? null : type.methods.get(method.substring(dot + 1));
    }

    // Whether a method is part of the JDK's public API: public or protected, in a public class, in a package its
    // module exports to everyone (on JDK 8, a package of the Java SE platform).
    boolean isApi(final String method) {
        final Type type = types.get(method.substring(0, method.indexOf('.')));
        final Integer access = accessOf(method);
        if (type == null || access == null || !type.isPublic) {
            return false;
        }
        final String packageName = type.name.substring(0, Math.max(type.name.lastIndexOf('/'), 0));
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 && isExported(packageName);
    }

    private boolean isExported(final String packageName) {
        if (!exportedPackages.isEmpty()) {
            return exportedPackages.contains(packageName);
        }
        for (final String prefix : JDK_8_API) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
