export interface ImportConfirmation {
  kind: 'import_confirmation'
  turnId: string
  spaceLabel: string
  folderName: string
  folderSegments: string[]
  urls: string[]
  expiresAt: number
  consumed: boolean
  status: 'pending' | 'cancelled' | 'unknown'
  confirmToken?: string
  cancelToken?: string
}

export function confirmationLockKey(conversationId: string, turnId: string): string {
  return `agent-import-confirmation:${conversationId}:${turnId}`
}

export function persistConfirmationLock(storage: Pick<Storage, 'setItem' | 'getItem'>, key: string): boolean {
  try { storage.setItem(key, '1'); return storage.getItem(key) === '1' } catch { return false }
}

export function importConfirmationStatus(card: ImportConfirmation, now: number, uncertain: boolean):
    'pending' | 'cancelled' | 'unknown' | 'expired' {
  if (card.status === 'cancelled') return 'cancelled'
  if (uncertain || card.consumed || card.status === 'unknown') return 'unknown'
  if (now >= Number(card.expiresAt)) return 'expired'
  if (card.status !== 'pending' || !card.confirmToken || !card.cancelToken || !Number.isFinite(Number(card.expiresAt))) return 'unknown'
  return 'pending'
}

export function importConfirmationAction(card: ImportConfirmation, action: 'confirm' | 'cancel',
    now: number, uncertain: boolean): {turnId: string; token: string} | null {
  if (importConfirmationStatus(card, now, uncertain) !== 'pending') return null
  const token = action === 'confirm' ? card.confirmToken : card.cancelToken
  return token ? { turnId: card.turnId, token } : null
}

