package io.github.apertechprojects.queryforge.repository;

import java.util.List;
import java.util.function.Consumer;

public interface ListConsumer<Entity> extends Consumer<List<Entity>> {
    void accept(List<Entity> entityList);
}
