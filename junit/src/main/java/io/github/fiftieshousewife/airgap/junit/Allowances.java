package io.github.fiftieshousewife.airgap.junit;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

// What AllowCalls allows a test class: its own, its base classes' and the classes it is nested in.
final class Allowances {

    private Allowances() {
    }

    // testClass: the test class, or null if it couldn't be loaded
    // Returns the entries of every AllowCalls on it, its base classes and its enclosing classes, and theirs
    static String[] of(final Class<?> testClass) {
        final Set<String> entries = new LinkedHashSet<>();
        for (Class<?> enclosing = testClass; enclosing != null; enclosing = enclosing.getEnclosingClass()) {
            for (Class<?> type = enclosing; type != null && type != Object.class; type = type.getSuperclass()) {
                addFrom(entries, type);
            }
        }
        return entries.toArray(new String[0]);
    }

    // element: a test method
    // Returns the entries of the AllowCalls on it, if any
    static String[] on(final AnnotatedElement element) {
        final Set<String> entries = new LinkedHashSet<>();
        addFrom(entries, element);
        return entries.toArray(new String[0]);
    }

    private static void addFrom(final Set<String> entries, final AnnotatedElement element) {
        final AllowCalls allowed = element.getAnnotation(AllowCalls.class);
        if (allowed != null) {
            entries.addAll(Arrays.asList(allowed.value()));
        }
    }
}
