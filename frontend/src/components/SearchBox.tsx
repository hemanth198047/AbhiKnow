import { useState, type FormEvent } from 'react'

interface Props { initial?: string; onSubmit: (q: string) => void; large?: boolean; busy?: boolean }

export default function SearchBox({ initial = '', onSubmit, large, busy }: Props) {
  const [value, setValue] = useState(initial)
  const submit = (e: FormEvent) => {
    e.preventDefault()
    const q = value.trim()
    if (q) onSubmit(q)
  }
  return (
    <form onSubmit={submit} role="search" aria-label="Search for knowledge resources" className="flex w-full gap-2">
      <label htmlFor="q" className="sr-only">What do you want to learn or find?</label>
      <input
        id="q" name="q" type="search" value={value} maxLength={300} autoComplete="off"
        onChange={e => setValue(e.target.value)}
        placeholder="e.g. I want to become an expert in artificial intelligence"
        className={`min-w-0 flex-1 rounded-lg border border-stone-400 bg-white px-4 text-stone-900 placeholder:text-stone-500 dark:border-stone-600 dark:bg-stone-900 dark:text-stone-100 ${large ? 'py-4 text-lg' : 'py-2'}`}
      />
      <button type="submit" disabled={busy || !value.trim()}
        className={`rounded-lg bg-blue-700 px-5 font-semibold text-white hover:bg-blue-800 disabled:opacity-50 ${large ? 'text-lg' : ''}`}>
        Search
      </button>
    </form>
  )
}
