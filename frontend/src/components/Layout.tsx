import { Link, NavLink, Outlet } from 'react-router-dom'

const navClass = ({ isActive }: { isActive: boolean }) =>
  `rounded px-3 py-2 text-sm font-medium ${isActive ? 'bg-stone-200 dark:bg-stone-800' : 'hover:bg-stone-200 dark:hover:bg-stone-800'}`

export default function Layout() {
  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:p-3 focus:text-black">
        Skip to main content
      </a>
      <header className="border-b border-stone-200 dark:border-stone-800">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <Link to="/" className="text-xl font-bold tracking-tight">AbhiKnow</Link>
          <nav aria-label="Primary" className="flex gap-1">
            <NavLink to="/" end className={navClass}>Discover</NavLink>
            <NavLink to="/sources" className={navClass}>Sources</NavLink>
          </nav>
        </div>
      </header>
      <main id="main" className="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
        <Outlet />
      </main>
      <footer className="border-t border-stone-200 px-4 py-6 text-sm text-stone-600 dark:border-stone-800 dark:text-stone-400">
        <div className="mx-auto max-w-5xl space-y-1">
          <p>AbhiKnow searches a limited set of open sources. Results are not exhaustive, and free to read does not mean free to copy or share.</p>
          <p>Thank you to arXiv for use of its open access interoperability.</p>
          <p>Metadata from OpenAlex, Crossref, Open Library and Project Gutenberg.</p>
        </div>
      </footer>
    </div>
  )
}
