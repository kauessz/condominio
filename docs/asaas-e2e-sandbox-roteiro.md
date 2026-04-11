# Roteiro E2E — Asaas Sandbox (CondoHub)

> Atualizado: abril/2026 — requer Railway com ASAAS_ENABLED=true

---

## Pré-requisitos

```env
# Variáveis no Railway (Settings → Variables)
ASAAS_ENABLED=true
ASAAS_BASE_URL=https://sandbox.asaas.com/api/v3
ASAAS_API_KEY_CONDO_{ID}=$aact_hmlg_000...   # substituir {ID} pelo ID real
```

**Descobrir o ID do condomínio Bossa Nova:**
```sql
SELECT id, name FROM condominiums WHERE name = 'Bossa Nova';
```
Exemplo: se retornar `id = 2`, a variável será `ASAAS_API_KEY_CONDO_2`.

---

## Passo 1 — Verificar API Key (validação imediata)

```bash
API_KEY="$aact_hmlg_000MzkwODA2MWY2OGM3MWRlMDU2NWM3MzJlNzZmNGZhZGY6Ojk5NGQzN2UwLTc1OTgtNDIyZS04OTUzLTYxZGIzZWUwZGMyMTo6JGFhY2hfMWJjZWQwMTctYmI1Mi00MGUyLWI3YjEtYjM5NDkwODVlNTRl"

curl -s -X GET "https://sandbox.asaas.com/api/v3/myAccount" \
  -H "access_token: $API_KEY" | jq '{name, cpfCnpj, email}'
```

**Esperado:** `200 OK` com os dados da conta sandbox.

---

## Passo 2 — Login no CondoHub (obter JWT)

```bash
DOMAIN="https://seu-projeto.railway.app"

TOKEN=$(curl -s -X POST "$DOMAIN/api/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Tenant: bossanova" \
  -d '{"email":"admin@bossanova.com","password":"Admin@2026"}' \
  | jq -r '.token')

echo "JWT: $TOKEN"
```

---

## Passo 3 — Descobrir morador e unidade

```bash
CONDO_ID=2   # ajustar conforme o banco

# Listar unidades do Bossa Nova
curl -s "$DOMAIN/api/units?condominiumId=$CONDO_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant: bossanova" | jq '[.[] | {id, number, block}]'

# Listar moradores
curl -s "$DOMAIN/api/residents?condominiumId=$CONDO_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant: bossanova" | jq '[.[] | {id, name, email, unitId}]'
```

---

## Passo 4 — Lançar cobrança para uma unidade

```bash
UNIT_ID=<id_da_unidade>   # pegar do passo 3

curl -s -X POST "$DOMAIN/api/financial/invoices/launch" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant: bossanova" \
  -H "Content-Type: application/json" \
  -d '{
    "condominiumId": '"$CONDO_ID"',
    "launchMode": "ONE_TIME",
    "scope": "SINGLE_UNIT",
    "chargeType": "EXTRA",
    "apportionmentMode": "PER_UNIT",
    "billingType": "BOLETO",
    "amount": 300.00,
    "title": "Taxa extra — Teste Asaas",
    "referenceMonth": "2026-04",
    "dueDate": "2026-04-30",
    "unitId": '"$UNIT_ID"'
  }' | jq '{createdCount, skippedCount}'
```

---

## Passo 5 — Buscar a invoice criada e gerar cobrança externa

```bash
# Listar invoices da unidade
INVOICE_ID=$(curl -s "$DOMAIN/api/financial/invoices?condominiumId=$CONDO_ID&unitId=$UNIT_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant: bossanova" \
  | jq '[.content[] | select(.status == "PENDING")] | first | .id')

echo "Invoice ID: $INVOICE_ID"

# Gerar cobrança externa no Asaas (billingType no body é opcional — usa o da invoice)
curl -s -X POST "$DOMAIN/api/financial/invoices/$INVOICE_ID/external-charge" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant: bossanova" \
  -H "Content-Type: application/json" \
  -d '{"billingType": "BOLETO"}' \
  | jq '{id, status, externalChargeId, boletoUrl}'
```

**Esperado:** invoice com `status = EXTERNAL_CREATED`, `externalChargeId` preenchido, `boletoUrl` válida.

**Conferir no painel Asaas Sandbox → Cobranças:** a cobrança deve aparecer com status "Aguardando".

---

## Passo 6 — Configurar Webhook no painel Asaas Sandbox

1. Acesse `https://sandbox.asaas.com` → **Configurações → Integrações → Webhooks → Adicionar**
2. Preencha:
   - **URL:** `https://SEU_DOMINIO/api/financial/webhooks/asaas/$CONDO_ID`
   - **Eventos:** `PAYMENT_RECEIVED`, `PAYMENT_CONFIRMED`, `PAYMENT_OVERDUE`, `PAYMENT_DELETED`, `PAYMENT_REFUNDED`
   - **Token de autenticação:** copie o token gerado pelo Asaas
3. No CondoHub (Financeiro → Configurações → Token do Webhook Asaas): cole o mesmo token → Salvar

---

## Passo 7 — Simular pagamento e verificar webhook

No painel Asaas Sandbox:
1. Vá em **Cobranças** → localize a cobrança criada no Passo 5
2. Clique em **Simular Pagamento** → Confirmar

Verificar resultado no CondoHub:

```bash
# Aguardar ~5s e buscar a invoice novamente
curl -s "$DOMAIN/api/financial/invoices/$INVOICE_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant: bossanova" \
  | jq '{id, status, paidAt, externalStatus, lastWebhookAt}'
```

**Esperado:** `status = PAID`, `paidAt` preenchido, `externalStatus = RECEIVED`.

Verificar logs webhook no painel Asaas: **Integrações → Webhooks → Histórico** — deve mostrar chamada com `200 OK`.

---

## Passo 8 — Repetir com Pix

```bash
INVOICE_ID_PIX=$(curl -s -X POST "$DOMAIN/api/financial/invoices/launch" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant: bossanova" \
  -H "Content-Type: application/json" \
  -d '{
    "condominiumId": '"$CONDO_ID"',
    "launchMode": "ONE_TIME",
    "scope": "SINGLE_UNIT",
    "chargeType": "EXTRA",
    "apportionmentMode": "PER_UNIT",
    "billingType": "PIX",
    "amount": 150.00,
    "title": "Taxa extra PIX — Teste Asaas",
    "referenceMonth": "2026-04",
    "dueDate": "2026-04-30",
    "unitId": '"$UNIT_ID"'
  }' | jq -r '.invoices[0].id // empty')

# Gerar cobrança Pix
curl -s -X POST "$DOMAIN/api/financial/invoices/$INVOICE_ID_PIX/external-charge" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant: bossanova" \
  -H "Content-Type: application/json" \
  -d '{"billingType": "PIX"}' \
  | jq '{id, status, pixQrCode: (.pixQrCode != null), pixCopyPaste}'
```

**Esperado:** `pixQrCode` com base64, QR Code visível no detalhe da invoice no frontend.

---

## Checklist de validação E2E

- [ ] `GET /myAccount` retorna 200 com dados da conta sandbox
- [ ] Invoice criada com `launchCharges`
- [ ] `POST /external-charge` retorna `externalChargeId` e `boletoUrl`
- [ ] Cobrança aparece no painel Asaas Sandbox
- [ ] Webhook configurado com URL e token corretos
- [ ] Pagamento simulado → invoice muda para `PAID`
- [ ] Evento aparece na Linha do Tempo da invoice no frontend
- [ ] Logs de webhook no Asaas mostram `200 OK`
- [ ] Cobrança Pix com QR Code funcionando
