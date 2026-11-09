import { useSessao } from "@/context/AuthContext";
import AdminDashboard from "@/pages/admin/AdminDashboard";
import StudentDashboard from "@/pages/student/StudentDashboard";
import TeacherDashboard from "@/pages/teacher/TeacherDashboard";

/** Direciona para o painel do perfil autenticado. */
export default function HomePage() {
  const { usuario } = useSessao();
  if (usuario.perfil === "ADMIN") return <AdminDashboard />;
  if (usuario.perfil === "PROFESSOR") return <TeacherDashboard />;
  return <StudentDashboard />;
}
