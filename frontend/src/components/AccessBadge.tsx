import type { AccessType } from '../lib/api'
import { accessTone, TONE_CLASS, TONE_ICON } from '../lib/labels'

/** Text plus icon, never colour alone. */
export default function AccessBadge({ type, label }: { type: AccessType; label: string }) {
  const tone = accessTone(type)
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${TONE_CLASS[tone]}`}>
      <span aria-hidden="true">{TONE_ICON[tone]}</span>{label}
    </span>
  )
}
