import { useNavigate } from 'react-router-dom'
import SearchBox from '../components/SearchBox'

const EXAMPLES = ['Artificial Intelligence from beginner to advanced research', 'quantum computing', 'author:"Geoffrey Hinton"', '10.1038/nature14539']

export default function HomePage() {
  const nav = useNavigate()
  const go = (q: string) => nav(`/search?q=${encodeURIComponent(q)}`)
  return (
    <div className="mx-auto max-w-2xl py-12 text-center">
      <h1 className="text-4xl font-bold tracking-tight">The World&apos;s Knowledge. One Intelligent Companion.</h1>
      <p className="mt-3 text-stone-700 dark:text-stone-300">
        Search books and research papers across open sources. See plainly what you can read for free and what is restricted.
      </p>
      <div className="mt-8"><SearchBox onSubmit={go} large /></div>
      <div className="mt-6 text-left text-sm">
        <p className="font-medium">Try:</p>
        <ul className="mt-1 flex flex-wrap gap-2">
          {EXAMPLES.map(e => (
            <li key={e}><button type="button" onClick={() => go(e)} className="rounded-full border border-stone-400 px-3 py-1 hover:bg-stone-200 dark:border-stone-600 dark:hover:bg-stone-800">{e}</button></li>
          ))}
        </ul>
        <p className="mt-4 text-stone-600 dark:text-stone-400">
          You can paste a DOI or ISBN, search by author, or put a phrase in quotes. AbhiKnow works without any AI service.
        </p>
      </div>
    </div>
  )
}
