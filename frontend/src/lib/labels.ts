import type { AccessType, ProviderOutcome } from './api'

export const TYPE_LABEL: Record<string, string> = {
  BOOK: 'Book', PAPER: 'Paper', PREPRINT: 'Preprint', THESIS: 'Thesis', COURSE: 'Course', VIDEO: 'Video', AUDIO: 'Audio',
  NEWS: 'News', CODE: 'Code', DATASET: 'Dataset', DOCUMENTATION: 'Documentation', OTHER: 'Other',
}

export const PROVIDER_LABEL: Record<string, string> = {
  openalex: 'OpenAlex', crossref: 'Crossref', arxiv: 'arXiv', openlibrary: 'Open Library', gutenberg: 'Project Gutenberg',
}

/** Result sections. Each resource type appears in exactly one section. */
export const SECTIONS: { id: string; title: string; types: string[] }[] = [
  { id: 'books', title: 'Books', types: ['BOOK'] },
  { id: 'papers', title: 'Research papers and preprints', types: ['PAPER', 'PREPRINT', 'THESIS'] },
  { id: 'datasets', title: 'Datasets', types: ['DATASET'] },
  { id: 'other', title: 'Other resources', types: ['OTHER', 'COURSE', 'VIDEO', 'AUDIO', 'NEWS', 'CODE', 'DOCUMENTATION'] },
]

export type Tone = 'free' | 'limited' | 'unknown' | 'blocked'

/** Never inferred: tone follows the provider-asserted access type only. */
export function accessTone(t: AccessType): Tone {
  switch (t) {
    case 'PUBLIC_DOMAIN': case 'OPEN_ACCESS': case 'FREE_VERIFIED_PERMISSION': case 'FREE_TO_READ_ONLINE': case 'FREE_REGISTRATION':
      return 'free'
    case 'LIBRARY_BORROWING': case 'USER_OWNED': return 'limited'
    case 'RESTRICTED': case 'METADATA_ONLY': case 'SUBSCRIPTION': case 'PURCHASE': case 'RENTAL': case 'INSTITUTIONAL':
    case 'AUTHENTICATION_REQUIRED': case 'CAPTCHA_REQUIRED': case 'WAITING_PERIOD':
      return 'blocked'
    default: return 'unknown'
  }
}

export const TONE_CLASS: Record<Tone, string> = {
  free: 'bg-emerald-100 text-emerald-900 ring-emerald-300 dark:bg-emerald-950 dark:text-emerald-200 dark:ring-emerald-800',
  limited: 'bg-amber-100 text-amber-900 ring-amber-300 dark:bg-amber-950 dark:text-amber-200 dark:ring-amber-800',
  blocked: 'bg-rose-100 text-rose-900 ring-rose-300 dark:bg-rose-950 dark:text-rose-200 dark:ring-rose-800',
  unknown: 'bg-stone-200 text-stone-800 ring-stone-300 dark:bg-stone-800 dark:text-stone-200 dark:ring-stone-700',
}
export const TONE_ICON: Record<Tone, string> = { free: '✓', limited: '◐', blocked: '⛔', unknown: '?' }

export const STATUS_TEXT: Record<ProviderOutcome['status'], string> = {
  OK: 'Answered', TIMEOUT: 'Timed out', ERROR: 'Failed', RATE_LIMITED: 'Rate limited', SKIPPED: 'Not applicable',
  CIRCUIT_OPEN: 'Paused', DISABLED: 'Disabled',
}

export const PERMISSION_TEXT: Record<string, string> = { YES: 'Yes', NO: 'No', UNKNOWN: 'Not stated' }

export const RELATION_TEXT: Record<string, string> = {
  PREPRINT_OF: 'Preprint of', HAS_PREPRINT: 'Has preprint', EDITION: 'Other edition',
}
