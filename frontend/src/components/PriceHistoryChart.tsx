import React, { useState, useEffect, useMemo, useRef } from 'react'
import type { PriceHistoryData, PriceRange } from '../types'
import { fetchPriceHistory } from '../api/priceApi'
import './PriceHistoryChart.css'

interface PriceHistoryChartProps {
  productId: number
  productName: string
  currentPrice: number
  cardSet?: string
  cardNumber?: string
  condition?: string
  grading?: string
  onClose?: () => void
}

export const PriceHistoryChart: React.FC<PriceHistoryChartProps> = ({
  productId,
  productName,
  currentPrice,
  cardSet,
  cardNumber,
  condition,
  grading,
}) => {
  const [selectedRange, setSelectedRange] = useState<PriceRange>('3M')
  const [data, setData] = useState<PriceHistoryData | null>(null)
  const [loading, setLoading] = useState<boolean>(true)
  const [hoverIndex, setHoverIndex] = useState<number | null>(null)
  const svgRef = useRef<SVGSVGElement | null>(null)

  useEffect(() => {
    let active = true
    setLoading(true)

    fetchPriceHistory(productId, selectedRange, {
      name: productName,
      price: currentPrice,
      set: cardSet,
      cardNumber,
      condition,
      grading,
    })
      .then((res) => {
        if (active) {
          setData(res)
          setLoading(false)
          setHoverIndex(null)
        }
      })
      .catch(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [productId, selectedRange, productName, currentPrice, cardSet, cardNumber, condition, grading])

  // Coordinate calculations for SVG Chart
  const chartLayout = useMemo(() => {
    if (!data || data.history.length === 0) return null

    const width = 680
    const height = 240
    const padTop = 24
    const padBottom = 34
    const padLeft = 58
    const padRight = 24

    const plotW = width - padLeft - padRight
    const plotH = height - padTop - padBottom

    const prices = data.history.map((pt) => pt.price)
    const minVal = Math.min(...prices)
    const maxVal = Math.max(...prices)
    const spread = maxVal - minVal || 1.0

    // Add 8% vertical headroom and footroom
    const yMin = Math.max(0, minVal - spread * 0.08)
    const yMax = maxVal + spread * 0.08
    const yRange = yMax - yMin

    const points = data.history.map((pt, i) => {
      const x = padLeft + (i / (data.history.length - 1)) * plotW
      const y = padTop + plotH - ((pt.price - yMin) / yRange) * plotH
      return { x, y, pt, index: i }
    })

    // Construct line path and filled area polygon
    const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' ')
    const areaPath = `${linePath} L ${points[points.length - 1].x.toFixed(1)} ${(padTop + plotH).toFixed(1)} L ${points[0].x.toFixed(1)} ${(padTop + plotH).toFixed(1)} Z`

    // Grid ticks (3 horizontal levels)
    const gridTicks = [
      { price: yMax, y: padTop },
      { price: (yMin + yMax) / 2, y: padTop + plotH / 2 },
      { price: yMin, y: padTop + plotH },
    ]

    return {
      width,
      height,
      padTop,
      padBottom,
      padLeft,
      padRight,
      plotW,
      plotH,
      points,
      linePath,
      areaPath,
      gridTicks,
      yMin,
      yMax,
    }
  }, [data])

  // Handle pointer tracking for crosshair tooltip
  const handleMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (!chartLayout || chartLayout.points.length === 0 || !svgRef.current) return
    const rect = svgRef.current.getBoundingClientRect()
    const mouseX = e.clientX - rect.left
    const svgX = (mouseX / rect.width) * chartLayout.width

    // Find nearest point
    let closestIdx = 0
    let minDiff = Infinity
    chartLayout.points.forEach((p, idx) => {
      const diff = Math.abs(p.x - svgX)
      if (diff < minDiff) {
        minDiff = diff
        closestIdx = idx
      }
    })

    setHoverIndex(closestIdx)
  }

  const handleMouseLeave = () => {
    setHoverIndex(null)
  }

  const activePoint =
    chartLayout && hoverIndex !== null && chartLayout.points[hoverIndex]
      ? chartLayout.points[hoverIndex]
      : chartLayout && chartLayout.points.length > 0
      ? chartLayout.points[chartLayout.points.length - 1]
      : null

  const isPositive = (data?.changePercentage ?? 0) >= 0

  return (
    <div className="tc-price-tracker">
      {/* Tracker Header */}
      <div className="tc-tracker-header">
        <div className="tc-tracker-title-box">
          <div className="tc-tracker-eyebrow tc-mono">
            <span>HISTORICAL VALUATION ENGINE</span>
            <span className="tc-live-dot" />
            <span className="tc-live-text">REAL-TIME INDEX</span>
          </div>
          <h3 className="tc-tracker-card-name">{productName}</h3>
          {(cardSet || cardNumber) && (
            <div className="tc-tracker-card-meta tc-mono">
              {cardSet && <span className="tc-meta-chip">{cardSet}</span>}
              {cardNumber && <span className="tc-meta-chip">#{cardNumber}</span>}
              {condition && <span className="tc-meta-chip">{condition}</span>}
              {grading && <span className="tc-meta-chip gold">{grading}</span>}
            </div>
          )}
        </div>

        {/* Range Selector */}
        <div className="tc-range-selector">
          {(['1M', '3M', '1Y'] as PriceRange[]).map((range) => (
            <button
              key={range}
              type="button"
              className={`tc-range-btn tc-mono ${selectedRange === range ? 'active' : ''}`}
              onClick={() => setSelectedRange(range)}
              disabled={loading}
            >
              {range === '1M' ? '1 Month' : range === '3M' ? '3 Months' : '1 Year'}
            </button>
          ))}
        </div>
      </div>

      {/* Metric Ribbon */}
      <div className="tc-metric-ribbon">
        <div className="tc-metric-cell">
          <span className="tc-metric-label tc-mono">
            {hoverIndex !== null ? 'POINT VALUATION' : 'CURRENT VAULT PRICE'}
          </span>
          <span className="tc-metric-val tc-pixel">
            ${activePoint ? activePoint.pt.price.toFixed(2) : currentPrice.toFixed(2)}
            <span className="tc-cur-label">CAD</span>
          </span>
        </div>

        <div className="tc-metric-cell">
          <span className="tc-metric-label tc-mono">TIMEFRAME RETURN</span>
          <div className={`tc-metric-change tc-mono ${isPositive ? 'positive' : 'negative'}`}>
            <span className="tc-change-arrow">{isPositive ? '▲' : '▼'}</span>
            <span>
              {isPositive ? '+' : ''}${data ? Math.abs(data.changeAmount).toFixed(2) : '0.00'} (
              {data ? (data.changePercentage >= 0 ? '+' : '') + data.changePercentage.toFixed(2) : '0.00'}%)
            </span>
          </div>
        </div>

        <div className="tc-metric-cell">
          <span className="tc-metric-label tc-mono">PERIOD LOW</span>
          <span className="tc-metric-subval tc-mono">
            ${data ? data.periodLow.toFixed(2) : '0.00'}
          </span>
        </div>

        <div className="tc-metric-cell">
          <span className="tc-metric-label tc-mono">PERIOD HIGH</span>
          <span className="tc-metric-subval tc-mono">
            ${data ? data.periodHigh.toFixed(2) : '0.00'}
          </span>
        </div>

        <div className="tc-metric-cell">
          <span className="tc-metric-label tc-mono">RECORDED DATE</span>
          <span className="tc-metric-subval tc-mono">
            {activePoint ? activePoint.pt.date : '—'}
          </span>
        </div>
      </div>

      {/* Interactive SVG Chart Stage */}
      <div className="tc-chart-stage">
        {loading && (
          <div className="tc-chart-loading tc-mono">
            <span className="tc-spinner" />
            <span>CALCULATING MARKET TIME SERIES...</span>
          </div>
        )}

        {chartLayout && (
          <svg
            ref={svgRef}
            className="tc-chart-svg"
            viewBox={`0 0 ${chartLayout.width} ${chartLayout.height}`}
            preserveAspectRatio="none"
            onMouseMove={handleMouseMove}
            onMouseLeave={handleMouseLeave}
          >
            <defs>
              <linearGradient id="tc-gradient-pos" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#4ade80" stopOpacity="0.28" />
                <stop offset="100%" stopColor="#4ade80" stopOpacity="0.0" />
              </linearGradient>
              <linearGradient id="tc-gradient-neg" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#f87171" stopOpacity="0.28" />
                <stop offset="100%" stopColor="#f87171" stopOpacity="0.0" />
              </linearGradient>
            </defs>

            {/* Horizontal Gridlines */}
            {chartLayout.gridTicks.map((tick, idx) => (
              <g key={idx}>
                <line
                  x1={chartLayout.padLeft}
                  y1={tick.y}
                  x2={chartLayout.width - chartLayout.padRight}
                  y2={tick.y}
                  stroke="#28282e"
                  strokeDasharray="4 4"
                  strokeWidth="1"
                />
                <text
                  x={chartLayout.padLeft - 8}
                  y={tick.y + 4}
                  fill="#757580"
                  fontSize="11"
                  textAnchor="end"
                  fontFamily="monospace"
                >
                  ${tick.price.toFixed(0)}
                </text>
              </g>
            ))}

            {/* Area Fill */}
            <path
              d={chartLayout.areaPath}
              fill={isPositive ? 'url(#tc-gradient-pos)' : 'url(#tc-gradient-neg)'}
            />

            {/* Trend Line */}
            <path
              d={chartLayout.linePath}
              fill="none"
              stroke={isPositive ? '#4ade80' : '#f87171'}
              strokeWidth="2.2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />

            {/* Crosshair on hover */}
            {activePoint && (
              <g>
                <line
                  x1={activePoint.x}
                  y1={chartLayout.padTop}
                  x2={activePoint.x}
                  y2={chartLayout.padTop + chartLayout.plotH}
                  stroke="#d4af37"
                  strokeDasharray="3 3"
                  strokeWidth="1.2"
                  opacity="0.8"
                />
                <circle
                  cx={activePoint.x}
                  cy={activePoint.y}
                  r="5"
                  fill="#d4af37"
                  stroke="#0f0f11"
                  strokeWidth="2.5"
                />
              </g>
            )}

            {/* Date axis labels */}
            {chartLayout.points.length > 0 && (
              <>
                <text
                  x={chartLayout.points[0].x}
                  y={chartLayout.height - 10}
                  fill="#757580"
                  fontSize="10"
                  fontFamily="monospace"
                  textAnchor="start"
                >
                  {chartLayout.points[0].pt.date}
                </text>
                <text
                  x={chartLayout.points[Math.floor(chartLayout.points.length / 2)].x}
                  y={chartLayout.height - 10}
                  fill="#757580"
                  fontSize="10"
                  fontFamily="monospace"
                  textAnchor="middle"
                >
                  {chartLayout.points[Math.floor(chartLayout.points.length / 2)].pt.date}
                </text>
                <text
                  x={chartLayout.points[chartLayout.points.length - 1].x}
                  y={chartLayout.height - 10}
                  fill="#757580"
                  fontSize="10"
                  fontFamily="monospace"
                  textAnchor="end"
                >
                  {chartLayout.points[chartLayout.points.length - 1].pt.date}
                </text>
              </>
            )}
          </svg>
        )}

        {/* Floating Tooltip Pill */}
        {activePoint && hoverIndex !== null && (
          <div
            className="tc-chart-tooltip"
            style={{
              left: `${(activePoint.x / (chartLayout?.width || 1)) * 100}%`,
              top: `${(activePoint.y / (chartLayout?.height || 1)) * 100}%`,
            }}
          >
            <div className="tc-tooltip-date tc-mono">{activePoint.pt.date}</div>
            <div className="tc-tooltip-price tc-pixel">${activePoint.pt.price.toFixed(2)} CAD</div>
            <div className="tc-tooltip-vol tc-mono">{activePoint.pt.volume} verified trades</div>
          </div>
        )}
      </div>

      {/* Market Quality & Ledger Footer */}
      <div className="tc-tracker-footer">
        <div className="tc-footer-item">
          <span className="tc-footer-label tc-mono">MARKET LIQUIDITY:</span>
          <span className="tc-footer-val tc-mono">HIGH TIER (ACTIVE ORDER BOOK)</span>
        </div>
        <div className="tc-footer-item">
          <span className="tc-footer-label tc-mono">LEDGER ASSURANCE:</span>
          <span className="tc-footer-val tc-mono">100% VERIFIED AUTHENTIC</span>
        </div>
        <div className="tc-footer-item">
          <span className="tc-footer-label tc-mono">PRICING ENGINE:</span>
          <span className="tc-footer-val tc-mono">DETERMINISTIC COMPOSITE</span>
        </div>
      </div>
    </div>
  )
}
