import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import ResourceCard from '../components/ResourceCard'
import ProviderStatus from '../components/ProviderStatus'
import SearchBox from '../components/SearchBox'
import { accessTone } from '../lib/labels'
import { safeHref } from '../lib/api'
import { base, response } from './fixtures'

describe('ResourceCard', () => {
  it('never presents unknown access as free', () => {
    render(<MemoryRouter><ResourceCard r={base} /></MemoryRouter>)
    expect(screen.getByText('Access unknown')).toBeInTheDocument()
    expect(screen.queryByText(/free/i)).not.toBeInTheDocument()
    expect(screen.getByText(/Check access at source/)).toBeInTheDocument()
  })

  it('shows provider-asserted open access, creators, provenance and the reasons', async () => {
    const r = { ...base, accessType: 'OPEN_ACCESS' as const, accessLabel: 'Open access', recommendedAction: 'Read for free', sources: ['openalex', 'arxiv'] }
    render(<MemoryRouter><ResourceCard r={r} /></MemoryRouter>)
    expect(screen.getByText('Open access')).toBeInTheDocument()
    expect(screen.getByText('Ian Goodfellow · 2016')).toBeInTheDocument()
    expect(screen.getByText(/OpenAlex, arXiv/)).toBeInTheDocument()
    await userEvent.click(screen.getByText('Why this result'))
    expect(screen.getByText('Title matches 2 of 2 search terms')).toBeVisible()
  })

  it('links to the detail page only when the resource has an id', () => {
    const { rerender } = render(<MemoryRouter><ResourceCard r={base} /></MemoryRouter>)
    expect(screen.getByRole('link', { name: 'Deep Learning' })).toHaveAttribute('href', '/resources/id-1')
    rerender(<MemoryRouter><ResourceCard r={{ ...base, id: undefined }} /></MemoryRouter>)
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })

  it('renders hostile text as text', () => {
    render(<MemoryRouter><ResourceCard r={{ ...base, title: '<img src=x onerror=alert(1)>' }} /></MemoryRouter>)
    expect(document.querySelector('img')).toBeNull()
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
  })
})

describe('ProviderStatus', () => {
  it('tells the user which sources failed', () => {
    const d = response()
    render(<ProviderStatus outcomes={d.providers} coverage={d.coverage} />)
    expect(screen.getByRole('status')).toHaveTextContent('OpenAlex could not be searched this time')
    expect(screen.getByText(/not exhaustive/)).toBeInTheDocument()
  })

  it('is quiet when everything answered', () => {
    const d = response({ providers: [{ providerId: 'arxiv', displayName: 'arXiv', status: 'OK', recordCount: 3, latencyMs: 1, cached: false }] })
    render(<ProviderStatus outcomes={d.providers} coverage={d.coverage} />)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})

describe('SearchBox', () => {
  it('does not submit blank queries and trims real ones', async () => {
    const onSubmit = vi.fn()
    render(<SearchBox onSubmit={onSubmit} />)
    expect(screen.getByRole('button', { name: 'Search' })).toBeDisabled()
    await userEvent.type(screen.getByRole('searchbox'), '  quantum computing  {enter}')
    expect(onSubmit).toHaveBeenCalledWith('quantum computing')
  })
})

describe('helpers', () => {
  it('safeHref only allows http(s)', () => {
    expect(safeHref('https://arxiv.org/abs/1')).toBe('https://arxiv.org/abs/1')
    expect(safeHref('javascript:alert(1)')).toBeUndefined()
    expect(safeHref('data:text/html,hi')).toBeUndefined()
    expect(safeHref('not a url')).toBeUndefined()
  })
  it('access tone follows the asserted type only', () => {
    expect(accessTone('UNKNOWN')).toBe('unknown')
    expect(accessTone('LIBRARY_BORROWING')).toBe('limited')
    expect(accessTone('RESTRICTED')).toBe('blocked')
    expect(accessTone('PUBLIC_DOMAIN')).toBe('free')
  })
})
