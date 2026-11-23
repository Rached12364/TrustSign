package com.trustsign.audit;

import com.trustsign.common.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Une ligne du journal d'audit : qui a fait quoi, quand. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant time;
    private String username;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String action;

    @Column(length = 1000)
    private String detail;

    private Boolean grounded;
    private int sourceCount;

    protected AuditEvent() {}

    public AuditEvent(String username, Role role, String action, String detail, Boolean grounded, int sourceCount) {
        this.time = Instant.now();
        this.username = username;
        this.role = role;
        this.action = action;
        this.detail = detail == null ? "" : detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        this.grounded = grounded;
        this.sourceCount = sourceCount;
    }

    public Long getId() { return id; }
    public Instant getTime() { return time; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Boolean getGrounded() { return grounded; }
    public int getSourceCount() { return sourceCount; }
}
