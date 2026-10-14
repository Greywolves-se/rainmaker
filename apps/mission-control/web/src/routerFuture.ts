/** Opt in to React Router v7 behaviour now, so the upgrade changes nothing (and v6 stops warning). */
export const ROUTER_FUTURE = {
  v7_startTransition: true,
  v7_relativeSplatPath: true,
} as const;
