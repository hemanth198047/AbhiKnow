export type AccessType =
  | 'PUBLIC_DOMAIN' | 'OPEN_ACCESS' | 'FREE_VERIFIED_PERMISSION' | 'FREE_TO_READ_ONLINE' | 'FREE_REGISTRATION'
  | 'LIBRARY_BORROWING' | 'USER_OWNED' | 'CAPTCHA_REQUIRED' | 'WAITING_PERIOD' | 'AUTHENTICATION_REQUIRED'
  | 'INSTITUTIONAL' | 'SUBSCRIPTION' | 'PURCHASE' | 'RENTAL' | 'METADATA_ONLY' | 'RESTRICTED' | 'UNKNOWN'

export interface Location {
  provider: string; url: string; kind?: string; format?: string; accessType: AccessType; accessLabel: string
  license?: string; canRead: string; canDownload: string; canRedistribute: string; note?: string
}
export interface Relation { kind: string; id: string; title: string }
export interface Resource {
  id?: string; title: string; subtitle?: string; description?: string; type: string; language?: string
  publisher?: string; year?: number; authors: string[]; identifiers: { scheme: string; value: string }[]
  locations: Location[]; sources: string[]; subjects: string[]; citedBy?: number
  accessType: AccessType; accessLabel: string; recommendedAction: string; score: number; reasons: string[]
  relations: Relation[]
}
export interface ProviderOutcome {
  providerId: string; displayName: string
  status: 'OK' | 'TIMEOUT' | 'ERROR' | 'RATE_LIMITED' | 'SKIPPED' | 'CIRCUIT_OPEN' | 'DISABLED'
  recordCount: number; latencyMs: number; message?: string; cached: boolean
}
export interface SearchResponse {
  query: { raw: string; searchedAs: string; mode: string }
  page: number; results: Resource[]; recommendedIds: string[]; countsByType: Record<string, number>
  totalBeforeFilters: number; providers: ProviderOutcome[]; durationMs: number; coverage: string; warnings: string[]
}
export interface ProviderView {
  id: string; name: string; baseUrl: string; trustStatus: string; automationAllowed: boolean
  lastReviewed?: string; notes?: string; pausedByCircuitBreaker: boolean
}
export interface Filters { types: string[]; access: 'ANY' | 'FREE' | 'OPEN'; yearFrom?: number; yearTo?: number; language?: string }

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
}

async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url, { headers: { Accept: 'application/json' } })
  if (!res.ok) {
    let detail = `Request failed (${res.status})`
    try { const body = await res.json(); if (body?.detail) detail = body.detail } catch { /* keep default */ }
    throw new ApiError(res.status, detail)
  }
  return res.json() as Promise<T>
}

export function searchUrl(q: string, f: Filters, page = 1): string {
  const p = new URLSearchParams({ q, page: String(page) })
  f.types.forEach(t => p.append('types', t))
  if (f.access !== 'ANY') p.set('access', f.access)
  if (f.yearFrom) p.set('yearFrom', String(f.yearFrom))
  if (f.yearTo) p.set('yearTo', String(f.yearTo))
  if (f.language) p.set('language', f.language)
  return `/api/search?${p}`
}

export const search = (q: string, f: Filters, page = 1) => getJson<SearchResponse>(searchUrl(q, f, page))
export const getResource = (id: string) => getJson<Resource>(`/api/resources/${encodeURIComponent(id)}`)
export const getProviders = () => getJson<ProviderView[]>('/api/providers')

/** Only plain http(s) links are ever rendered as hrefs. */
export function safeHref(url: string | undefined): string | undefined {
  if (!url) return undefined
  try {
    const u = new URL(url)
    return u.protocol === 'https:' || u.protocol === 'http:' ? u.toString() : undefined
  } catch { return undefined }
}
