import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
  sendTradeMessage,
  getTradeQuote,
  submitTradeRequest,
  fetchStoreProducts,
} from '../api/tradeAssistantApi';
import type {
  Product,
  TradeFlowType,
  CustomerCardItem,
  TradeChatMessage,
  TradeQuoteResponse,
  TradeSubmissionResponse,
} from '../types';
import './TradeAssistant.css';

export function TradeAssistant() {
  const [flowType, setFlowType] = useState<TradeFlowType>('SELL');
  const [conversationId, setConversationId] = useState<string>('');
  const [messages, setMessages] = useState<TradeChatMessage[]>([
    {
      role: 'assistant',
      content: 'Would you like to sell or trade?',
    },
  ]);
  const [inputMessage, setInputMessage] = useState('');
  const [extractedCards, setExtractedCards] = useState<CustomerCardItem[]>([]);
  const [availableProducts, setAvailableProducts] = useState<Product[]>([]);
  const [selectedProductIds, setSelectedProductIds] = useState<number[]>([]);

  const [isLoadingChat, setIsLoadingChat] = useState(false);
  const [isLoadingQuote, setIsLoadingQuote] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const [quoteResult, setQuoteResult] = useState<TradeQuoteResponse | null>(null);
  const [submissionResult, setSubmissionResult] = useState<TradeSubmissionResponse | null>(null);

  // Customer contact form for quote submission
  const [customerName, setCustomerName] = useState('');
  const [customerEmail, setCustomerEmail] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [customerNotes, setCustomerNotes] = useState('');

  // Load store inventory for Trade flow
  useEffect(() => {
    fetchStoreProducts()
      .then((prods) => setAvailableProducts(prods))
      .catch(() => setAvailableProducts([]));
  }, []);

  const handleSelectMode = (mode: TradeFlowType) => {
    setFlowType(mode);
    setQuoteResult(null);
    setSubmissionResult(null);

    const promptText =
      mode === 'SELL'
        ? "I want to sell my Pokémon cards for cash."
        : "I want to trade my Pokémon cards for store inventory.";

    const initialReply =
      mode === 'SELL'
        ? "Great! What cards are you looking to sell? Tell me the card name, set, or condition (e.g. Charizard Base Set Near Mint)."
        : "Awesome! What cards are you trading in, and what store card(s) caught your eye?";

    setMessages([
      { role: 'assistant', content: 'Would you like to sell or trade?' },
      { role: 'user', content: promptText },
      { role: 'assistant', content: initialReply },
    ]);
  };

  const handleSendMessage = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!inputMessage.trim() || isLoadingChat) return;

    const userText = inputMessage.trim();
    setInputMessage('');
    setErrorMsg(null);

    const updatedMessages: TradeChatMessage[] = [
      ...messages,
      { role: 'user', content: userText },
    ];
    setMessages(updatedMessages);
    setIsLoadingChat(true);

    try {
      const response = await sendTradeMessage({
        conversationId: conversationId || undefined,
        flowType,
        messages: updatedMessages,
        confirmedItems: extractedCards,
        storeProductIds: selectedProductIds,
      });

      setConversationId(response.conversationId);
      setMessages([
        ...updatedMessages,
        { role: 'assistant', content: response.reply },
      ]);

      if (response.extractedItems && response.extractedItems.length > 0) {
        setExtractedCards(response.extractedItems);
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Error communicating with assistant. Please try again.');
    } finally {
      setIsLoadingChat(false);
    }
  };

  const handleToggleConfirmCard = (index: number) => {
    setExtractedCards((prev) =>
      prev.map((card, idx) =>
        idx === index ? { ...card, confirmed: !card.confirmed } : card
      )
    );
  };

  const handleRemoveCard = (index: number) => {
    setExtractedCards((prev) => prev.filter((_, idx) => idx !== index));
    setQuoteResult(null);
  };

  const handleToggleStoreProduct = (prodId: number) => {
    setSelectedProductIds((prev) =>
      prev.includes(prodId) ? prev.filter((id) => id !== prodId) : [...prev, prodId]
    );
    setQuoteResult(null);
  };

  const allCardsConfirmed =
    extractedCards.length > 0 &&
    extractedCards.every((c) => c.confirmed === true);

  const handleComputeQuote = async () => {
    if (!allCardsConfirmed || isLoadingQuote) return;

    setIsLoadingQuote(true);
    setErrorMsg(null);

    try {
      const quote = await getTradeQuote({
        flowType,
        customerCards: extractedCards,
        storeProductIds: flowType === 'TRADE' ? selectedProductIds : undefined,
      });

      setQuoteResult(quote);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to compute quote.');
    } finally {
      setIsLoadingQuote(false);
    }
  };

  const handleSubmitQuote = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!quoteResult || !customerName.trim() || !customerEmail.trim() || isSubmitting) {
      return;
    }

    setIsSubmitting(true);
    setErrorMsg(null);

    try {
      const result = await submitTradeRequest({
        quote: {
          flowType: quoteResult.flowType,
          customerCards: quoteResult.customerCards,
          storeProductIds:
            quoteResult.flowType === 'TRADE' ? selectedProductIds : undefined,
        },
        customerName: customerName.trim(),
        customerEmail: customerEmail.trim(),
        customerPhone: customerPhone.trim() || undefined,
        customerNotes: customerNotes.trim() || undefined,
      });

      setSubmissionResult(result);
    } catch (err: any) {
      setErrorMsg(err.message || 'Failed to submit trade quote.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="tc-assistant-container">
      <div className="tc-assistant-header">
        <h1 className="tc-assistant-title">Sell or Trade Assistant</h1>
        <p className="tc-assistant-subtitle">
          Describe Pokémon cards to get an instant valuation, cash offer, or trade credit powered by live market rates.
        </p>
      </div>

      {errorMsg && (
        <div className="tc-message-bubble tc-message-assistant" style={{ borderColor: 'var(--tc-status-red)' }}>
          <strong style={{ color: 'var(--tc-status-red)' }}>Notice:</strong> {errorMsg}
          <div style={{ marginTop: 8 }}>
            <Link to="/sell" style={{ color: 'var(--tc-text-primary)', textDecoration: 'underline' }}>
              Switch to manual buylist submission
            </Link>
          </div>
        </div>
      )}

      {/* Chat Window */}
      <div className="tc-chat-window">
        <div className="tc-chat-messages">
          {messages.map((m, idx) => (
            <div
              key={idx}
              className={`tc-message-bubble ${
                m.role === 'assistant' ? 'tc-message-assistant' : 'tc-message-user'
              }`}
            >
              {m.content}
              {idx === 0 && (
                <div className="tc-mode-selection">
                  <button
                    type="button"
                    className={`tc-mode-btn ${flowType === 'SELL' ? 'active' : ''}`}
                    onClick={() => handleSelectMode('SELL')}
                  >
                    💰 Sell for Cash
                  </button>
                  <button
                    type="button"
                    className={`tc-mode-btn ${flowType === 'TRADE' ? 'active' : ''}`}
                    onClick={() => handleSelectMode('TRADE')}
                  >
                    🔄 Trade for Cards
                  </button>
                </div>
              )}
            </div>
          ))}

          {isLoadingChat && (
            <div className="tc-message-bubble tc-message-assistant">
              <span className="tc-loading-spinner" /> Finding cards...
            </div>
          )}
        </div>

        {/* Input bar */}
        <form className="tc-chat-input-bar" onSubmit={handleSendMessage}>
          <input
            type="text"
            className="tc-chat-input"
            placeholder={
              flowType === 'SELL'
                ? "Describe cards (e.g. PSA 10 Charizard Base Set 4/102, sealed booster box)..."
                : "Describe incoming cards or asking trade cards..."
            }
            value={inputMessage}
            onChange={(e) => setInputMessage(e.target.value)}
            disabled={isLoadingChat}
          />
          <button
            type="submit"
            className="tc-send-btn"
            disabled={!inputMessage.trim() || isLoadingChat}
          >
            Send
          </button>
        </form>
      </div>

      {/* Matched Cards Section */}
      {extractedCards.length > 0 && (
        <section className="tc-cards-section">
          <div className="tc-section-header">
            <h2 className="tc-section-title">
              Matched Cards ({extractedCards.length})
            </h2>
            <span style={{ fontSize: 13, color: 'var(--tc-text-secondary)' }}>
              {allCardsConfirmed ? '✓ All matches confirmed' : 'Please confirm each card match to proceed'}
            </span>
          </div>

          <div className="tc-cards-grid">
            {extractedCards.map((card, index) => (
              <div
                key={index}
                className={`tc-card-item ${card.confirmed ? 'confirmed' : ''}`}
              >
                <div className="tc-card-img-wrap">
                  {card.imageUrl ? (
                    <img
                      src={card.imageUrl}
                      alt={card.name}
                      className="tc-card-thumb"
                      loading="lazy"
                      onError={(e) => {
                        (e.target as HTMLElement).style.display = 'none';
                      }}
                    />
                  ) : (
                    <span className="tc-card-placeholder">No Art</span>
                  )}
                </div>

                <div className="tc-card-details">
                  <div>
                    <h3 className="tc-card-name">{card.name}</h3>
                    <div className="tc-card-meta">
                      {card.set && <span>Set: {card.set}</span>}
                      {card.cardNumber && <span>#{card.cardNumber}</span>}
                    </div>

                    <div className="tc-badge-row">
                      {card.condition && (
                        <span className="tc-tag">{card.condition}</span>
                      )}
                      {card.grading && (
                        <span className="tc-tag tc-tag-green">{card.grading}</span>
                      )}
                      {card.isSealed && (
                        <span className="tc-tag tc-tag-green">Sealed</span>
                      )}
                      {card.quantity && card.quantity > 1 && (
                        <span className="tc-tag">Qty: {card.quantity}</span>
                      )}
                    </div>
                  </div>

                  <div className="tc-card-actions">
                    <button
                      type="button"
                      className={`tc-action-btn tc-confirm-btn ${
                        card.confirmed ? 'is-confirmed' : ''
                      }`}
                      onClick={() => handleToggleConfirmCard(index)}
                    >
                      {card.confirmed ? 'Confirmed ✓' : 'Confirm Match'}
                    </button>
                    <button
                      type="button"
                      className="tc-action-btn tc-remove-btn"
                      onClick={() => handleRemoveCard(index)}
                    >
                      Remove
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Store Items Selection for TRADE flow */}
      {flowType === 'TRADE' && extractedCards.length > 0 && (
        <section className="tc-store-section">
          <div className="tc-section-header">
            <h2 className="tc-section-title">
              Target Store Inventory ({selectedProductIds.length} selected)
            </h2>
          </div>
          <p style={{ fontSize: 13, color: 'var(--tc-text-secondary)', margin: '0 0 12px' }}>
            Choose the cards from Tailor Cards inventory you wish to receive in exchange:
          </p>

          <div className="tc-store-select-row">
            <select
              className="tc-store-dropdown"
              onChange={(e) => {
                const id = Number(e.target.value);
                if (id) {
                  handleToggleStoreProduct(id);
                  e.target.value = '';
                }
              }}
              defaultValue=""
            >
              <option value="" disabled>
                Select a card from Tailor Cards inventory...
              </option>
              {availableProducts.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} — ${p.price?.toFixed(2)} CAD
                </option>
              ))}
            </select>
          </div>

          {selectedProductIds.length > 0 && (
            <div className="tc-badge-row" style={{ marginTop: 12 }}>
              {selectedProductIds.map((id) => {
                const prod = availableProducts.find((p) => p.id === id);
                return (
                  <span
                    key={id}
                    className="tc-tag tc-tag-green"
                    style={{ cursor: 'pointer', padding: '6px 10px' }}
                    onClick={() => handleToggleStoreProduct(id)}
                    title="Click to remove"
                  >
                    {prod ? `${prod.name} ($${prod.price?.toFixed(2)})` : `Item #${id}`} ✕
                  </span>
                );
              })}
            </div>
          )}
        </section>
      )}

      {/* Quote Calculation Action */}
      {extractedCards.length > 0 && !submissionResult && (
        <div className="tc-quote-cta">
          <button
            type="button"
            className="tc-calculate-btn"
            onClick={handleComputeQuote}
            disabled={!allCardsConfirmed || isLoadingQuote}
          >
            {isLoadingQuote ? (
              <>
                <span className="tc-loading-spinner" /> Valuating Cards...
              </>
            ) : (
              'Calculate Instant Quote'
            )}
          </button>
          {!allCardsConfirmed && (
            <span style={{ fontSize: 13, color: 'var(--tc-text-muted)' }}>
              Confirm each card match above before calculating quote.
            </span>
          )}
        </div>
      )}

      {/* Computed Quote Result */}
      {quoteResult && !submissionResult && (
        <div className="tc-quote-result-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span
              className={`tc-quote-badge ${
                quoteResult.decision === 'ACCEPT'
                  ? 'tc-badge-accept'
                  : quoteResult.decision === 'COUNTER'
                  ? 'tc-badge-counter'
                  : quoteResult.decision === 'DECLINE'
                  ? 'tc-badge-decline'
                  : 'tc-badge-review'
              }`}
            >
              {quoteResult.decision === 'ACCEPT' && 'Offer Accepted'}
              {quoteResult.decision === 'COUNTER' && 'Counteroffer (Top-Up)'}
              {quoteResult.decision === 'DECLINE' && 'Offer Declined'}
              {quoteResult.decision === 'NEEDS_REVIEW' && 'Manual Appraisal Needed'}
            </span>
            <span style={{ fontSize: 13, color: 'var(--tc-text-muted)' }}>
              Flow: {quoteResult.flowType}
            </span>
          </div>

          <div className="tc-quote-financials">
            {quoteResult.flowType === 'SELL' ? (
              <div>
                <div className="tc-fin-label">Cash Offer</div>
                <div className="tc-fin-val tc-fin-val-highlight">
                  ${quoteResult.cashOffer?.toFixed(2) ?? '0.00'} CAD
                </div>
              </div>
            ) : (
              <>
                <div>
                  <div className="tc-fin-label">Trade Credit</div>
                  <div className="tc-fin-val tc-fin-val-highlight">
                    ${quoteResult.tradeCredit?.toFixed(2) ?? '0.00'} CAD
                  </div>
                </div>
                {quoteResult.decision === 'COUNTER' && (
                  <div>
                    <div className="tc-fin-label">Cash Top-Up Needed</div>
                    <div className="tc-fin-val" style={{ color: 'var(--tc-status-blue)' }}>
                      ${quoteResult.counterTopUp?.toFixed(2) ?? '0.00'} CAD
                    </div>
                  </div>
                )}
              </>
            )}

            <div>
              <div className="tc-fin-label">Your Cards Market Value</div>
              <div className="tc-fin-val">
                ${quoteResult.customerTotalMarketCad?.toFixed(2) ?? '0.00'} CAD
              </div>
            </div>

            {quoteResult.flowType === 'TRADE' && (
              <div>
                <div className="tc-fin-label">Store Cards List Price</div>
                <div className="tc-fin-val">
                  ${quoteResult.storeTotalListPriceCad?.toFixed(2) ?? '0.00'} CAD
                </div>
              </div>
            )}
          </div>

          <div className="tc-quote-explanation">
            {quoteResult.explanation}
          </div>

          {/* Submission Form */}
          <form className="tc-submission-box" onSubmit={handleSubmitQuote}>
            <h3 style={{ margin: 0, fontSize: 15, color: '#ffffff' }}>
              Lock in Quote &amp; Submit for Review
            </h3>
            <p style={{ margin: 0, fontSize: 13, color: 'var(--tc-text-secondary)' }}>
              Submit your quote to our team to verify card condition and finalize the transaction.
            </p>

            <div className="tc-form-grid">
              <div className="tc-form-field">
                <label className="tc-form-label">Full Name *</label>
                <input
                  type="text"
                  required
                  className="tc-form-input"
                  placeholder="e.g. Ash Ketchum"
                  value={customerName}
                  onChange={(e) => setCustomerName(e.target.value)}
                />
              </div>

              <div className="tc-form-field">
                <label className="tc-form-label">Email Address *</label>
                <input
                  type="email"
                  required
                  className="tc-form-input"
                  placeholder="e.g. ash@pallet.town"
                  value={customerEmail}
                  onChange={(e) => setCustomerEmail(e.target.value)}
                />
              </div>

              <div className="tc-form-field">
                <label className="tc-form-label">Phone Number (Optional)</label>
                <input
                  type="tel"
                  className="tc-form-input"
                  placeholder="e.g. (555) 019-2831"
                  value={customerPhone}
                  onChange={(e) => setCustomerPhone(e.target.value)}
                />
              </div>

              <div className="tc-form-field">
                <label className="tc-form-label">Notes / Instructions (Optional)</label>
                <input
                  type="text"
                  className="tc-form-input"
                  placeholder="e.g. Local drop-off in Toronto or tracked shipping"
                  value={customerNotes}
                  onChange={(e) => setCustomerNotes(e.target.value)}
                />
              </div>
            </div>

            <button
              type="submit"
              className="tc-submit-offer-btn"
              disabled={isSubmitting || !customerName.trim() || !customerEmail.trim()}
            >
              {isSubmitting ? (
                <>
                  <span className="tc-loading-spinner" /> Submitting...
                </>
              ) : (
                'Submit Trade / Sell Request'
              )}
            </button>
          </form>
        </div>
      )}

      {/* Submission Success State */}
      {submissionResult && (
        <div className="tc-success-card">
          <h2 style={{ color: 'var(--tc-status-green)', margin: '0 0 8px' }}>
            Submission Received!
          </h2>
          <p style={{ color: 'var(--tc-text-secondary)', margin: 0 }}>
            Your quote has been registered with our team. Please keep your reference code handy:
          </p>
          <div className="tc-ref-code">{submissionResult.referenceCode}</div>
          <p style={{ color: 'var(--tc-text-secondary)', fontSize: 13, maxWidth: 480, margin: '0 auto 20px' }}>
            We have emailed confirmation to <strong>{submissionResult.customerEmail}</strong>.
            Please package your cards securely in penny sleeves &amp; top-loaders before shipping or local drop-off.
          </p>

          <button
            type="button"
            className="tc-action-btn"
            style={{ padding: '10px 20px', color: '#ffffff', borderColor: 'var(--tc-border-highlight)' }}
            onClick={() => {
              setSubmissionResult(null);
              setQuoteResult(null);
              setExtractedCards([]);
              setSelectedProductIds([]);
              setMessages([
                { role: 'assistant', content: 'Would you like to sell or trade?' },
              ]);
            }}
          >
            Start Another Quote
          </button>
        </div>
      )}
    </div>
  );
}
