import {
  BookOpen,
  FileClock,
  FolderCog,
  GraduationCap,
  KeyRound,
  LayoutDashboard,
  Library,
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
    { to: "/materiais", label: "Meus materiais", icon: FolderCog },
    { to: "/auditoria", label: "Acessos", icon: FileClock },
    { to: "/conta/senha", label: "Alterar senha", icon: KeyRound },
  ],
  ALUNO: [
    { to: "/", label: "Início", icon: LayoutDashboard, end: true },
    { to: "/turmas", label: "Minhas turmas", icon: GraduationCap },
    { to: "/biblioteca", label: "Biblioteca", icon: BookOpen },
    { to: "/conta/senha", label: "Alterar senha", icon: KeyRound },
  ],
  ADMIN: [
    { to: "/", label: "Painel", icon: LayoutDashboard, end: true },
    { to: "/usuarios", label: "Usuários", icon: Users },
    { to: "/turmas", label: "Turmas", icon: GraduationCap },
    { to: "/biblioteca", label: "Biblioteca", icon: Library },
    { to: "/materiais", label: "Materiais", icon: FolderCog },
    { to: "/auditoria", label: "Auditoria", icon: FileClock },
    { to: "/conta/senha", label: "Alterar senha", icon: KeyRound },
  ],
};

export const PERFIL_LABEL: Record<Perfil, string> = {
  ADMIN: "Administrador",
  PROFESSOR: "Professor",
  ALUNO: "Aluno",
};
