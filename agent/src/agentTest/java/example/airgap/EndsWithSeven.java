package example.airgap;

/** A program that ends its JVM with status 7, as a test runner does once its tests finish. */
public final class EndsWithSeven {

    static final int STATUS = 7;

    private EndsWithSeven() {
    }

    /**
     * Ends the JVM.
     *
     * @param arguments ignored
     */
    public static void main(final String[] arguments) {
        System.exit(STATUS);
    }
}
