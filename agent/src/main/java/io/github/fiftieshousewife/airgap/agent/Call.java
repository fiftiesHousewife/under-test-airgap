package io.github.fiftieshousewife.airgap.agent;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/** One risky call, as the report records it: one line of {@code schema/airgap-report.schema.json}. */
@Getter
@Builder
public final class Call {

    @NonNull
    private final Kind kind;
    @NonNull
    private final String target;
    @NonNull
    private final String outcome;
    private final String allowedBy;
    private final String proxy;
    @NonNull
    private final String run;
    @NonNull
    private final String javaVersion;
    private final String test;
    @NonNull
    private final String caller;
    @Builder.Default
    private final List<String> stack = Collections.emptyList();

    /** @return the call as one line of JSON, without a line ending */
    public String toJson() {
        return new Json()
                .field("kind", kind.getReportName())
                .field("target", target)
                .field("outcome", outcome)
                .field("allowed_by", allowedBy)
                .field("proxy", proxy)
                .object("run", new Json().field("kind", run).field("java_version", javaVersion))
                .field("test", test)
                .field("caller", caller)
                .array("stack", stack)
                .toString();
    }
}
