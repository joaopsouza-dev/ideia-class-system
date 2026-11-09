import {
  BookOpen,
  FileClock,
  GraduationCap,
  LayoutDashboard,
  Library,
  Plus,
  Users,
  type LucideIcon,
} from "lucide-react";
import type { Perfil } from "@/types";

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}

export const NAV: Record<Perfil, NavItem[]> = {
  PROFESSOR: [
    { to: "/", label: "Painel", icon: LayoutDashboard, end: true },
    { to: "/turmas", label: "Minhas turmas", icon: GraduationCap },
    { to: "/biblioteca", label: "Biblioteca", icon: Library },
    { to: "/materiais/novo", label: "Novo material", icon: Plus },
    { to: "/auditoria", label: "Acessos", icon: FileClock },
  ],
  ALUNO: [
    { to: "/", label: "Início", icon: LayoutDashboard, end: true },
    { to: "/turmas", label: "Minhas turmas", icon: GraduationCap },
    { to: "/biblioteca", label: "Biblioteca", icon: BookOpen },
  ],
  ADMIN: [
    { to: "/", label: "Painel", icon: LayoutDashboard, end: true },
    { to: "/usuarios", label: "Usuários", icon: Users },
    { to: "/turmas", label: "Turmas", icon: GraduationCap },
    { to: "/biblioteca", label: "Biblioteca", icon: Library },
    { to: "/auditoria", label: "Auditoria", icon: FileClock },
  ],
};

export const PERFIL_LABEL: Record<Perfil, string> = {
  ADMIN: "Administrador",
  PROFESSOR: "Professor",
  ALUNO: "Aluno",
};
