import { Router } from "express";
import {
  listCondos,
  createCondo,
  updateCondo,
  deleteCondo,
} from "../controllers/condo.controller.js";
import { authGuard } from "../middlewares/authGuard.js";

const router = Router();

router.get("/condos", authGuard(), listCondos);
router.post("/condos", authGuard(["ADMIN", "MANAGER"]), createCondo);
router.put("/condos/:id", authGuard(["ADMIN", "MANAGER"]), updateCondo);
router.delete("/condos/:id", authGuard(["ADMIN"]), deleteCondo);

export default router;