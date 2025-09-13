import express from "express";
import cors from "cors";
import helmet from "helmet";
import morgan from "morgan";

import authRouter from "./routes/auth.routes.js";
import condoRouter from "./routes/condo.routes.js";
import unitRouter from "./routes/unit.routes.js";
import residentRouter from "./routes/resident.routes.js";

export const app = express();

app.use(helmet());
app.use(cors());
app.use(express.json());
app.use(morgan("dev"));

app.get("/health", (_req, res) => res.json({ ok: true }));

// ✅ sem prefixo — o front usa /auth/*, /condos, /units, /residents
app.use(authRouter);
app.use(condoRouter);
app.use(unitRouter);
app.use(residentRouter);