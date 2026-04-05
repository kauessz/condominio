package com.example.condo.job;

import com.example.condo.entity.Reservation;
import com.example.condo.repo.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Job que marca como COMPLETED as reservas cujo end_datetime já passou.
 * Roda a cada 15 minutos.
 */
@Component
public class ReservationCompletionJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationCompletionJob.class);

    private final ReservationRepository reservationRepo;

    public ReservationCompletionJob(ReservationRepository reservationRepo) {
        this.reservationRepo = reservationRepo;
    }

    @Scheduled(fixedDelay = 900_000) // 15 min
    @Transactional
    public void run() {
        List<Reservation> past = reservationRepo.findApprovedAndPast(Instant.now());
        if (past.isEmpty()) return;
        for (Reservation r : past) {
            r.setStatus(Reservation.Status.COMPLETED);
        }
        reservationRepo.saveAll(past);
        log.info("ReservationCompletionJob: {} reservas marcadas como COMPLETED", past.size());
    }
}
