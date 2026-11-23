package example.pitsample;

/** Code for PIT to plant bugs in, while the airgap agent guards the JVMs PIT starts. */
public final class Discount {

    private Discount() {
    }

    /**
     * A price with a discount taken off.
     *
     * @param price the price, in pence
     * @param percent the discount
     * @return the price after the discount
     */
    public static int priceAfter(final int price, final int percent) {
        return price - price * percent / 100;
    }
}
