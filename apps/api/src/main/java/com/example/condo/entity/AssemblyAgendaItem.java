package com.example.condo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "assembly_agenda_item")
public class AssemblyAgendaItem {

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

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
