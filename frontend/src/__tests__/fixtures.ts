import type { Resource, SearchResponse } from '../lib/api'

export const base: Resource = {
  id: 'id-1', title: 'Deep Learning', type: 'BOOK', authors: ['Ian Goodfellow'], year: 2016, identifiers: [], locations: [],
  sources: ['openlibrary'], subjects: [], accessType: 'UNKNOWN', accessLabel: 'Access unknown', recommendedAction: 'Check access at source',
  score: 0.5, reasons: ['Title matches 2 of 2 search terms'], relations: [],
}

export function response(over: Partial<SearchResponse> = {}): SearchResponse {
  return {
    query: { raw: 'deep learning', searchedAs: 'deep learning', mode: 'KEYWORD' }, page: 1,
    results: [base], recommendedIds: ['id-1'], countsByType: { BOOK: 1 }, totalBeforeFilters: 1,
    providers: [
      { providerId: 'openlibrary', displayName: 'Open Library', status: 'OK', recordCount: 1, latencyMs: 500, cached: false },
      { providerId: 'openalex', displayName: 'OpenAlex', status: 'RATE_LIMITED', recordCount: 0, latencyMs: 10, message: 'Provider rate limit (HTTP 429)', cached: false },
    ],
    durationMs: 1200, coverage: 'Searched 1 of 2 providers that could answer this query. AbhiKnow searches a limited set of open sources; results are not exhaustive.',
    warnings: [], ...over,
  }
}
