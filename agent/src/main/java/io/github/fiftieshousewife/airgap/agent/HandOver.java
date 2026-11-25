package io.github.fiftieshousewife.airgap.agent;

import java.util.concurrent.Executor;

/**
 * A task one thread hands to another, carrying the test running where it was handed over, what that test allows,
 * and the line of the project's code behind it. The JDK's HTTP client runs every request on its own threads; its
 * tasks are handed over this way, so a call it makes is allowed, named and blamed as the code that sent the request.
 * The test still ends for the task when it ends where it ran: a task run later sees no test.
 */
public final class HandOver implements Runnable {

    private static final ThreadLocal<String> CALLER = new ThreadLocal<>();

    private final Object scope;
    private final String caller;
    private final Runnable task;

    private HandOver(final Object scope, final String caller, final Runnable task) {
        this.scope = scope;
        this.caller = caller;
        this.task = task;
    }

    /**
     * Called by the hook on the HTTP client's executor.
     *
     * @param task the task being handed over
     * @return the task, carrying this thread's test and caller, or the task itself if there is nothing to carry
     */
    public static Runnable carry(final Runnable task) {
        if (task == null || task instanceof HandOver) {
            return task;
        }
        final Object scope = RunningTests.toHandOver();
        final String caller = callerHere();
        return scope == null && caller == null ? task : new HandOver(scope, caller, task);
    }

    /**
     * Called by the hook on the HTTP client's {@code sendAsync}, with the executor that will run the request's tasks.
     *
     * @param executor the executor, or {@code null} when the client uses its own, whose tasks are handed over anyway
     * @return an executor whose every task carries this thread's test and caller, as they are now
     */
    public static Executor carryingTo(final Executor executor) {
        final Object scope = RunningTests.toHandOver();
        final String caller = callerHere();
        if (executor == null || scope == null && caller == null) {
            return executor;
        }
        return task -> executor.execute(task instanceof HandOver ? task : new HandOver(scope, caller, task));
    }

    // Returns the caller a task running on this thread was handed over with, or null
    static String carriedCaller() {
        return CALLER.get();
    }

    private static String callerHere() {
        final String carried = CALLER.get();
        if (carried != null) {
            return carried;
        }
        final Decider decider = Gate.currentDecider();
        return decider == null ? null : decider.callerOf(new Throwable().getStackTrace());
    }

    @Override
    public void run() {
        final Object outerScope = RunningTests.adopt(scope);
        final String outerCaller = CALLER.get();
        CALLER.set(caller);
        try {
            task.run();
        } finally {
            RunningTests.adopt(outerScope);
            if (outerCaller == null) {
                CALLER.remove();
            } else {
                CALLER.set(outerCaller);
            }
        }
    }
}
