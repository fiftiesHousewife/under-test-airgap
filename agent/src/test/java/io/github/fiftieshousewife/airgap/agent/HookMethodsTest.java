package io.github.fiftieshousewife.airgap.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.util.Arrays;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

class HookMethodsTest {

    @Test
    void picksTheInnermostLookupOnly() {
        final int most = Arrays.stream(InetAddress.class.getDeclaredMethods())
                .filter(method -> "getAllByName0".equals(method.getName()))
                .mapToInt(Method::getParameterCount).max().orElse(-1);
        final MethodList<MethodDescription.InDefinedShape> matched = TypeDescription.ForLoadedType.of(InetAddress.class)
                .getDeclaredMethods().filter(HookMethods.innermostLookup());

        assertAll(
                () -> assertThat(matched).singleElement().satisfies(method -> assertAll(
                        () -> assertThat(method.getName()).isEqualTo("getAllByName0"),
                        () -> assertThat(method.getParameters()).hasSize(most),
                        () -> assertThat(Modifier.isPrivate(method.getModifiers())).isTrue())),
                () -> assertThat(TypeDescription.ForLoadedType.of(Object.class).getDeclaredMethods()
                        .filter(HookMethods.innermostLookup())).isEmpty());
    }

    @Test
    void picksTheProcessStartEveryRouteReaches() {
        final MethodList<MethodDescription.InDefinedShape> matched =
                TypeDescription.ForLoadedType.of(ProcessBuilder.class).getDeclaredMethods()
                        .filter(HookMethods.processStart());

        assertThat(matched).as("from JDK 9, start(Redirect[]), which start() and startPipeline reach")
                .singleElement().satisfies(method -> assertThat(method.getParameters()).hasSize(1));
    }
}
