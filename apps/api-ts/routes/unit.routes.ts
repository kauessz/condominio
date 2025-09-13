import { Router } from "express";
import {
  listUnits,
  createUnit,
  updateUnit,
  deleteUnit,
} from "../controllers/unit.controller.js";
import { authGuard } from "../middlewares/authGuard.js";

const router = Router();

router.get("/units", authGuard(), listUnits);
router.post("/units", authGuard(["ADMIN", "MANAGER"]), createUnit);
router.put("/units/:id", authGuard(["ADMIN", "MANAGER"]), updateUnit);
router.delete("/units/:id", authGuard(["ADMIN"]), deleteUnit);

export default router;