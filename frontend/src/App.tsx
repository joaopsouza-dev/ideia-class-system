import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "@/layouts/AppShell";
import { GuestOnly, RequireAuth, RequireRole } from "@/layouts/RouteGuards";
import { Spinner } from "@/components/ui/States";
import ForgotPasswordPage from "@/pages/auth/ForgotPasswordPage";
import LoginPage from "@/pages/auth/LoginPage";
import RegisterPage from "@/pages/auth/RegisterPage";
import HomePage from "@/pages/home/HomePage";
import { NotFoundPage } from "@/pages/NotFoundPage";

// Telas pesadas (pdf.js) e de gestão carregam sob demanda.
const MaterialViewerPage = lazy(() => import("@/pages/materials/MaterialViewerPage"));
const LibraryPage = lazy(() => import("@/pages/materials/LibraryPage"));
const ClassesPage = lazy(() => import("@/pages/classes/ClassesPage"));
const ClassDetailPage = lazy(() => import("@/pages/classes/ClassDetailPage"));
const UsersPage = lazy(() => import("@/pages/admin/UsersPage"));
const AuditPage = lazy(() => import("@/pages/admin/AuditPage"));

export default function App() {
  return (
    <Suspense fallback={<Spinner label="Carregando…" className="min-h-dvh" />}>
      <Routes>
        <Route element={<GuestOnly />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/cadastro" element={<RegisterPage />} />
          <Route path="/recuperar-senha" element={<ForgotPasswordPage />} />
        </Route>

        <Route element={<RequireAuth />}>
          <Route element={<AppShell />}>
            <Route index element={<HomePage />} />
            <Route path="turmas" element={<ClassesPage />} />
            <Route path="turmas/:turmaId" element={<ClassDetailPage />} />
            <Route path="biblioteca" element={<LibraryPage />} />
            <Route path="material/:id" element={<MaterialViewerPage />} />

            {/* Cadastro e edição só serão liberados na etapa de upload/CRUD; até lá, voltam à biblioteca. */}
            <Route path="materiais/novo" element={<Navigate to="/biblioteca" replace />} />
            <Route path="materiais/:id/editar" element={<Navigate to="/biblioteca" replace />} />

            <Route element={<RequireRole perfis={["PROFESSOR", "ADMIN"]} />}>
              <Route path="auditoria" element={<AuditPage />} />
            </Route>
            <Route element={<RequireRole perfis={["ADMIN"]} />}>
              <Route path="usuarios" element={<UsersPage />} />
            </Route>
          </Route>
        </Route>

        <Route path="/acesso-negado" element={<NotFoundPage forbidden />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}
