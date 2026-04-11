# Guia de Configuração — Asaas Sandbox (CondoHub)

> Versão: abril/2026 — CondoHub Financial Module

---

## O que é o Asaas Sandbox?

O Asaas Sandbox é um ambiente de homologação totalmente isolado da produção.
Cobranças geradas no Sandbox **nunca movimentam dinheiro real**.
Use-o para testar a integração do CondoHub antes de ativar a API Key de produção.

---

## 1. Criar conta no Sandbox

1. Acesse [https://sandbox.asaas.com](https://sandbox.asaas.com).
2. Clique em **Criar conta** e preencha os dados (CPF/CNPJ fictícios são aceitos no Sandbox).
3. Confirme o e-mail de boas-vindas.
4. Ao entrar, você estará no ambiente sandbox — o banner laranja no topo confirma isso.

---

## 2. Obter a API Key

1. No menu lateral do Sandbox, vá em **Configurações → Integrações → Chaves de API**.
2. Clique em **Gerar nova chave**.
3. Copie o valor gerado — ele começa com `$aact_` (sandbox) ou `$aatp_` (produção).
4. Configure essa chave na variável de ambiente do CondoHub:

```env
# Chave global (todos os condomínios compartilham a mesma conta Asaas)
ASAAS_API_KEY=$aact_xxxxxxxxxxxxxxxxxxxx

# OU chave por condomínio (cada condo tem sua própria conta Asaas)
ASAAS_API_KEY_CONDO_1=$aact_xxxxxxxxxxxxxxxxxxxx
ASAAS_API_KEY_CONDO_2=$aact_yyyyyyyyyyyyyyyyyy
```

5. Ative o Asaas no ambiente:

```env
ASAAS_ENABLED=true
ASAAS_BASE_URL=https://api-sandbox.asaas.com
```

---

## 3. Configurar o Webhook

O webhook permite que o Asaas notifique o CondoHub quando um pagamento é confirmado,
vence ou é cancelado — sem que o sistema precise fazer polling.

### 3.1 Escolher um token de autenticação

Gere uma string aleatória segura (mínimo 32 caracteres) para proteger o endpoint.
Exemplo com `openssl`:

```bash
openssl rand -hex 32
# Exemplo de saída: a3f1b2c4d5e6...
```

### 3.2 Configurar no painel Asaas

1. No Sandbox, vá em **Configurações → Integrações → Webhooks**.
2. Clique em **Adicionar webhook**.
3. Preencha os campos:

| Campo | Valor |
|---|---|
| URL | `https://SEU_DOMINIO/api/financial/webhooks/asaas` |
| Token de autenticação | O token gerado no passo 3.1 |
| Eventos | Marque: `PAYMENT_RECEIVED`, `PAYMENT_OVERDUE`, `PAYMENT_DELETED`, `PAYMENT_UPDATED` |

> **Para testes locais:** use [ngrok](https://ngrok.com) ou [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/) para expor o localhost.
> Exemplo: `ngrok http 8080` → copie a URL HTTPS gerada (ex.: `https://abc123.ngrok.io`).

### 3.3 Configurar no CondoHub

1. Defina a variável de ambiente:

```env
ASAAS_WEBHOOK_TOKEN=o_mesmo_token_do_passo_3_1
```

2. **Ou** configure por condomínio via banco de dados / painel admin:
   - Acesse **Financeiro → Configurações** no CondoHub (perfil ADMIN/SINDICO).
   - Habilite "Habilitar Asaas neste condomínio".
   - Preencha o campo **Token do Webhook Asaas** com o mesmo valor.
   - Clique em **Salvar configuração**.

---

## 4. Testar a integração

### 4.1 Simular um pagamento no Sandbox

O Asaas Sandbox tem um **simulador de pagamentos**:

1. Crie uma cobrança pelo CondoHub (botão **Gerar cobrança externa** em uma invoice).
2. No painel Asaas Sandbox, acesse **Cobranças → Financeiro Recebido → Simular pagamento**.
3. Selecione a cobrança e clique em **Confirmar pagamento simulado**.
4. Dentro de alguns segundos o webhook é disparado para o CondoHub.
5. Verifique no CondoHub que o status da invoice mudou para `PAID`.

### 4.2 Verificar no CondoHub

- Abra a invoice no modal de detalhes — o campo **Último webhook** deve ser preenchido.
- A **Linha do tempo** da invoice mostrará o evento `WEBHOOK_RECEIVED` com o status recebido.
- O campo **Status externo** mostrará `RECEIVED`.

### 4.3 Verificar nos logs do backend

```bash
# Com Docker / Railway
railway logs --tail 100 | grep -i asaas

# Com Java local
grep "ASAAS\|webhook\|invoice" logs/spring.log | tail -50
```

---

## 5. Migrar para produção

Quando os testes estiverem concluídos:

1. Crie uma conta real em [https://www.asaas.com](https://www.asaas.com) e obtenha a API Key de produção.
2. Atualize as variáveis de ambiente:

```env
ASAAS_API_KEY=$aatp_xxxxxxxxxxxxxxxxxxxx   # começa com $aatp_ em produção
ASAAS_BASE_URL=https://api.asaas.com       # URL de produção
ASAAS_ENABLED=true
```

3. Reconfigure o webhook no painel **de produção** do Asaas apontando para o domínio real.
4. Reaplique o mesmo token de webhook (ou gere um novo — atualize nos dois lados).

---

## 6. Checklist de validação

- [ ] `ASAAS_ENABLED=true` nas variáveis de ambiente
- [ ] `ASAAS_BASE_URL` aponta para sandbox (homologação) ou produção
- [ ] API Key configurada (`ASAAS_API_KEY` ou `ASAAS_API_KEY_CONDO_{id}`)
- [ ] Token de webhook igual nos dois lados (Asaas e CondoHub)
- [ ] Endpoint público e acessível pela internet
- [ ] Eventos `PAYMENT_RECEIVED`, `PAYMENT_OVERDUE`, `PAYMENT_DELETED`, `PAYMENT_UPDATED` habilitados
- [ ] Pelo menos um pagamento simulado recebido com sucesso no ambiente de testes
