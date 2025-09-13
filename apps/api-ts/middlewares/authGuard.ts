// src/middlewares/authGuard.ts
import { NextFunction, Request, Response } from "express";
import jwt from "jsonwebtoken";
import { env } from "../env.js";

export type Role = "ADMIN" | "MANAGER" | "RESIDENT";
export interface AuthUser { id: string; role: Role }
interface JwtPayload extends jwt.JwtPayload, AuthUser {}

declare global {
  namespace Express {
    interface Request { user?: AuthUser }
  }
}

function extractToken(authorization?: string) {
  if (!authorization) return null;
  const val = authorization.trim();
  if (/^bearer\s+/i.test(val)) return val.replace(/^bearer\s+/i, "").trim();
  return val; // permite enviar só o token
}

export function authGuard(roles?: Role[]) {
  return (req: Request, res: Response, next: NextFunction) => {
    const token = extractToken(req.headers.authorization);
    if (!token) return res.status(401).json({ error: "No token" });

    try {
      const { id, role } = jwt.verify(token, env.JWT_SECRET) as JwtPayload;
      if (roles && !roles.includes(role)) {
        return res.status(403).json({ error: "Forbidden" });
      }
      req.user = { id, role };
      next();
    } catch {
      return res.status(401).json({ error: "Invalid token" });
    }
  };
}