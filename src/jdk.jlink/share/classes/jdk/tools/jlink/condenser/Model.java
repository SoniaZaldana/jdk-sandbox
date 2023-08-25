package jdk.tools.jlink.condenser;

import java.util.stream.Stream;

public interface Model {
    Stream<EntityKey.ContainerKey> modules();
    Stream<EntityKey.ContainerKey> classPath();
    Stream<EntityKey.ClassKey> containerClasses(EntityKey.ContainerKey containerKey);
    Stream<EntityKey.ResourceKey> containerResources(EntityKey.ContainerKey containerKey);
    EntityKey.ContainerKind containerKind(EntityKey.ContainerKey containerKey);
    ModelUpdater updater();
    Model apply(ModelUpdater updater);

}
