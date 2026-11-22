// Plugins every project shares are declared once here, so all projects load them with the same class loader.
plugins {
    alias(libs.plugins.shadow) apply false
    id("io.github.fiftieshousewife.java-conventions") apply false
}
