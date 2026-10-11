import { request } from "./http";
import type { AdminLoginResponse, AdminProfileResponse } from "./types";

/**
 * Raw admin-auth endpoints. Components should go through lib/auth/adminSession.ts, which keeps
 * the tokens, refreshes them and retries; calling these directly would bypass that.
 */

const BASE = "/api/admin/auth";

export function adminLogin(username: string,
  password: string): Promise<AdminLoginResponse> {
  return request(`${BASE}/login`,
    {
      method: "POST",
      body: { username, password }
    });
}

/** Single-use: the response carries the next refresh token and the one sent here stops working. */
export function adminRefresh(refreshToken: string): Promise<AdminLoginResponse> {
  return request(`${BASE}/refresh`,
    {
      method: "POST",
      body: { refreshToken }
    });
}

/** Always 204, so it reveals nothing about the token. */
export function adminLogout(refreshToken: string): Promise<void> {
  return request(`${BASE}/logout`,
    {
      method: "POST",
      body: { refreshToken }
    });
}

export function adminLogoutAll(accessToken: string): Promise<void> {
  return request(`${BASE}/logout-all`,
    {
      method: "POST",
      token: accessToken
    });
}

export function adminMe(accessToken: string): Promise<AdminProfileResponse> {
  return request(`${BASE}/me`,
    { token: accessToken });
}

export function adminChangePassword(
  accessToken: string,
  currentPassword: string,
  newPassword: string,
): Promise<AdminLoginResponse> {

  return request(`${BASE}/change-password`,
    {
      method: "POST",
      token: accessToken,
      body: { currentPassword, newPassword }
    }
  );
}
