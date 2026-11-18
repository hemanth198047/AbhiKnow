import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getResource, safeHref } from '../lib/api'
import { PERMISSION_TEXT, PROVIDER_LABEL, RELATION_TEXT, TYPE_LABEL } from '../lib/labels'
import AccessBadge from '../components/AccessBadge'

export default function ResourcePage() {
  const { id = '' } = useParams()
  const nav = useNavigate()
  const { data: r, error, isLoading } = useQuery({ queryKey: ['resource', id], queryFn: () => getResource(id) })

  if (isLoading) return <p role="status">Loading…</p>
  if (error || !r) return <p role="alert" className="rounded border border-rose-300 bg-rose-50 p-3 text-rose-900">{(error as Error)?.message ?? 'Resource not found.'}</p>

  return (
    <article className="space-y-6">
      <p><button type="button" onClick={() => nav(-1)} className="text-sm text-blue-800 underline dark:text-blue-300">← Back</button></p>
      <header>
        <p className="text-sm font-medium text-stone-600 dark:text-stone-400">{TYPE_LABEL[r.type] ?? r.type}</p>
        <h1 className="text-3xl font-bold leading-tight">{r.title}</h1>
        {r.subtitle && <p className="text-lg text-stone-700 dark:text-stone-300">{r.subtitle}</p>}
        <p className="mt-2">{r.authors.length ? r.authors.join(', ') : 'Author not listed'}{r.year ? ` · ${r.year}` : ''}{r.publisher ? ` · ${r.publisher}` : ''}</p>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <AccessBadge type={r.accessType} label={r.accessLabel} />
          <span className="text-sm">Suggested action: <strong>{r.recommendedAction}</strong></span>
        </div>
      </header>

      {r.description && <section aria-labelledby="d"><h2 id="d" className="text-xl font-bold">Summary from the source</h2><p className="mt-1 max-w-3xl whitespace-pre-line">{r.description}</p></section>}

      <section aria-labelledby="loc">
        <h2 id="loc" className="text-xl font-bold">Where to get it</h2>
        <p className="mt-1 text-sm text-stone-700 dark:text-stone-300">
          Access is what each source states. Being free to read does not mean you may copy or share it. Links open the provider&apos;s own site.
        </p>
        <ul className="mt-3 space-y-3">
          {r.locations.map(l => {
            const href = safeHref(l.url)
            return (
              <li key={l.url} className="rounded-lg border border-stone-200 p-3 dark:border-stone-800">
                <div className="flex flex-wrap items-center gap-2">
                  <AccessBadge type={l.accessType} label={l.accessLabel} />
                  <span className="text-sm">{PROVIDER_LABEL[l.provider] ?? l.provider}{l.kind ? ` · ${l.kind}` : ''}{l.format && l.format.toUpperCase() !== l.kind ? ` · ${l.format.toUpperCase()}` : ''}</span>
                </div>
                {href
                  ? <a href={href} target="_blank" rel="noopener noreferrer nofollow" className="mt-1 block break-all text-blue-800 underline dark:text-blue-300">{href}<span className="sr-only"> (opens in a new tab)</span></a>
                  : <p className="mt-1 text-sm">Link unavailable</p>}
                <dl className="mt-2 grid grid-cols-3 gap-2 text-sm">
                  <div><dt className="font-medium">Read</dt><dd>{PERMISSION_TEXT[l.canRead]}</dd></div>
                  <div><dt className="font-medium">Download</dt><dd>{PERMISSION_TEXT[l.canDownload]}</dd></div>
                  <div><dt className="font-medium">Redistribute</dt><dd>{PERMISSION_TEXT[l.canRedistribute]}</dd></div>
                </dl>
                {l.license && <p className="mt-1 text-sm">License: {l.license}</p>}
                {l.note && <p className="mt-1 text-sm text-stone-700 dark:text-stone-300">{l.note}</p>}
              </li>
            )
          })}
        </ul>
      </section>

      {r.relations.length > 0 && (
        <section aria-labelledby="rel">
          <h2 id="rel" className="text-xl font-bold">Related versions</h2>
          <ul className="mt-2 list-disc pl-5">
            {r.relations.map(x => <li key={x.kind + x.id}>{RELATION_TEXT[x.kind] ?? x.kind}: <Link to={`/resources/${x.id}`} className="text-blue-800 underline dark:text-blue-300">{x.title}</Link></li>)}
          </ul>
        </section>
      )}

      <section aria-labelledby="meta">
        <h2 id="meta" className="text-xl font-bold">Details</h2>
        <dl className="mt-2 grid max-w-3xl grid-cols-[10rem_1fr] gap-x-4 gap-y-1 text-sm">
          {r.identifiers.length > 0 && (<><dt className="font-medium">Identifiers</dt><dd>{r.identifiers.map(i => `${i.scheme}: ${i.value}`).join(' · ')}</dd></>)}
          {r.language && (<><dt className="font-medium">Language</dt><dd>{r.language}</dd></>)}
          {r.subjects.length > 0 && (<><dt className="font-medium">Subjects</dt><dd>{r.subjects.join(', ')}</dd></>)}
          {r.citedBy != null && (<><dt className="font-medium">Citations</dt><dd>{r.citedBy} (context only; not a measure of correctness)</dd></>)}
          <dt className="font-medium">Metadata from</dt><dd>{r.sources.map(s => PROVIDER_LABEL[s] ?? s).join(', ')}</dd>
        </dl>
      </section>
    </article>
  )
}
