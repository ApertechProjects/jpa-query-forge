package io.github.apertechprojects.queryforge.testenv.entity.finance;

import io.github.apertechprojects.queryforge.testenv.entity.User;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;

/** Like procurementservice's audited base entities: fields inherited by the query model. */
@MappedSuperclass
public abstract class AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User createdBy;

    private LocalDateTime createdDate;

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }
}
