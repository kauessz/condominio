import { Request, Response } from "express";
import { prisma } from "../services/prisma.js";
import { z } from "zod";
import { Prisma } from "@prisma/client";

/** Schema de criação/edição */
const baseSchema = z.object({
  name: z.string().min(2, "Nome muito curto"),
  email: z.string().email("Email inválido"),
  phone: z.string().min(8, "Telefone inválido"),
  condoId: z.string().min(1, "Condomínio obrigatório"),
  unitId: z.string().optional().nullable(), // 1 morador por unidade (schema tem unique do lado do Resident.unitId)
});

/** GET /residents?condoId=...&q=...&page=1&pageSize=8 */
export async function listResidents(req: Request, res: Response) {
  try {
    const condoId = String(req.query.condoId ?? "");
    if (!condoId) return res.status(400).json({ error: "condoId é obrigatório" });

    const page = Math.max(1, Number(req.query.page ?? 1));
    const pageSize = Math.max(1, Math.min(50, Number(req.query.pageSize ?? 10)));
    const q = String(req.query.q ?? "").trim();

    const where: Prisma.ResidentWhereInput = {
      condoId,
      ...(q && {
        OR: [
          { name: { contains: q, mode: "insensitive" } },
          { email: { contains: q, mode: "insensitive" } },
          { phone: { contains: q, mode: "insensitive" } },
        ],
      }),
    };

    const [items, total] = await Promise.all([
      prisma.resident.findMany({
        where,
        include: { unit: true },
        skip: (page - 1) * pageSize,
        take: pageSize,
        orderBy: { name: "asc" },
      }),
      prisma.resident.count({ where }),
    ]);

    return res.json({ items, total, page, pageSize });
  } catch (e) {
    console.error(e);
    return res.status(500).json({ error: "Erro ao listar moradores" });
  }
}

/** POST /residents */
export async function createResident(req: Request, res: Response) {
  try {
    const { name, email, phone, condoId, unitId } = baseSchema.parse(req.body);

    if (unitId) {
      // unidade precisa existir e pertencer ao mesmo condomínio
      const unit = await prisma.unit.findUnique({ where: { id: unitId } });
      if (!unit || unit.condoId !== condoId) {
        return res.status(400).json({ error: "Unidade inválida para este condomínio" });
      }
      // 1 morador por unidade -> checa ocupação
      const already = await prisma.resident.findFirst({ where: { unitId } });
      if (already) return res.status(409).json({ error: "A unidade já possui um morador" });
    }

    const resident = await prisma.resident.create({
      data: { name, email, phone, condoId, unitId: unitId ?? null },
      include: { unit: true },
    });

    return res.status(201).json(resident);
  } catch (e: any) {
    if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === "P2002") {
      const target = Array.isArray((e.meta as any)?.target) ? (e.meta as any).target : [];
      if (target.includes("email")) return res.status(409).json({ error: "Este e-mail já está em uso" });
      if (target.includes("unitId")) return res.status(409).json({ error: "A unidade já possui um morador" });
      return res.status(409).json({ error: "Violação de unicidade" });
    }
    if (e?.issues?.[0]?.message) return res.status(400).json({ error: e.issues[0].message });
    console.error(e);
    return res.status(500).json({ error: "Erro interno ao criar morador" });
  }
}

/** PUT /residents/:id */
export async function updateResident(req: Request, res: Response) {
  try {
    const id = String(req.params.id);
    const data = baseSchema.partial().parse({ ...req.body }); // parcial para edição

    // Se for trocar de unidade, manter regra 1:1
    if (typeof data.unitId !== "undefined" && data.unitId) {
      const unit = await prisma.unit.findUnique({ where: { id: data.unitId } });
      if (!unit || (data.condoId && unit.condoId !== data.condoId)) {
        return res.status(400).json({ error: "Unidade inválida para este condomínio" });
      }
      const already = await prisma.resident.findFirst({ where: { unitId: data.unitId, NOT: { id } } });
      if (already) return res.status(409).json({ error: "A unidade já possui um morador" });
    }

    const updated = await prisma.resident.update({
      where: { id },
      data: {
        ...(data.name && { name: data.name }),
        ...(data.email && { email: data.email }),
        ...(data.phone && { phone: data.phone }),
        ...(typeof data.unitId !== "undefined" ? { unitId: data.unitId ?? null } : {}),
      },
      include: { unit: true },
    });

    return res.json(updated);
  } catch (e: any) {
    if (e instanceof Prisma.PrismaClientKnownRequestError && e.code === "P2002") {
      const target = Array.isArray((e.meta as any)?.target) ? (e.meta as any).target : [];
      if (target.includes("email")) return res.status(409).json({ error: "Este e-mail já está em uso" });
      if (target.includes("unitId")) return res.status(409).json({ error: "A unidade já possui um morador" });
      return res.status(409).json({ error: "Violação de unicidade" });
    }
    if (e?.code === "P2025") return res.status(404).json({ error: "Morador não encontrado" });
    console.error(e);
    return res.status(500).json({ error: "Erro ao atualizar morador" });
  }
}

/** DELETE /residents/:id */
export async function deleteResident(req: Request, res: Response) {
  try {
    const id = String(req.params.id);
    await prisma.resident.delete({ where: { id } });
    return res.status(204).send();
  } catch (e: any) {
    if (e?.code === "P2025") return res.status(404).json({ error: "Morador não encontrado" });
    console.error(e);
    return res.status(500).json({ error: "Erro ao excluir morador" });
  }
}