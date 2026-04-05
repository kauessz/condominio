package com.example.condo.job;

import com.example.condo.config.CondoHubProperties;
import com.example.condo.entity.Visitor;
import com.example.condo.repo.VisitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Job que expira visitas PENDING que excederam o timeout configurado.
 *
 * Executa a cada `condohub.visitor.expiration-check-interval-ms` milissegundos
 * (padrão: 60 segundos).
 *
 * Visitas expiradas passam para REJECTED com motivo "Tempo de aprovação expirado".
 *
 * Nota: usa o timeout global configurado em application.yml. Em fases futuras
 * pode ser ajustado por condomínio via Condominium.visitorPendingTimeoutMinutes.
 */
@Component
public class VisitorExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(VisitorExpirationJob.class);

    private final VisitorRepository visitorRepository;
    private final CondoHubProperties properties;

    public VisitorExpirationJob(
        VisitorRepository visitorRepository,
        CondoHubProperties properties
    ) {
        this.visitorRepository = visitorRepository;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${condohub.visitor.expiration-check-interval-ms:60000}")
    @Transactional
    public void expirePendingVisitors() {
        int timeoutMinutes = properties.getVisitor().getPendingTimeoutMinutes();
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);

        List<Visitor> expired = visitorRepository.findPendingExpired(cutoff);

        if (expired.isEmpty()) return;

        expired.forEach(v -> {
            v.setStatus(Visitor.Status.REJECTED);
            v.setRejectionReason("Tempo de aprovação expirado (" + timeoutMinutes + " min)");
        });

        visitorRepository.saveAll(expired);

        log.info("[VisitorExpirationJob] {} visita(s) expirada(s) (timeout={}min)",
            expired.size(), timeoutMinutes);

        // TODO Fase 2: notificar portaria via WebSocket/push que visita expirou
    }
}
