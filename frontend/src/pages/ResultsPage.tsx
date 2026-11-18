import { useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { search, type Filters, type Resource } from '../lib/api'
import { SECTIONS } from '../lib/labels'
import SearchBox from '../components/SearchBox'
import FilterBar from '../components/FilterBar'
import ProviderStatus from '../components/ProviderStatus'
import ResourceCard from '../components/ResourceCard'

function parseFilters(p: URLSearchParams): Filters {
  const num = (k: string) => (p.get(k) ? Number(p.get(k)) : undefined)
  const access = p.get('access')
  return {
    types: p.getAll('types'),
    access: access === 'FREE' || access === 'OPEN' ? access : 'ANY',
    yearFrom: num('yearFrom'), yearTo: num('yearTo'), language: p.get('language') ?? undefined,
  }
}

export default function ResultsPage() {
  const [params, setParams] = useSearchParams()
  const nav = useNavigate()
  const q = params.get('q') ?? ''
  const filters = useMemo(() => parseFilters(params), [params])

  const { data, error, isFetching } = useQuery({
    queryKey: ['search', q, filters],
    queryFn: () => search(q, filters),
    enabled: q.trim().length > 0,
  })

  const setFilters = (f: Filters) => {
    const p = new URLSearchParams({ q })
    f.types.forEach(t => p.append('types', t))
    if (f.access !== 'ANY') p.set('access', f.access)
    if (f.yearFrom) p.set('yearFrom', String(f.yearFrom))
    if (f.yearTo) p.set('yearTo', String(f.yearTo))
    if (f.language) p.set('language', f.language)
    setParams(p)
  }

  const recommended: Resource[] = data ? data.recommendedIds.map(id => data.results.find(r => r.id === id)).filter((r): r is Resource => !!r) : []

  return (
    <div className="space-y-5">
      <SearchBox key={q} initial={q} busy={isFetching} onSubmit={nq => nav(`/search?q=${encodeURIComponent(nq)}`)} />
      <FilterBar value={filters} onChange={setFilters} />

      <div aria-live="polite" role="status">
        {isFetching && <p className="py-6 text-center">Searching open sources… this can take several seconds.</p>}
      </div>

      {error && <p role="alert" className="rounded border border-rose-300 bg-rose-50 p-3 text-rose-900">{(error as Error).message}</p>}

      {data && !isFetching && (
        <>
          {data.query.searchedAs !== data.query.raw && (
            <p className="text-sm text-stone-700 dark:text-stone-300">Searched as: <strong>{data.query.searchedAs}</strong></p>
          )}
          <ProviderStatus outcomes={data.providers} coverage={data.coverage} />
          {data.warnings.map(w => <p key={w} role="status" className="text-sm text-amber-800 dark:text-amber-300">{w}</p>)}

          {data.results.length === 0 ? (
            <div className="rounded-lg border border-stone-200 p-6 text-center dark:border-stone-800">
              <p className="font-medium">No results found.</p>
              <p className="mt-1 text-sm">
                {data.totalBeforeFilters > 0
                  ? `${data.totalBeforeFilters} results were found but hidden by your filters. Try clearing them.`
                  : 'Try different keywords, or check the source status above in case a source was unavailable.'}
              </p>
            </div>
          ) : (
            <>
              <p className="text-sm text-stone-700 dark:text-stone-300">
                {data.results.length} results{data.totalBeforeFilters !== data.results.length ? ` (of ${data.totalBeforeFilters} before filters)` : ''} in {(data.durationMs / 1000).toFixed(1)}s
              </p>
              {recommended.length > 0 && (
                <section aria-labelledby="rec-h" className="space-y-3">
                  <h2 id="rec-h" className="text-xl font-bold">Recommended starting points</h2>
                  <p className="text-sm text-stone-700 dark:text-stone-300">The best-ranked results of each type. See “Why this result” on each card.</p>
                  <div className="grid gap-3 md:grid-cols-2">{recommended.map(r => <ResourceCard key={r.id} r={r} />)}</div>
                </section>
              )}
              {SECTIONS.map(s => {
                const items = data.results.filter(r => s.types.includes(r.type))
                if (items.length === 0) return null
                return (
                  <section key={s.id} aria-labelledby={`h-${s.id}`} className="space-y-3">
                    <h2 id={`h-${s.id}`} className="text-xl font-bold">{s.title} <span className="text-base font-normal text-stone-600 dark:text-stone-400">({items.length})</span></h2>
                    <div className="grid gap-3 md:grid-cols-2">{items.map(r => <ResourceCard key={r.id ?? r.title} r={r} />)}</div>
                  </section>
                )
              })}
            </>
          )}
        </>
      )}
    </div>
  )
}
