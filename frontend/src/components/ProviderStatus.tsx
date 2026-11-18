import type { ProviderOutcome } from '../lib/api'
import { PROVIDER_LABEL, STATUS_TEXT } from '../lib/labels'

export default function ProviderStatus({ outcomes, coverage }: { outcomes: ProviderOutcome[]; coverage: string }) {
  const problems = outcomes.filter(o => ['TIMEOUT', 'ERROR', 'RATE_LIMITED', 'CIRCUIT_OPEN'].includes(o.status))
  return (
    <section aria-label="Sources searched" className="rounded-lg border border-stone-200 p-3 text-sm dark:border-stone-800">
      <p>{coverage}</p>
      {problems.length > 0 && (
        <p role="status" className="mt-2 font-medium text-amber-800 dark:text-amber-300">
          {problems.map(p => PROVIDER_LABEL[p.providerId] ?? p.displayName).join(', ')} could not be searched this time, so some results may be missing.
        </p>
      )}
      <details className="mt-2">
        <summary className="cursor-pointer">Details per source</summary>
        <ul className="mt-2 space-y-1">
          {outcomes.map(o => (
            <li key={o.providerId}>
              <span className="font-medium">{PROVIDER_LABEL[o.providerId] ?? o.displayName}:</span> {STATUS_TEXT[o.status]}
              {o.status === 'OK' && ` (${o.recordCount} found${o.cached ? ', cached' : ''})`}
              {o.message && o.status !== 'OK' ? ` – ${o.message}` : ''}
            </li>
          ))}
        </ul>
      </details>
    </section>
  )
}
