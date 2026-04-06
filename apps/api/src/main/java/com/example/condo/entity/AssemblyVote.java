package com.example.condo.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "assembly_vote")
public class AssemblyVote {

    public enum VoteValue { YES, NO, ABSTAIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agenda_item_id", nullable = false)
    private Long agendaItemId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "option_id")
    private Long optionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_value")
    private VoteValue voteValue;

    @Column(name = "voted_by")
    private Long votedBy;

    @Column(name = "voted_at")
    private Instant votedAt;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAgendaItemId() { return agendaItemId; }
    public void setAgendaItemId(Long agendaItemId) { this.agendaItemId = agendaItemId; }

    public Long getUnitId() { return unitId; }
    public void setUnitId(Long unitId) { this.unitId = unitId; }

    public Long getOptionId() { return optionId; }
    public void setOptionId(Long optionId) { this.optionId = optionId; }

    public VoteValue getVoteValue() { return voteValue; }
    public void setVoteValue(VoteValue voteValue) { this.voteValue = voteValue; }

    public Long getVotedBy() { return votedBy; }
    public void setVotedBy(Long votedBy) { this.votedBy = votedBy; }

    public Instant getVotedAt() { return votedAt; }
    public void setVotedAt(Instant votedAt) { this.votedAt = votedAt; }
}
