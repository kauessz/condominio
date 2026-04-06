package com.example.condo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "assembly_agenda_item")
public class AssemblyAgendaItem {

    public enum ItemType {
        GENERAL_VOTE,
        OFFICE_ELECTION
    }

    public enum ResolutionStatus {
        NOT_APPLICABLE,
        PENDING,
        APPLIED,
        TIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assembly_id", nullable = false)
    private Long assemblyId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "requires_vote", nullable = false)
    private boolean requiresVote = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType = ItemType.GENERAL_VOTE;

    @Column(name = "office_name")
    private String officeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false)
    private ResolutionStatus resolutionStatus = ResolutionStatus.NOT_APPLICABLE;

    @Column(name = "winning_option_id")
    private Long winningOptionId;

    @Column(name = "resolved_at")
    private java.time.Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "applied_user_id")
    private Long appliedUserId;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssemblyId() { return assemblyId; }
    public void setAssemblyId(Long assemblyId) { this.assemblyId = assemblyId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isRequiresVote() { return requiresVote; }
    public void setRequiresVote(boolean requiresVote) { this.requiresVote = requiresVote; }

    public ItemType getItemType() { return itemType; }
    public void setItemType(ItemType itemType) { this.itemType = itemType; }

    public String getOfficeName() { return officeName; }
    public void setOfficeName(String officeName) { this.officeName = officeName; }

    public ResolutionStatus getResolutionStatus() { return resolutionStatus; }
    public void setResolutionStatus(ResolutionStatus resolutionStatus) { this.resolutionStatus = resolutionStatus; }

    public Long getWinningOptionId() { return winningOptionId; }
    public void setWinningOptionId(Long winningOptionId) { this.winningOptionId = winningOptionId; }

    public java.time.Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(java.time.Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }

    public Long getAppliedUserId() { return appliedUserId; }
    public void setAppliedUserId(Long appliedUserId) { this.appliedUserId = appliedUserId; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
