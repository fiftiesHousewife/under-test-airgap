package io.github.fiftieshousewife.airgap.agent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A JUnit 4 test's name from its Description's text, "method(class)", read the way JUnit 4 itself reads it, so the
// agent needs no JUnit. A parameterized test keeps its parameters' name: "testConnect[external](com.example.Test)" is
// com.example.Test.testConnect[external]. A description that isn't a method, such as a suite's, is named as it reads.
final class JUnit4Names {

    private static final Pattern METHOD_AND_CLASS = Pattern.compile("([\\s\\S]*)\\((.*)\\)");

    private JUnit4Names() {
    }

    static String testName(final String description) {
        final Matcher matcher = METHOD_AND_CLASS.matcher(description);
        return matcher.matches() ? matcher.group(2) + "." + matcher.group(1) : description;
    }
}
