"use client";

import { useEffect, useSyncExternalStore } from "react";

import { getServerSnapshot, getSnapshot, initSession, subscribe, type AdminSession } from "./adminSession";

/** The current staff session; also restores it after a reload (once, shared by all callers). */
export function useAdminSession(): AdminSession {
  const session = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    void initSession();
  }, []);

  return session;
}
