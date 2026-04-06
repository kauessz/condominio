package com.example.condo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "assembly_agenda_option")
public class AssemblyAgendaOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agenda_item_id", nullable = false)
    private Long agendaItemId;

    @Column(name = "candidate_name", nullable = false)
    private String candidateName;

    @Column(name = "candidate_user_id")
    private Long candidateUserId;

    @Column(name = "candidate_unit_label")
    private String candidateUnitLabel;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAgendaItemId() { return agendaItemId; }
    public void setAgendaItemId(Long agendaItemId) { this.agendaItemId = agendaItemId; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public Long getCandidateUserId() { return candidateUserId; }
    public void setCandidateUserId(Long candidateUserId) { this.candidateUserId = candidateUserId; }

    public String getCandidateUnitLabel() { return candidateUnitLabel; }
    public void setCandidateUnitLabel(String candidateUnitLabel) { this.candidateUnitLabel = candidateUnitLabel; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
