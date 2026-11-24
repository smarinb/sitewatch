/** "example.com" becomes "https://example.com"; anything that already has a scheme is left to the server to judge. */
export function normalizeUrl(input: string): string {
  const trimmed = input.trim()
  return trimmed === '' || /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`
}
