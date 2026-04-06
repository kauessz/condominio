package com.example.condo.repo;

import com.example.condo.entity.AssemblyVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssemblyVoteRepository extends JpaRepository<AssemblyVote, Long> {

    Optional<AssemblyVote> findByAgendaItemIdAndUnitId(Long agendaItemId, Long unitId);

    boolean existsByAgendaItemIdAndUnitId(Long agendaItemId, Long unitId);

    List<AssemblyVote> findByAgendaItemId(Long agendaItemId);

    @Query("select count(v) from AssemblyVote v where v.agendaItemId = :itemId and v.voteValue = :value")
    long countByItemAndValue(@Param("itemId") Long itemId, @Param("value") AssemblyVote.VoteValue value);

    @Query("select count(v) from AssemblyVote v where v.agendaItemId = :itemId")
    long countByItem(@Param("itemId") Long itemId);

    @Query("select v.optionId, count(v) from AssemblyVote v where v.agendaItemId = :itemId and v.optionId is not null group by v.optionId")
    List<Object[]> countByItemGroupedByOption(@Param("itemId") Long itemId);
}
