import { Request, Response } from "express";
import { prisma } from "../services/prisma.js";
import { z } from "zod";

const createSchema = z.object({
  number: z.string().min(1),
  block: z.string().optional(),
  condoId: z.string().cuid(),
});

export async function listUnits(req: Request, res: Response) {
  const { condoId, q = "", page = "1", pageSize = "10" } = req.query as any;
  const where: any = {};
  if (condoId) where.condoId = condoId;
  if (q) where.OR = [
    { number: { contains: String(q), mode: "insensitive" } },
    { block:  { contains: String(q), mode: "insensitive" } },
  ];

  const take = Math.max(1, +pageSize);
  const skip = (Math.max(1, +page) - 1) * take;

  const [items, total] = await Promise.all([
    prisma.unit.findMany({
      where,
      orderBy: [{ block: "asc" }, { number: "asc" }],
      include: { resident: true },
      skip, take,
    }),
    prisma.unit.count({ where }),
  ]);

  res.json({ items, total, page: +page, pageSize: take });
}

export async function createUnit(req: Request, res: Response) {
  const body = createSchema.parse(req.body);
  const unit = await prisma.unit.create({ data: body });
  res.status(201).json(unit);
}

export async function updateUnit(req: Request, res: Response) {
  const { id } = req.params;
  const body = createSchema.partial().parse(req.body);
  const unit = await prisma.unit.update({ where: { id }, data: body });
  res.json(unit);
}

export async function deleteUnit(req: Request, res: Response) {
  const { id } = req.params;
  await prisma.unit.delete({ where: { id } });
  res.status(204).end();
}