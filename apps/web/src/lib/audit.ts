type AuditDetails = Record<string, unknown> | null | undefined;

export type AuditItem = {
  id: number;
  createdAt: string;
  module: string;
  action: string;
  entityType: string;
  entityId: string;
  description: string;
  actorUserId?: number | null;
  actorName?: string | null;
  actorEmail?: string | null;
  actorRole?: string | null;
  condominiumId?: number | null;
  condominiumName?: string | null;
  details?: AuditDetails;
};

type AuditPresentation = {
  moduleLabel: string;
  actionLabel: string;
  title: string;
  context: string;
};

const MODULE_LABELS: Record<string, string> = {
  VISITORS: "Visitantes",
  RESERVATIONS: "Reservas",
  ASSEMBLIES: "Assembleias",
  PARKING: "Vagas",
  FINANCIAL: "Financeiro",
  CONDOMINIUMS: "Condomínios",
  USERS: "Usuários",
  RESIDENTS: "Moradores",
  UNITS: "Unidades",
  SYSTEM: "Sistema",
};

const ACTION_LABELS: Record<string, string> = {
  CREATE: "Cadastro",
  UPDATE: "Atualização",
  DELETE: "Exclusão",
  APPROVE: "Aprovação",
  REJECT: "Rejeição",
  CHECK_IN: "Check-in",
  CHECK_OUT: "Check-out",
  STATUS_CHANGE: "Mudança de status",
  VOTE_CAST: "Voto registrado",
  CONFIG_CHANGED: "Configuração alterada",
  ASSIGN_PARKING_SPOT: "Atribuição de vaga",
  REMOVE_PARKING_ASSIGNMENT: "Remoção de atribuição",
  REGISTER_PAYMENT: "Pagamento registrado",
  PAYMENT_REGISTERED: "Pagamento registrado",
  CREATE_VISITOR: "Cadastro de visitante",
  UPDATE_VISITOR: "Atualização de visitante",
  APPROVE_VISITOR: "Aprovação de visitante",
  REJECT_VISITOR: "Rejeição de visitante",
  CHECK_IN_VISITOR: "Check-in",
  CHECK_OUT_VISITOR: "Check-out",
  REGISTER_DELIVERY: "Cadastro de entrega",
  WITHDRAW_DELIVERY: "Retirada de entrega",
  CREATE_RESERVATION: "Cadastro de reserva",
  APPROVE_RESERVATION: "Aprovação de reserva",
  REJECT_RESERVATION: "Rejeição de reserva",
  CANCEL_RESERVATION: "Cancelamento de reserva",
  CREATE_ASSEMBLY: "Cadastro de assembleia",
  OPEN_ASSEMBLY: "Abertura de assembleia",
  CLOSE_ASSEMBLY: "Encerramento de assembleia",
  ADD_AGENDA_ITEM: "Inclusão de pauta",
  ADD_ELECTION_CANDIDATES: "Inclusão de candidatos",
  CAST_VOTE: "Voto registrado",
  VALIDATE_RESULT: "Validação de resultado",
  APPLY_ROLE_EFFECT: "Efetivação de resultado",
  CREATE_PARKING_SPOT: "Cadastro de vaga",
  UPDATE_PARKING_POLICY: "Política de vagas alterada",
  CREATE_DRAW: "Cadastro de sorteio",
  EXECUTE_DRAW: "Execução de sorteio",
  CREATE_INVOICE: "Lançamento de cobrança",
  CREATE_EXTERNAL_CHARGE: "Cobrança externa criada",
  PAYMENT_CONFIRMED: "Pagamento confirmado",
  PAYMENT_FAILED: "Falha de pagamento",
  WEBHOOK_RECEIVED: "Webhook recebido",
  GENERATE_CHARGE_BATCH: "Geração de cobranças",
  UPDATE_FINANCIAL_CONFIG: "Configuração alterada",
  EXTRA_FEE_APPORTIONMENT: "Rateio extraordinário",
};

export function getAuditModuleLabel(module?: string | null) {
  if (!module) return "Sistema";
  return MODULE_LABELS[module] ?? module;
}

export function getAuditActionLabel(action?: string | null) {
  if (!action) return "Ação";
  return ACTION_LABELS[action] ?? humanizeCode(action);
}

export function getAuditPresentation(item: AuditItem): AuditPresentation {
  const details = item.details ?? {};
  const presentation = resolvePresentation(item, details);
  return {
    moduleLabel: getAuditModuleLabel(item.module),
    actionLabel: getAuditActionLabel(item.action),
    title: presentation.title || item.description || fallbackEntityLabel(item),
    context: presentation.context,
  };
}

function resolvePresentation(item: AuditItem, details: AuditDetails): { title: string; context: string } {
  switch (item.module) {
    case "VISITORS":
      return presentVisitor(item, details);
    case "USERS":
      return presentUser(item, details);
    case "RESIDENTS":
      return presentResident(item, details);
    case "ASSEMBLIES":
      return presentAssembly(item, details);
    case "PARKING":
      return presentParking(item, details);
    case "FINANCIAL":
      return presentFinancial(item, details);
    case "RESERVATIONS":
      return presentReservation(item, details);
    default:
      return {
        title: item.description,
        context: buildCommonContext(item),
      };
  }
}

function presentVisitor(item: AuditItem, details: AuditDetails) {
  const identity = getVisitorLabel(item, details);
  const unit = firstText(details, ["unitLabel"]) || unitLabelFromDetails(details);
  const titleByAction: Record<string, string> = {
    CREATE_VISITOR: `Visitante ${identity} cadastrado`,
    UPDATE_VISITOR: `Visitante ${identity} atualizado`,
    APPROVE_VISITOR: `Visitante ${identity} aprovado`,
    REJECT_VISITOR: `Visitante ${identity} rejeitado`,
    CHECK_IN_VISITOR: `Visitante ${identity} realizou check-in`,
    CHECK_OUT_VISITOR: `Visitante ${identity} realizou check-out`,
    REGISTER_DELIVERY: `Entrega ${identity} registrada`,
    WITHDRAW_DELIVERY: `Entrega ${identity} retirada`,
    STATUS_CHANGE: `Visitante ${identity} teve o status alterado`,
  };
  return {
    title: titleByAction[item.action] || item.description,
    context: joinParts([unit, item.condominiumName]),
  };
}

function presentUser(item: AuditItem, details: AuditDetails) {
  const userName = firstText(details, ["userName"]);
  const userEmail = firstText(details, ["userEmail", "email"]);
  const identity = getUserLabel(item, details);
  const unit = firstText(details, ["unitLabel"]) || unitLabelFromDetails(details);
  const titleByAction: Record<string, string> = {
    CREATE: `Usuário ${identity} cadastrado`,
    UPDATE: `Usuário ${identity} atualizado`,
    DELETE: `Usuário ${identity} removido`,
  };
  return {
    title: titleByAction[item.action] || `Usuário ${identity} atualizado`,
    context: joinParts([userEmail && userEmail !== identity ? userEmail : "", unit, item.condominiumName]),
  };
}

function presentResident(item: AuditItem, details: AuditDetails) {
  const residentName = getResidentLabel(item, details);
  const unit = firstText(details, ["unitLabel"]) || unitLabelFromDetails(details);
  const titleByAction: Record<string, string> = {
    CREATE: `Morador ${residentName} cadastrado`,
    UPDATE: `Morador ${residentName} atualizado`,
    DELETE: `Morador ${residentName} removido`,
  };
  return {
    title: titleByAction[item.action] || `Morador ${residentName} atualizado`,
    context: joinParts([unit, item.condominiumName]),
  };
}

function presentAssembly(item: AuditItem, details: AuditDetails) {
  const assemblyTitle = getAssemblyLabel(item, details);
  const officeName = firstText(details, ["officeName"]);
  const agendaTitle = getAgendaLabel(item, details);
  if (item.entityType === "AssemblyAgendaItem") {
    const agendaIdentity = agendaTitle;
    const titleByAction: Record<string, string> = {
      ADD_AGENDA_ITEM: `Pauta ${agendaIdentity} cadastrada`,
      ADD_ELECTION_CANDIDATES: `Candidatos adicionados à pauta ${agendaIdentity}`,
      CREATE: `Pauta ${agendaIdentity} cadastrada`,
      UPDATE: `Pauta ${agendaIdentity} atualizada`,
    };
    return {
      title: titleByAction[item.action] || `Pauta ${agendaIdentity} cadastrada`,
      context: joinParts([assemblyTitle ? `Assembleia: ${assemblyTitle}` : "", officeName ? `Cargo: ${officeName}` : "", item.condominiumName]),
    };
  }
  if (item.entityType === "AssemblyVote") {
    const voteContext = agendaTitle || assemblyTitle || getVoteLabel(item);
    return {
      title: agendaTitle
        ? `Voto registrado na pauta ${voteContext}`
        : assemblyTitle
          ? `Voto registrado na assembleia ${voteContext}`
          : `Voto #${item.entityId} registrado`,
      context: joinParts([assemblyTitle && agendaTitle ? `Assembleia: ${assemblyTitle}` : "", officeName ? `Cargo: ${officeName}` : "", firstText(details, ["candidateName"]), item.condominiumName]),
    };
  }
  const assemblyIdentity = assemblyTitle;
  const titleByAction: Record<string, string> = {
    CREATE_ASSEMBLY: `Assembleia ${assemblyIdentity} criada`,
    OPEN_ASSEMBLY: `Assembleia ${assemblyIdentity} aberta`,
    CLOSE_ASSEMBLY: `Assembleia ${assemblyIdentity} encerrada`,
    VALIDATE_RESULT: `Resultado da assembleia ${assemblyIdentity} validado`,
    CAST_VOTE: `Voto registrado na assembleia ${assemblyIdentity}`,
    APPLY_ROLE_EFFECT: `Resultado da eleição ${officeName ?? assemblyIdentity} efetivado`,
    CREATE: `Assembleia ${assemblyIdentity} criada`,
  };
  return {
    title: titleByAction[item.action] || item.description,
    context: joinParts([agendaTitle ? `Pauta: ${agendaTitle}` : "", officeName ? `Cargo: ${officeName}` : "", item.condominiumName]),
  };
}

function presentParking(item: AuditItem, details: AuditDetails) {
  const spotCode = getParkingLabel(item, details);
  const unit = firstText(details, ["unitLabel"]) || unitLabelFromDetails(details);
  const drawName = firstText(details, ["drawName"]);
  const titleByAction: Record<string, string> = {
    CREATE_PARKING_SPOT: `Vaga ${spotCode} cadastrada`,
    ASSIGN_PARKING_SPOT: `Vaga ${spotCode} atribuída`,
    REMOVE_PARKING_ASSIGNMENT: `Atribuição da vaga ${spotCode} removida`,
    CREATE_DRAW: `Sorteio ${drawName ?? item.description} criado`,
    EXECUTE_DRAW: `Sorteio ${drawName ?? item.entityId} executado`,
  };
  return {
    title: titleByAction[item.action] || item.description,
    context: joinParts([unit, drawName, item.condominiumName]),
  };
}

function presentFinancial(item: AuditItem, details: AuditDetails) {
  const invoiceTitle = getInvoiceLabel(item, details);
  const unit = firstText(details, ["unitLabel"]) || unitLabelFromDetails(details);
  const referenceMonth = firstText(details, ["referenceMonth"]);
  const titleByAction: Record<string, string> = {
    CREATE_INVOICE: `Cobrança ${invoiceTitle} lançada`,
    CREATE_EXTERNAL_CHARGE: `Cobrança externa criada para ${invoiceTitle}`,
    REGISTER_PAYMENT: `Pagamento registrado para ${invoiceTitle}`,
    PAYMENT_REGISTERED: `Pagamento registrado para ${invoiceTitle}`,
    PAYMENT_CONFIRMED: `Pagamento confirmado para ${invoiceTitle}`,
    PAYMENT_FAILED: `Falha operacional em ${invoiceTitle}`,
    WEBHOOK_RECEIVED: `Webhook recebido para ${invoiceTitle}`,
    GENERATE_CHARGE_BATCH: `Cobranças geradas em lote`,
    EXTRA_FEE_APPORTIONMENT: `Rateio extraordinário gerado`,
    UPDATE_FINANCIAL_CONFIG: `Configuração financeira alterada`,
  };
  return {
    title: titleByAction[item.action] || item.description,
    context: joinParts([unit, referenceMonth, item.condominiumName]),
  };
}

function presentReservation(item: AuditItem, details: AuditDetails) {
  const areaName = getReservationLabel(item, details);
  const unit = firstText(details, ["unitLabel"]) || unitLabelFromDetails(details);
  const titleByAction: Record<string, string> = {
    CREATE_RESERVATION: `Reserva de ${areaName} criada`,
    APPROVE_RESERVATION: `Reserva de ${areaName} aprovada`,
    REJECT_RESERVATION: `Reserva de ${areaName} rejeitada`,
    CANCEL_RESERVATION: `Reserva de ${areaName} cancelada`,
  };
  return {
    title: titleByAction[item.action] || item.description,
    context: joinParts([unit, item.condominiumName]),
  };
}

function buildCommonContext(item: AuditItem) {
  return joinParts([fallbackEntityLabel(item), item.condominiumName]);
}

function getVisitorLabel(item: AuditItem, details: AuditDetails) {
  return firstText(details, ["visitorName", "name"]) || `#${item.entityId}`;
}

function getUserLabel(item: AuditItem, details: AuditDetails) {
  return firstText(details, ["userName"]) || firstText(details, ["userEmail", "email"]) || `#${item.entityId}`;
}

function getResidentLabel(item: AuditItem, details: AuditDetails) {
  return firstText(details, ["residentName", "name"]) || `#${item.entityId}`;
}

function getAssemblyLabel(item: AuditItem, details: AuditDetails) {
  return firstText(details, ["assemblyTitle", "title"]) || `#${item.entityId}`;
}

function getAgendaLabel(item: AuditItem, details: AuditDetails) {
  return firstText(details, ["agendaTitle", "agendaItemTitle", "itemTitle"]) || `#${item.entityId}`;
}

function getVoteLabel(item: AuditItem) {
  return `#${item.entityId}`;
}

function getParkingLabel(item: AuditItem, details: AuditDetails) {
  return firstText(details, ["parkingSpotCode", "spotCode", "code"]) || `#${item.entityId}`;
}

function getInvoiceLabel(item: AuditItem, details: AuditDetails) {
  return firstText(details, ["invoiceTitle", "title"]) || `#${item.entityId}`;
}

function getReservationLabel(item: AuditItem, details: AuditDetails) {
  return firstText(details, ["commonAreaName", "areaName"]) || `#${item.entityId}`;
}

function fallbackEntityLabel(item: Pick<AuditItem, "entityType" | "entityId">) {
  const entityLabels: Record<string, string> = {
    Visitor: "Visitante",
    User: "Usuário",
    Resident: "Morador",
    Reservation: "Reserva",
    Assembly: "Assembleia",
    AssemblyAgendaItem: "Pauta",
    AssemblyVote: "Voto",
    ParkingSpot: "Vaga",
    ParkingDraw: "Sorteio",
    ParkingSpotAssignment: "Atribuição de vaga",
    Invoice: "Cobrança",
    FinancialConfig: "Configuração financeira",
    Condominium: "Condomínio",
  };
  return `${entityLabels[item.entityType] ?? item.entityType} #${item.entityId}`;
}

function unitLabelFromDetails(details: AuditDetails) {
  const unitId = firstValue(details, ["unitId"]);
  const block = firstText(details, ["unitBlock", "block"]);
  const number = firstText(details, ["unitNumber"]);
  const code = firstText(details, ["unitCode"]);
  const base = number || code || (unitId != null ? `#${String(unitId)}` : "");
  if (!base) return "";
  return block ? `Unidade ${base} - Bloco ${block}` : `Unidade ${base}`;
}

function firstText(details: AuditDetails, keys: string[]) {
  const value = firstValue(details, keys);
  if (value == null) return "";
  const text = String(value).trim();
  return text.length > 0 ? text : "";
}

function firstValue(details: AuditDetails, keys: string[]) {
  if (!details || typeof details !== "object") return null;
  for (const key of keys) {
    const value = details[key];
    if (value != null && value !== "") {
      return value;
    }
  }
  return null;
}

function joinParts(parts: Array<string | null | undefined>) {
  return parts.map((part) => (part ?? "").trim()).filter(Boolean).join(" • ");
}

function humanizeCode(code: string) {
  return code
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

export const auditModuleOptions = [
  { value: "", label: "Todos os módulos" },
  { value: "VISITORS", label: getAuditModuleLabel("VISITORS") },
  { value: "RESERVATIONS", label: getAuditModuleLabel("RESERVATIONS") },
  { value: "ASSEMBLIES", label: getAuditModuleLabel("ASSEMBLIES") },
  { value: "PARKING", label: getAuditModuleLabel("PARKING") },
  { value: "FINANCIAL", label: getAuditModuleLabel("FINANCIAL") },
  { value: "CONDOMINIUMS", label: getAuditModuleLabel("CONDOMINIUMS") },
  { value: "SYSTEM", label: getAuditModuleLabel("SYSTEM") },
];
