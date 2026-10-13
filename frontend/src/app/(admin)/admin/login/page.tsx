import type { Metadata } from "next";

import AdminLoginForm from "@/components/AdminLoginForm";

export const metadata: Metadata = {
  title: "Staff sign in | Iron & Oak",
};

export default function AdminLoginPage() {
  return <AdminLoginForm />;
}
