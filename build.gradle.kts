// Plugins every project shares are declared once here, so all projects load them with the same class loader.
plugins {
    alias(libs.plugins.shadow) apply false
    id("io.github.fiftieshousewife.java-conventions") apply false
    id("io.github.fiftieshousewife.airgap-publishing") apply false
}

// nmcp comes onto the class path with build-logic, so it's applied by id here, without a version.
apply(plugin = "com.gradleup.nmcp.aggregation")

// Maven Central: every published part goes up as one deployment through the Central Portal, published once it passes
// the portal's checks. Needs centralPortalUsername and centralPortalPassword (a Central Portal user token).
configure<nmcp.NmcpAggregationExtension> {
    centralPortal {
        username = providers.gradleProperty("centralPortalUsername")
        password = providers.gradleProperty("centralPortalPassword")
        publishingType = "AUTOMATIC"
    }
}

dependencies {
    listOf(":agent", ":junit", ":maven-extension", ":gradle-plugin").forEach { "nmcpAggregation"(project(it)) }
}
