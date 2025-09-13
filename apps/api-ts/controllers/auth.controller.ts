import { Request, Response } from 'express';
import bcrypt from 'bcryptjs';
import { prisma } from '../services/prisma.js';
import { signJwt } from '../utils/jwt.js';

export async function login(req: Request, res: Response){
  const { email, password } = req.body;
  const user = await prisma.user.findUnique({ where: { email } });
  if(!user) return res.status(401).json({ error: 'Invalid credentials' });
  const ok = await bcrypt.compare(password, user.passwordHash);
  if(!ok) return res.status(401).json({ error: 'Invalid credentials' });
  const token = signJwt({ id: user.id, role: user.role });
  res.json({ token, user: { id: user.id, name: user.name, role: user.role } });
}

export async function me(req: Request, res: Response) {
  const { id } = (req as any).user; // setado pelo authGuard
  const user = await prisma.user.findUnique({
    where: { id },
    select: { id: true, name: true, email: true, role: true },
  });
  res.json(user);
}