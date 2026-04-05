--
-- PostgreSQL database dump
--

-- Dumped from database version 15.6
-- Dumped by pg_dump version 15.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: Condo; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public."Condo" (
    id text NOT NULL,
    name text NOT NULL,
    cnpj text NOT NULL,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


ALTER TABLE public."Condo" OWNER TO postgres;

--
-- Name: Resident; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public."Resident" (
    id text NOT NULL,
    name text NOT NULL,
    email text NOT NULL,
    phone text,
    "condoId" text NOT NULL,
    "unitId" text
);


ALTER TABLE public."Resident" OWNER TO postgres;

--
-- Name: Unit; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public."Unit" (
    id text NOT NULL,
    number text NOT NULL,
    block text,
    "condoId" text NOT NULL
);


ALTER TABLE public."Unit" OWNER TO postgres;

--
-- Name: User; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public."User" (
    id text NOT NULL,
    name text NOT NULL,
    email text NOT NULL,
    "passwordHash" text NOT NULL,
    role public."Role" DEFAULT 'RESIDENT'::public."Role" NOT NULL,
    "createdAt" timestamp(3) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" timestamp(3) without time zone NOT NULL
);


ALTER TABLE public."User" OWNER TO postgres;

--
-- Data for Name: Condo; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public."Condo" (id, name, cnpj, "createdAt") FROM stdin;
cmf7mjpsn0001syc0j9xt9rtk	Residencial Aurora	12.345.678/0001-90	2025-09-06 02:07:59.831
\.


--
-- Data for Name: Resident; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public."Resident" (id, name, email, phone, "condoId", "unitId") FROM stdin;
cmf8cv9620001sybcw1zc8epe	Maria Souza	maria@ex.com	11999999998	cmf7mjpsn0001syc0j9xt9rtk	cmf7s2uph0001sy3kzdbs1amp
cmf7mjpu90005syc0tqke5xnt	João Silva	joao@ex.com	11999999999	cmf7mjpsn0001syc0j9xt9rtk	cmf8o3lbv0007syeohzh7tia8
cmf8o44kp0009syeofbf8dz4t	KAUE CARNEIRO LIMA SANTOS	ssz.kaue@gmail.com	(13) 98802-6188	cmf7mjpsn0001syc0j9xt9rtk	cmf8o37mc0005syeoknz9qcb9
\.


--
-- Data for Name: Unit; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public."Unit" (id, number, block, "condoId") FROM stdin;
cmf7s2uph0001sy3kzdbs1amp	201	B	cmf7mjpsn0001syc0j9xt9rtk
cmf8o37mc0005syeoknz9qcb9	110	A	cmf7mjpsn0001syc0j9xt9rtk
cmf8o3lbv0007syeohzh7tia8	301	B	cmf7mjpsn0001syc0j9xt9rtk
\.


--
-- Data for Name: User; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public."User" (id, name, email, "passwordHash", role, "createdAt", "updatedAt") FROM stdin;
cmf7mjpq20000syc028m1e6r6	Admin	admin@condo.local	$2b$10$rdipML82w1vTbaxQ8X8N5OihB628yoqrK2BNDlVJiYlmAbMHOHiaG	ADMIN	2025-09-06 02:07:59.738	2025-09-06 02:07:59.738
\.


--
-- Name: Condo Condo_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public."Condo"
    ADD CONSTRAINT "Condo_pkey" PRIMARY KEY (id);


--
-- Name: Resident Resident_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public."Resident"
    ADD CONSTRAINT "Resident_pkey" PRIMARY KEY (id);


--
-- Name: Unit Unit_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public."Unit"
    ADD CONSTRAINT "Unit_pkey" PRIMARY KEY (id);


--
-- Name: User User_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public."User"
    ADD CONSTRAINT "User_pkey" PRIMARY KEY (id);


--
-- Name: Condo_cnpj_key; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX "Condo_cnpj_key" ON public."Condo" USING btree (cnpj);


--
-- Name: Resident_email_key; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX "Resident_email_key" ON public."Resident" USING btree (email);


--
-- Name: Resident_unitId_key; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX "Resident_unitId_key" ON public."Resident" USING btree ("unitId");


--
-- Name: User_email_key; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX "User_email_key" ON public."User" USING btree (email);


--
-- Name: Resident Resident_condoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public."Resident"
    ADD CONSTRAINT "Resident_condoId_fkey" FOREIGN KEY ("condoId") REFERENCES public."Condo"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: Resident Resident_unitId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public."Resident"
    ADD CONSTRAINT "Resident_unitId_fkey" FOREIGN KEY ("unitId") REFERENCES public."Unit"(id) ON UPDATE CASCADE ON DELETE SET NULL;


--
-- Name: Unit Unit_condoId_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public."Unit"
    ADD CONSTRAINT "Unit_condoId_fkey" FOREIGN KEY ("condoId") REFERENCES public."Condo"(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- Name: TABLE "Condo"; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public."Condo" TO anon;
GRANT ALL ON TABLE public."Condo" TO authenticated;


--
-- Name: TABLE "Resident"; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public."Resident" TO anon;
GRANT ALL ON TABLE public."Resident" TO authenticated;


--
-- Name: TABLE "Unit"; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public."Unit" TO anon;
GRANT ALL ON TABLE public."Unit" TO authenticated;


--
-- Name: TABLE "User"; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public."User" TO anon;
GRANT ALL ON TABLE public."User" TO authenticated;


--
-- PostgreSQL database dump complete
--

