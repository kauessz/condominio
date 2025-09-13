import { PrismaClient } from "@prisma/client";
import bcrypt from "bcryptjs";
const prisma = new PrismaClient();

async function main() {
  // 1) Admin
  const adminPass = await bcrypt.hash("admin123", 10);
  await prisma.user.upsert({
    where: { email: "admin@condo.local" },
    update: {},
    create: {
      name: "Admin",
      email: "admin@condo.local",
      passwordHash: adminPass,
      role: "ADMIN",
    },
  });

  // 2) Condomínio (upsert por CNPJ)
  const cnpj = "12.345.678/0001-90";
  const condo = await prisma.condo.upsert({
    where: { cnpj },
    update: {},
    create: { name: "Residencial Aurora", cnpj },
  });

  // 3) Unidade (como não há unique composto, checa e cria se faltar)
  let unit = await prisma.unit.findFirst({
    where: { number: "101", block: "A", condoId: condo.id },
  });
  if (!unit) {
    unit = await prisma.unit.create({
      data: { number: "101", block: "A", condoId: condo.id },
    });
  }

  // 4) Morador (upsert por email) e vínculo à unidade
  const residentEmail = "joao@ex.com";
  await prisma.resident.upsert({
    where: { email: residentEmail },
    update: { condoId: condo.id, unitId: unit.id },
    create: {
      name: "João Silva",
      email: residentEmail,
      phone: "11999999999",
      condoId: condo.id,
      unitId: unit.id,
    },
  });
}

main()
  .then(() => console.log("Seed ok (idempotente)"))
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());