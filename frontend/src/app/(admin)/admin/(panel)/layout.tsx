import AdminShell from "@/components/admin/AdminShell";

/** Every signed-in staff screen (not the login or change-password pages) sits inside the panel. */
export default function PanelLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <AdminShell>{children}</AdminShell>;
}
