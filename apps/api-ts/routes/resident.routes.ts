import { Router } from "express";
import { authGuard } from "../middlewares/authGuard.js";
import {
  listResidents,
  createResident,
  updateResident,
  deleteResident,
} from "../controllers/resident.controller.js";

const router = Router();

router.get("/residents", authGuard(), listResidents);
router.post("/residents", authGuard(["ADMIN", "MANAGER"]), createResident);
router.put("/residents/:id", authGuard(["ADMIN", "MANAGER"]), updateResident);
router.delete("/residents/:id", authGuard(["ADMIN"]), deleteResident);

export default router;