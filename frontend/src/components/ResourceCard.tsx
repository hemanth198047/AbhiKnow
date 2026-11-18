import { Link } from 'react-router-dom'
import type { Resource } from '../lib/api'
import { PROVIDER_LABEL, TYPE_LABEL } from '../lib/labels'
import AccessBadge from './AccessBadge'

export default function ResourceCard({ r, headingLevel = 3 }: { r: Resource; headingLevel?: 2 | 3 | 4 }) {
  const H = `h${headingLevel}` as 'h2' | 'h3' | 'h4'
  const formats = Array.from(new Set(r.locations.map(l => l.format?.toUpperCase()).filter(Boolean))) as string[]
  const title = <>{r.title}{r.subtitle ? `: ${r.subtitle}` : ''}</>
  return (
    <article className="rounded-lg border border-stone-200 bg-white p-4 dark:border-stone-800 dark:bg-stone-900">
      <H className="text-lg font-semibold leading-snug">
        {r.id ? <Link to={`/resources/${r.id}`} className="text-blue-800 underline-offset-2 hover:underline dark:text-blue-300">{title}</Link> : title}
      </H>
      <p className="mt-1 text-sm text-stone-700 dark:text-stone-300">
        {r.authors.length ? r.authors.slice(0, 4).join(', ') + (r.authors.length > 4 ? ' et al.' : '') : 'Author not listed'}
        {r.year ? ` · ${r.year}` : ''}
      </p>
      <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
        <span className="rounded bg-stone-200 px-2 py-0.5 font-medium dark:bg-stone-800">{TYPE_LABEL[r.type] ?? r.type}</span>
        {formats.length > 0 && <span className="text-stone-600 dark:text-stone-400">{formats.join(' · ')}</span>}
        <AccessBadge type={r.accessType} label={r.accessLabel} />
      </div>
      <p className="mt-2 text-sm"><span className="font-medium">Suggested action:</span> {r.recommendedAction}</p>
      <p className="mt-1 text-xs text-stone-600 dark:text-stone-400">
        Source{r.sources.length > 1 ? 's' : ''}: {r.sources.map(s => PROVIDER_LABEL[s] ?? s).join(', ')}
      </p>
      {r.reasons.length > 0 && (
        <details className="mt-2 text-sm">
          <summary className="cursor-pointer text-stone-700 dark:text-stone-300">Why this result</summary>
          <ul className="mt-1 list-disc pl-5 text-stone-700 dark:text-stone-300">
            {r.reasons.map(x => <li key={x}>{x}</li>)}
          </ul>
        </details>
      )}
    </article>
  )
}
