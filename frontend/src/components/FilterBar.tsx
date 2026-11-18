import type { Filters } from '../lib/api'

const TYPES = [['BOOK', 'Books'], ['PAPER', 'Papers'], ['PREPRINT', 'Preprints'], ['THESIS', 'Theses'], ['DATASET', 'Datasets']] as const

interface Props { value: Filters; onChange: (f: Filters) => void }

export default function FilterBar({ value, onChange }: Props) {
  const toggle = (t: string) =>
    onChange({ ...value, types: value.types.includes(t) ? value.types.filter(x => x !== t) : [...value.types, t] })
  const num = (s: string) => (s === '' ? undefined : Number(s))
  return (
    <form aria-label="Filter results" className="space-y-3 rounded-lg border border-stone-200 p-3 text-sm dark:border-stone-800" onSubmit={e => e.preventDefault()}>
      <fieldset>
        <legend className="font-semibold">Type</legend>
        <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1">
          {TYPES.map(([id, label]) => (
            <label key={id} className="flex items-center gap-1.5">
              <input type="checkbox" checked={value.types.includes(id)} onChange={() => toggle(id)} /> {label}
            </label>
          ))}
        </div>
      </fieldset>
      <div className="flex flex-wrap items-end gap-4">
        <div>
          <label htmlFor="access" className="block font-semibold">Access</label>
          <select id="access" value={value.access} onChange={e => onChange({ ...value, access: e.target.value as Filters['access'] })}
            className="mt-1 rounded border border-stone-400 bg-white px-2 py-1 dark:border-stone-600 dark:bg-stone-900">
            <option value="ANY">Any</option>
            <option value="FREE">Free to read (provider-stated)</option>
            <option value="OPEN">Open license or public domain</option>
          </select>
        </div>
        <div>
          <label htmlFor="yf" className="block font-semibold">From year</label>
          <input id="yf" type="number" min={1000} max={2100} value={value.yearFrom ?? ''} onChange={e => onChange({ ...value, yearFrom: num(e.target.value) })}
            className="mt-1 w-24 rounded border border-stone-400 bg-white px-2 py-1 dark:border-stone-600 dark:bg-stone-900" />
        </div>
        <div>
          <label htmlFor="yt" className="block font-semibold">To year</label>
          <input id="yt" type="number" min={1000} max={2100} value={value.yearTo ?? ''} onChange={e => onChange({ ...value, yearTo: num(e.target.value) })}
            className="mt-1 w-24 rounded border border-stone-400 bg-white px-2 py-1 dark:border-stone-600 dark:bg-stone-900" />
        </div>
        <div>
          <label htmlFor="lang" className="block font-semibold">Language</label>
          <select id="lang" value={value.language ?? ''} onChange={e => onChange({ ...value, language: e.target.value || undefined })}
            className="mt-1 rounded border border-stone-400 bg-white px-2 py-1 dark:border-stone-600 dark:bg-stone-900">
            <option value="">Any</option>
            <option value="en">English</option>
          </select>
        </div>
      </div>
      <p className="text-xs text-stone-600 dark:text-stone-400">
        Filters are applied to what the sources returned. Results without a stated year or language are hidden when you filter on them.
      </p>
    </form>
  )
}
