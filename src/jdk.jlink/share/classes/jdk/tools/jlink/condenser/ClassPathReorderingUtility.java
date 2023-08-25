package jdk.tools.jlink.condenser;

import java.util.stream.Stream;

public class ClassPathReorderingUtility {

    public static Model insertAtBeginning(Model model, EntityKey.ContainerKey containerKey) {
        Stream<EntityKey.ContainerKey> reorderedClassPath = Stream.concat(Stream.of(containerKey), model.classPath());
        ModelUpdater updater = model.updater();

        /* Unclear how addToClassPath is implemented. I assume it adds the container at the end of existing classpath.
           Because of this, we remove the existing classpath (if any) before adding the new reordered one.
         */
        deleteExistingClassPath(updater, model.classPath());

        addClassPath(updater, reorderedClassPath);
        return model.apply(updater);
    }

    public static Model insertBeforeOnClassPath(Model model, EntityKey.ContainerKey before, EntityKey.ContainerKey insert) {
        ModelUpdater updater = model.updater();

        // TODO: remove
        // illustrating example
        // [1,2,3,4,5] insert before 3
        // first half -> [1,2]
        // second half -> [3,4,5]

        // returns longest prefix of elements that match the given predicate.
        Stream<EntityKey.ContainerKey> firstHalf = model.classPath().takeWhile(key -> ! key.equals(before));

        // drops the longest prefix of elements that satisfy the given predicate while returning the remaining elements
        Stream<EntityKey.ContainerKey> secondHalf = model.classPath().dropWhile(key -> ! key.equals(before));

        // first half + insert + second half
        Stream<EntityKey.ContainerKey> reorderedClassPath =
                Stream.concat(Stream.concat(firstHalf, Stream.of(insert)), secondHalf);

        deleteExistingClassPath(updater, model.classPath());
        addClassPath(updater, reorderedClassPath);
        return model.apply(updater);
    }

    public static Model insertAfterOnClassPath(Model model, EntityKey.ContainerKey after, EntityKey.ContainerKey insert) {
        ModelUpdater updater = model.updater();

        // TODO: remove
        // illustrating example
        // [1,2,3,4,5] insert after 3
        // [1,2] first half
        // [4,5] second half


        Stream<EntityKey.ContainerKey> firstHalf = model.classPath().takeWhile(key -> ! key.equals(after));
        Stream<EntityKey.ContainerKey> secondHalf = model.classPath().dropWhile(key -> ! key.equals(after))
                .dropWhile(key -> key.equals(after));

        // first half + after + insert + second half
        Stream<EntityKey.ContainerKey> reorderedClassPath = Stream.concat(
                Stream.concat(firstHalf, Stream.of(after, insert)), secondHalf);

        deleteExistingClassPath(updater, model.classPath());
        addClassPath(updater, reorderedClassPath);
        return model.apply(updater);
    }

    private static void deleteExistingClassPath(ModelUpdater modelUpdater, Stream<EntityKey.ContainerKey> containerKeyStream) {
        containerKeyStream.forEach(modelUpdater::removeFromClassPath);
    }

    private static void addClassPath(ModelUpdater modelUpdater, Stream<EntityKey.ContainerKey> containerKeyStream) {
        containerKeyStream.forEach(modelUpdater::addToClassPath);
    }
}
