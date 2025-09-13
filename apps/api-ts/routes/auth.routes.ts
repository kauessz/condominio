import { Router } from "express";
import { login, me } from "../controllers/auth.controller.js";
import { authGuard } from "../middlewares/authGuard.js";

const router = Router();

router.post("/auth/login", login);
router.get("/auth/me", authGuard(), me);

export default router;