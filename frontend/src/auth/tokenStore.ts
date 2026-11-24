/**
 * The bearer token the API client sends. Module-level on purpose: it is read by
 * request code outside React, and must change the instant the user signs in or
 * out, before any component effect runs (children's effects run before their
 * parent's, so a ref updated in the provider's effect would be one step late).
 */
let current: string | null = null

export const tokenStore = {
  get: (): string | null => current,
  set: (token: string | null): void => {
    current = token
  },
}
