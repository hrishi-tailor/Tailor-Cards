import { useEffect, useState } from 'react'
import { buylistChatApi } from '../api/buylistChatApi'
import type { ChatStatus } from '../api/buylistChatApi'
import { BuylistChat } from './BuylistChat'
import { SellBuylist } from './SellBuylist'

/** /sell: the AI buylist chat when enabled (CHATBOT_ENABLED); the classic form only as a fallback when it is off. */
export function SellRoute() {
  const [status, setStatus] = useState<ChatStatus | null | undefined>(undefined)

  useEffect(() => {
    buylistChatApi.status().then(setStatus).catch(() => setStatus(null))
  }, [])

  if (status === undefined) {
    return null
  }
  if (!status?.enabled) {
    return <SellBuylist />
  }
  return <BuylistChat status={status} />
}
