import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ResultsPage from '../pages/ResultsPage'
import ResourcePage from '../pages/ResourcePage'
import { base, response } from './fixtures'

function mockFetch(handler: (url: string) => { status?: number; body: unknown }) {
  const f = vi.fn(async (url: string) => {
    const { status = 200, body } = handler(url)
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', f)
  return f
}

function renderAt(path: string, ui: React.ReactNode, route: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}><Routes><Route path={route} element={ui} /></Routes></MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('ResultsPage', () => {
  it('shows grouped results, recommended picks, and the failed source', async () => {
    const f = mockFetch(() => ({ body: response({ results: [base, { ...base, id: 'id-2', title: 'A Paper', type: 'PAPER' }], recommendedIds: ['id-1'] }) }))
    renderAt('/search?q=deep%20learning', <ResultsPage />, '/search')
    expect(await screen.findByRole('heading', { name: 'Recommended starting points' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /^Books/ })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Research papers and preprints/ })).toBeInTheDocument()
    expect(screen.getByText(/OpenAlex could not be searched this time/)).toBeInTheDocument()
    expect(f).toHaveBeenCalledWith('/api/search?q=deep+learning&page=1', expect.anything())
  })

  it('explains when filters hide everything', async () => {
    mockFetch(() => ({ body: response({ results: [], recommendedIds: [], totalBeforeFilters: 7 }) }))
    renderAt('/search?q=x&access=OPEN', <ResultsPage />, '/search')
    expect(await screen.findByText(/7 results were found but hidden by your filters/)).toBeInTheDocument()
  })

  it('shows an honest message when the API rejects the query', async () => {
    mockFetch(() => ({ status: 400, body: { detail: 'Query is too long (max 300 characters)' } }))
    renderAt('/search?q=x', <ResultsPage />, '/search')
    expect(await screen.findByRole('alert')).toHaveTextContent('Query is too long')
  })

  it('tells the user how the query was interpreted', async () => {
    mockFetch(() => ({ body: response({ query: { raw: 'learn quantum computing', searchedAs: 'quantum computing', mode: 'KEYWORD' } }) }))
    renderAt('/search?q=learn%20quantum%20computing', <ResultsPage />, '/search')
    expect(await screen.findByText('quantum computing', { selector: 'strong' })).toBeInTheDocument()
  })
})

describe('ResourcePage', () => {
  it('lists every location with separate read, download and redistribute permissions and safe links', async () => {
    mockFetch(() => ({
      body: {
        ...base, accessType: 'FREE_TO_READ_ONLINE', accessLabel: 'Free to read online', recommendedAction: 'Read for free',
        relations: [{ kind: 'PREPRINT_OF', id: 'other', title: 'Published version' }],
        locations: [
          { provider: 'arxiv', url: 'https://arxiv.org/pdf/1', kind: 'PDF', format: 'pdf', accessType: 'FREE_TO_READ_ONLINE', accessLabel: 'Free to read online', canRead: 'YES', canDownload: 'YES', canRedistribute: 'UNKNOWN', note: 'Reuse terms depend on the authors.' },
          { provider: 'x', url: 'javascript:alert(1)', accessType: 'UNKNOWN', accessLabel: 'Access unknown', canRead: 'UNKNOWN', canDownload: 'UNKNOWN', canRedistribute: 'UNKNOWN' },
        ],
      },
    }))
    renderAt('/resources/id-1', <ResourcePage />, '/resources/:id')
    expect(await screen.findByRole('heading', { name: 'Deep Learning' })).toBeInTheDocument()
    const link = screen.getByRole('link', { name: /arxiv.org\/pdf\/1/ })
    expect(link).toHaveAttribute('rel', 'noopener noreferrer nofollow')
    expect(link).toHaveAttribute('target', '_blank')
    expect(screen.getByText('Link unavailable')).toBeInTheDocument()
    expect(document.querySelector('a[href^="javascript"]')).toBeNull()
    expect(screen.getAllByText('Not stated').length).toBeGreaterThan(0)
    expect(screen.getByRole('link', { name: 'Published version' })).toHaveAttribute('href', '/resources/other')
  })

  it('shows a clear error for a missing resource', async () => {
    mockFetch(() => ({ status: 404, body: { detail: 'Resource not found' } }))
    renderAt('/resources/nope', <ResourcePage />, '/resources/:id')
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Resource not found'))
  })
})
