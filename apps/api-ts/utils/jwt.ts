import jwt from 'jsonwebtoken';
import { env } from '../env.js';
export function signJwt(payload: object){
  return jwt.sign(payload, env.JWT_SECRET, { expiresIn: '7d' });
}
