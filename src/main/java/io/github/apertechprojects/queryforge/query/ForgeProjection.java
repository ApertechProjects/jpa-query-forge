package io.github.apertechprojects.queryforge.query;

import io.github.apertechprojects.queryforge.internal.QueryRunner;
import io.github.apertechprojects.queryforge.internal.QuerySpec;
import io.github.apertechprojects.queryforge.internal.Projection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Query after {@code select}, like EF Core's {@code IQueryable<TResult>}: rows are projected onto a
 * {@code @ForgeModel} class or with a select lambda. Conditions and order come from the query it was created from.
 *
 * @param <R> result type
 */
public final class ForgeProjection<R> {

    private final QueryRunner<?> runner;
    private final QuerySpec spec;
    private final Projection<R> projection;

    ForgeProjection(QueryRunner<?> runner, QuerySpec spec, Projection<R> projection) {
        this.runner = runner;
        this.spec = spec;
        this.projection = projection;
    }

    /**
     * {@code SELECT DISTINCT} over the selected fields. Sort keys that are not selected are added to the select
     * list, as SQL requires; they can then make otherwise equal rows distinct.
     */
    public ForgeProjection<R> distinct() {
        return new ForgeProjection<>(runner, spec.withDistinct(), projection);
    }

    public ForgeProjection<R> page(int pageNumber, int pageSize) {
        ForgeQueryable.checkPage(pageNumber, pageSize);
        return new ForgeProjection<>(runner, spec.withRange(pageNumber * pageSize, pageSize), projection);
    }

    public ForgeProjection<R> take(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        return new ForgeProjection<>(runner, spec.withRange(spec.offset(), count), projection);
    }

    public List<R> toList() {
        return runner.project(spec, projection);
    }

    /**
     * Page with the total count of matching entities. Only page number and size of {@code pageable} are used.
     * With {@link #distinct()} the total still counts entities, not distinct rows.
     */
    public Page<R> toPage(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(runner.project(spec.withRange(null, null), projection));
        }
        return toPage(pageable.getPageNumber(), pageable.getPageSize());
    }

    public Page<R> toPage(int pageNumber, int pageSize) {
        ForgeQueryable.checkPage(pageNumber, pageSize);
        return runner.projectPage(spec, projection, pageNumber, pageSize);
    }

    public Optional<R> firstOrDefault() {
        return runner.project(spec.first(), projection).stream().findFirst();
    }

    /** Number of matching entities. */
    public long count() {
        return runner.count(spec);
    }

    public boolean any() {
        return runner.exists(spec);
    }
}
