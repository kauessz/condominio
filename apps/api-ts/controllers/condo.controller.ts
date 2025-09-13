// apps/api/src/controllers/condo.controller.ts
import type { Request, Response } from "express";
import { z } from "zod";
import prisma from "../services/prisma.js";
import { Prisma } from "@prisma/client";

/* Schemas */
const querySchema = z.object({
  q: z.string().optional().default(""),
  page: z.coerce.number().min(1).optional().default(1),
  pageSize: z.coerce.number().min(1).max(100).optional().default(10),
});

const upsertSchema = z.object({
  name: z.string().min(2),
  cnpj: z.string().min(3),
});

/* GET /condos */
export async function listCondos(req: Request, res: Response) {
  const { q, page, pageSize } = querySchema.parse(req.query);

  // ✅ dê um tipo explícito ao where para satisfazer o Prisma
  const where: Prisma.CondoWhereInput = q
    ? {
        OR: [
          { name: { contains: q, mode: "insensitive" as const } },
          { cnpj: { contains: q, mode: "insensitive" as const } },
        ],
      }
    : {};

  const [items, total] = await Promise.all([
    prisma.condo.findMany({
      where,
      orderBy: { createdAt: "desc" },
      skip: (page - 1) * pageSize,
      take: pageSize,
      select: {
        id: true,
        name: true,
        cnpj: true,
        createdAt: true,
        _count: { select: { units: true, residents: true } },
      },
    }),
    prisma.condo.count({ where }),
  ]);

  return res.json({ items, total, page, pageSize });
}

/* POST /condos */
export async function createCondo(req: Request, res: Response) {
  const data = upsertSchema.parse(req.body);
  const created = await prisma.condo.create({ data });
  return res.status(201).json(created);
}

/* PUT /condos/:id */
export async function updateCondo(req: Request, res: Response) {
  const { id } = req.params;
  const data = upsertSchema.partial().parse(req.body);
  const updated = await prisma.condo.update({ where: { id }, data });
  return res.json(updated);
}

/* DELETE /condos/:id */
export async function deleteCondo(req: Request, res: Response) {
  const { id } = req.params;
  await prisma.condo.delete({ where: { id } });
  return res.status(204).send();
}