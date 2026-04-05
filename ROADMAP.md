# Roadmap

## Proxima fase

### Notificacoes
- centralizar eventos de dominio relevantes para visitantes, reservas, OS, assembleias e financeiro
- definir canais iniciais por configuracao: e-mail, in-app e webhook futuro
- evitar acoplamento direto do service com provider externo

### Anexos e arquivos
- introduzir modelo simples de metadados com tenant_id, condominium_id, entidade dona e autor
- separar upload temporario de vinculacao definitiva
- preparar storage abstrato para disco local em dev e objeto em producao

### Dashboards e metricas
- consolidar consultas agregadas por modulo antes de desenhar widgets
- criar camada de leitura dedicada para indicadores operacionais
- priorizar metricas de entrada/saida, reservas, inadimplencia e OS em atraso

### Marketplace de servicos
- modelar catalogo, fornecedor, solicitacao e status sem misturar com work orders internas
- prever aprovacao e rastreabilidade por condominio

### Financeiro avancado
- expandir configuracoes por condominio para multa, juros, desconto e regras de rateio
- preparar integracao de cobranca sem acoplar controller diretamente ao gateway
- adicionar conciliacao e eventos de pagamento assíncronos

### Mobile e notificacoes push
- manter backend como source of truth
- estabilizar autorizacao por escopo e trilha de auditoria antes de publicar app
- expor contratos DTO estaveis para visitas, reservas, boletos e assembleias

## Preparacao tecnica recomendada
- continuar migracao de regras de autorizacao de verbos HTTP para permissoes mais semanticas
- adicionar endpoint de consulta de auditoria com filtros por tenant, condominio, entidade e periodo
- reforcar concorrencia em reservas e sorteios de vagas
- ampliar cobertura de testes backend para modulos novos e auditoria
