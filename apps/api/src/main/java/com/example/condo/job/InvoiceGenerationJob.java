package com.example.condo.job;

import com.example.condo.entity.Invoice;
import com.example.condo.repo.FinancialConfigRepository;
import com.example.condo.repo.InvoiceRepository;
import com.example.condo.service.FinancialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Job de geração mensal de faturas.
 * Roda todo dia 1 do mês às 08:00.
 *
 * Também marca como OVERDUE as faturas vencidas (roda diariamente às 06:00).
 */
@Component
public class InvoiceGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(InvoiceGenerationJob.class);

    private final FinancialConfigRepository configRepo;
    private final InvoiceRepository invoiceRepo;
    private final FinancialService financialService;

    public InvoiceGenerationJob(FinancialConfigRepository configRepo,
                                 InvoiceRepository invoiceRepo,
                                 FinancialService financialService) {
        this.configRepo = configRepo;
        this.invoiceRepo = invoiceRepo;
        this.financialService = financialService;
    }

    /** Gera faturas do mês corrente para todos os condomínios configurados */
    @Scheduled(cron = "0 0 8 1 * *") // Dia 1 de cada mês às 08:00
    @Transactional
    public void generateMonthlyInvoices() {
        YearMonth month = YearMonth.now();
        log.info("InvoiceGenerationJob: gerando faturas para {}", month);

        configRepo.findAll().forEach(config -> {
            try {
                List<Invoice> invoices = financialService.generateMonthlyInvoices(
                    config.getCondominiumId(), config.getTenantId(), month);
                log.info("Geradas {} faturas para condomínio {}", invoices.size(), config.getCondominiumId());
            } catch (Exception e) {
                log.error("Erro ao gerar faturas para condomínio {}: {}", config.getCondominiumId(), e.getMessage());
            }
        });
    }

    /** Marca como OVERDUE faturas vencidas que ainda estão PENDING */
    @Scheduled(cron = "0 0 6 * * *") // Todo dia às 06:00
    @Transactional
    public void markOverdueInvoices() {
        List<Invoice> overdue = invoiceRepo.findOverdue(LocalDate.now());
        if (overdue.isEmpty()) return;
        for (Invoice inv : overdue) {
            inv.setStatus(Invoice.Status.OVERDUE);
        }
        invoiceRepo.saveAll(overdue);
        log.info("InvoiceOverdueJob: {} faturas marcadas como OVERDUE", overdue.size());
    }
}
