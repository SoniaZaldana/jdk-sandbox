package jdk.tools.jlink.condenser;

public interface ModelUpdater {
    ModelUpdater addToClassPath(EntityKey.ContainerKey containerKey);
    ModelUpdater removeFromClassPath(EntityKey.ContainerKey containerKey);

}
