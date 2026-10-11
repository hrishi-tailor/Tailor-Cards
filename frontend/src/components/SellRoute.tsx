import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { buylistChatApi } from '../api/buylistChatApi'
import type { ChatStatus } from '../api/buylistChatApi'
import { BuylistChat } from './BuylistChat'
import { SellBuylist } from './SellBuylist'

/** /sell: the AI buylist chat when enabled (CHATBOT_ENABLED), otherwise the classic form. */
export function SellRoute() {
  const [params] = useSearchParams()
  const [status, setStatus] = useState<ChatStatus | null | undefined>(undefined)

  useEffect(() => {
    buylistChatApi.status().then(setStatus).catch(() => setStatus(null))
  }, [])

  if (status === undefined) {
    return null
  }
  if (!status?.enabled || params.get('form') === '1') {
    return <SellBuylist />
  }
  return <BuylistChat status={status} />
}
