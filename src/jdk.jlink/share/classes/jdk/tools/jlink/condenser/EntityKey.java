package jdk.tools.jlink.condenser;

import java.lang.constant.ClassDesc;

sealed interface EntityKey {

    record ClassPathKey() implements EntityKey {}

    record ContainerKey(String name) implements EntityKey { }

    record ClassKey(ContainerKey container, ClassDesc desc) implements EntityKey { }

    record ResourceKey(ContainerKey container, String name) implements EntityKey { }

    enum ContainerKind {
        JAR, MODULAR_JAR, JMOD
    }

}
