import { useQuery } from '@tanstack/react-query'
import { getProviders } from '../lib/api'
import { PROVIDER_LABEL } from '../lib/labels'

const TRUST_TEXT: Record<string, string> = {
  PENDING_REVIEW: 'Pending review: queried for metadata only',
  APPROVED: 'Approved', BLOCKED: 'Blocked: never queried',
}

export default function SourcesPage() {
  const { data, error, isLoading } = useQuery({ queryKey: ['providers'], queryFn: getProviders })
  return (
    <div className="space-y-4">
      <h1 className="text-3xl font-bold">Sources</h1>
      <p className="max-w-3xl">
        These are the providers AbhiKnow can search. Each has a trust status. Nothing here bypasses paywalls, logins or other access controls,
        and sites that distribute copyrighted works without permission are blocked.
      </p>
      {isLoading && <p role="status">Loading…</p>}
      {error && <p role="alert" className="rounded border border-rose-300 bg-rose-50 p-3 text-rose-900">{(error as Error).message}</p>}
      <ul className="space-y-3">
        {data?.map(p => (
          <li key={p.id} className="rounded-lg border border-stone-200 p-4 dark:border-stone-800">
            <h2 className="text-lg font-semibold">{PROVIDER_LABEL[p.id] ?? p.name}</h2>
            <p className="text-sm">{TRUST_TEXT[p.trustStatus] ?? p.trustStatus}{p.pausedByCircuitBreaker ? ' · temporarily paused after repeated failures' : ''}</p>
            {p.lastReviewed && <p className="text-sm text-stone-600 dark:text-stone-400">Terms last looked at: {p.lastReviewed}</p>}
            {p.notes && <p className="mt-1 text-sm">{p.notes}</p>}
          </li>
        ))}
      </ul>
    </div>
  )
}
