/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */

import { extent, max } from 'd3-array';
import { scaleLinear, scaleTime } from 'd3-scale';
import { area, line as d3Line } from 'd3-shape';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { RenderArea } from '../../../../sonarcloud-webapp/private/libs/feature-dashboards/src/components/visualizations/RenderArea';
import { RenderXAxis } from '../../../../sonarcloud-webapp/private/libs/feature-dashboards/src/components/visualizations/RenderXAxis';
import { RenderYAxis } from '../../../../sonarcloud-webapp/private/libs/feature-dashboards/src/components/visualizations/RenderYAxis';

interface Distribution {
  key: string | null;
  value: number | null;
}

interface IssueHistoryItem {
  date: string;
  distribution: Distribution[] | null;
}

interface ChartPoint {
  x: Date;
  y: number | null;
}

interface ChartSerie {
  color: string;
  data: ChartPoint[];
  name: string;
}

interface HostResponseHandler {
  reject: (error: unknown) => void;
  resolve: (value: unknown) => void;
  timeout: number;
}

declare global {
  interface Window {
    __ISSUE_HISTORY_INITIAL_DATA__?: IssueHistoryItem[];
  }
}

const SERIES_COLORS = [
  '#2d9cdb',
  '#27ae60',
  '#f2c94c',
  '#eb5757',
  '#9b51e0',
  '#172033',
  '#f2994a',
  '#56ccf2',
];

const initializeProtocolVersions = ['2025-11-25', '2025-06-18', '2024-11-05'];

function notify(method: string, params?: unknown) {
  window.parent.postMessage({ jsonrpc: '2.0', method, params }, '*');
}

function notifySizeChanged() {
  notify('ui/notifications/size-changed', { height: document.body.scrollHeight || 1 });
}

function issueHistoryFrom(source: unknown): IssueHistoryItem[] | null {
  if (!source || typeof source !== 'object') {
    return null;
  }

  const record = source as Record<string, unknown>;
  const result = record.result as Record<string, unknown> | undefined;
  const data = record.data as Record<string, unknown> | undefined;
  const toolResult = record.toolResult as Record<string, unknown> | undefined;
  const structured = (record.structuredContent ||
    result?.structuredContent ||
    data?.structuredContent ||
    toolResult?.structuredContent) as Record<string, unknown> | undefined;

  const issueHistory = structured?.issueHistory as Record<string, unknown> | undefined;
  if (Array.isArray(issueHistory?.issueCountHistory)) {
    return issueHistory.issueCountHistory as IssueHistoryItem[];
  }
  if (Array.isArray(structured?.issueCountHistory)) {
    return structured.issueCountHistory as IssueHistoryItem[];
  }
  if (Array.isArray(structured?.history)) {
    return structured.history.map((item) => {
      const row = item as Record<string, unknown>;
      return {
        date: String(row.date ?? ''),
        distribution: [{ key: String(row.bucket ?? 'all'), value: Number(row.count ?? 0) }],
      };
    });
  }
  return null;
}

function useMcpHostBridge(onIssueHistory: (history: IssueHistoryItem[]) => void) {
  useEffect(() => {
    let nextId = 1;
    const pending = new Map<number, HostResponseHandler>();

    function request(method: string, params: unknown, timeoutMs: number) {
      const id = nextId++;
      window.parent.postMessage({ jsonrpc: '2.0', id, method, params }, '*');
      return new Promise((resolve, reject) => {
        const timeout = window.setTimeout(() => {
          pending.delete(id);
          reject(new Error(`${method} timed out`));
        }, timeoutMs);
        pending.set(id, { reject, resolve, timeout });
      });
    }

    function initialize(index: number) {
      if (index >= initializeProtocolVersions.length) {
        console.error('ui/initialize failed for all protocol versions');
        notify('ui/notifications/initialized');
        notifySizeChanged();
        return;
      }

      request(
        'ui/initialize',
        {
          protocolVersion: initializeProtocolVersions[index],
          appInfo: {
            name: 'sonarqube_issue_history',
            title: 'SonarQube Issue History',
            version: '1.0.0',
          },
          appCapabilities: {},
        },
        2000,
      )
        .then(() => {
          notify('ui/notifications/initialized');
          notifySizeChanged();
        })
        .catch((error) => {
          console.warn('ui/initialize failed:', error);
          initialize(index + 1);
        });
    }

    function handleMessage(event: MessageEvent) {
      const message = event.data;
      if (!message || typeof message !== 'object') {
        return;
      }

      if (message.jsonrpc !== '2.0') {
        const history = issueHistoryFrom(message);
        if (history) {
          onIssueHistory(history);
        }
        return;
      }

      if (message.id !== undefined && pending.has(message.id)) {
        const response = pending.get(message.id);
        pending.delete(message.id);
        if (!response) {
          return;
        }
        window.clearTimeout(response.timeout);
        if (message.error) {
          response.reject(message.error);
        } else {
          response.resolve(message.result);
        }
        return;
      }

      const history = issueHistoryFrom(message.params) || issueHistoryFrom(message);
      if (history) {
        onIssueHistory(history);
      }
    }

    window.addEventListener('message', handleMessage);
    window.addEventListener('load', notifySizeChanged);
    initialize(0);

    return () => {
      window.removeEventListener('message', handleMessage);
      window.removeEventListener('load', notifySizeChanged);
      pending.forEach((handler) => window.clearTimeout(handler.timeout));
      pending.clear();
    };
  }, [onIssueHistory]);
}

function useElementSize<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [size, setSize] = useState({ height: 0, width: 0 });

  useEffect(() => {
    const element = ref.current;
    if (!element) {
      return undefined;
    }

    function updateSize() {
      setSize({
        height: element?.clientHeight ?? 0,
        width: element?.clientWidth ?? 0,
      });
      notifySizeChanged();
    }

    const resizeObserver =
      typeof ResizeObserver !== 'undefined' ? new ResizeObserver(updateSize) : undefined;
    resizeObserver?.observe(element);
    updateSize();
    window.addEventListener('resize', updateSize);

    return () => {
      resizeObserver?.disconnect();
      window.removeEventListener('resize', updateSize);
    };
  }, []);

  return [ref, size] as const;
}

function issueHistoryToSeries(history: IssueHistoryItem[]): ChartSerie[] {
  const byBucket = new Map<string, Map<number, number>>();
  const timestamps = new Set<number>();

  for (const item of history) {
    const x = new Date(item.date);
    if (Number.isNaN(x.getTime())) {
      continue;
    }
    const timestamp = x.getTime();
    timestamps.add(timestamp);

    const distribution =
      Array.isArray(item.distribution) && item.distribution.length > 0
        ? item.distribution
        : [{ key: 'No bucket', value: 0 }];

    for (const bucket of distribution) {
      const y = Number(bucket.value);
      if (!Number.isFinite(y)) {
        continue;
      }
      const key = String(bucket.key || 'all');
      const points = byBucket.get(key) ?? new Map<number, number>();
      points.set(timestamp, y);
      byBucket.set(key, points);
    }
  }

  const sortedTimestamps = Array.from(timestamps).sort((left, right) => left - right);

  return Array.from(byBucket.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([name, points], index) => ({
      color: SERIES_COLORS[index % SERIES_COLORS.length],
      data: sortedTimestamps.map((timestamp) => ({
        x: new Date(timestamp),
        y: points.get(timestamp) ?? null,
      })),
      name,
    }));
}

function formatDate(date: Date) {
  return date.toLocaleString(undefined, { day: 'numeric', month: 'short' });
}

function formatValue(value: number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 }).format(value);
}

function SimpleTimeline({
  height,
  highlightedIndex,
  onHighlightedIndexChange,
  series,
  width,
}: {
  height: number;
  highlightedIndex?: number;
  onHighlightedIndexChange: (index?: number) => void;
  series: ChartSerie[];
  width: number;
}) {
  const padding = { bottom: 38, left: 58, right: 20, top: 16 };
  const availableWidth = Math.max(width - padding.left - padding.right, 1);
  const availableHeight = Math.max(height - padding.top - padding.bottom, 1);
  const flatData = series.flatMap((serie) => serie.data);
  const xExtent = extent(flatData, (point) => point.x) as [Date | undefined, Date | undefined];
  const yMax = max(flatData, (point) => Number(point.y) || 0) || 1;
  const xStart = xExtent[0] ?? new Date();
  const xEnd = xExtent[1] ?? xStart;
  const adjustedXStart =
    xStart.getTime() === xEnd.getTime()
      ? new Date(xStart.getTime() - 24 * 60 * 60 * 1000)
      : xStart;
  const adjustedXEnd =
    xStart.getTime() === xEnd.getTime()
      ? new Date(xEnd.getTime() + 24 * 60 * 60 * 1000)
      : xEnd;
  const xScale = scaleTime()
    .domain([adjustedXStart, adjustedXEnd])
    .range([0, availableWidth]);
  const yScale = scaleLinear().range([availableHeight, 0]).domain([0, yMax]).nice();
  const yTicks = yScale.ticks(5);

  const lineGenerator = d3Line<ChartPoint>()
    .defined((point) => point.y !== null && point.y !== undefined)
    .x((point) => xScale(point.x))
    .y((point) => yScale(Number(point.y)));
  const areaGenerator = area<ChartPoint>()
    .defined((point) => point.y !== null && point.y !== undefined)
    .x((point) => xScale(point.x))
    .y1((point) => yScale(Number(point.y)))
    .y0(yScale(0));

  function updateHighlightedIndex(clientX: number, rect: DOMRect) {
    const [firstSerie] = series;
    if (!firstSerie) {
      return;
    }
    const relativeX = Math.max(0, Math.min(clientX - rect.left - padding.left, availableWidth));
    const hoveredDate = xScale.invert(relativeX).getTime();
    let nearestIndex = 0;
    let nearestDistance = Number.POSITIVE_INFINITY;
    firstSerie.data.forEach((point, index) => {
      const distance = Math.abs(point.x.getTime() - hoveredDate);
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearestIndex = index;
      }
    });
    onHighlightedIndexChange(nearestIndex);
  }

  const highlightedPoint = highlightedIndex === undefined ? undefined : series[0]?.data[highlightedIndex];
  const highlightedX = highlightedPoint ? xScale(highlightedPoint.x) : undefined;

  return (
    <svg
      aria-label="Issue history by bucket"
      className="issue-history-chart"
      height={height}
      onMouseLeave={() => onHighlightedIndexChange(undefined)}
      onMouseMove={(event) => updateHighlightedIndex(event.clientX, event.currentTarget.getBoundingClientRect())}
      role="img"
      width={width}
    >
      <g transform={`translate(${padding.left}, ${padding.top})`}>
        <RenderXAxis availableHeight={availableHeight} availableWidth={availableWidth} xScale={xScale} />
        <RenderYAxis
          availableHeight={availableHeight}
          formatTick={(tick) => formatValue(tick)}
          ticks={yTicks}
          yScale={yScale}
        />
        <g>
          {yTicks.map((tick) => (
            <line
              className="grid-line"
              key={`grid-${tick}`}
              x1={0}
              x2={availableWidth}
              y1={yScale(tick)}
              y2={yScale(tick)}
            />
          ))}
        </g>
        {series.map((serie) => (
          <g key={serie.name}>
            <RenderArea
              areaColor={serie.color}
              areaOpacity={0.12}
              areaPath={areaGenerator(serie.data)}
              color={serie.color}
              showArea
            />
            <path className="series-line" d={lineGenerator(serie.data) ?? undefined} stroke={serie.color} />
          </g>
        ))}
        {highlightedX !== undefined && (
          <line className="cursor-line" x1={highlightedX} x2={highlightedX} y1={0} y2={availableHeight} />
        )}
        {series.map((serie) => {
          const point = highlightedIndex === undefined ? undefined : serie.data[highlightedIndex];
          if (!point || point.y === null || point.y === undefined) {
            return null;
          }
          return (
            <circle
              className="series-dot"
              cx={xScale(point.x)}
              cy={yScale(point.y)}
              fill={serie.color}
              key={`${serie.name}-${point.x.toISOString()}`}
              r={4}
            />
          );
        })}
      </g>
    </svg>
  );
}

function Tooltip({
  highlightedIndex,
  series,
}: {
  highlightedIndex?: number;
  series: ChartSerie[];
}) {
  const highlightedPoint = highlightedIndex === undefined ? undefined : series[0]?.data[highlightedIndex];
  if (!highlightedPoint) {
    return null;
  }

  return (
    <div className="tooltip" role="tooltip">
      <strong>{formatDate(highlightedPoint.x)}</strong>
      {series.map((serie) => {
        const point = serie.data[highlightedIndex];
        if (!point || point.y === null || point.y === undefined) {
          return null;
        }
        return (
          <span className="tooltip-row" key={serie.name}>
            <span>
              <span className="tooltip-swatch" style={{ background: serie.color }} />
              {serie.name}
            </span>
            <span>{formatValue(point.y)}</span>
          </span>
        );
      })}
    </div>
  );
}

function IssueHistoryApp() {
  const [history, setHistory] = useState<IssueHistoryItem[]>(window.__ISSUE_HISTORY_INITIAL_DATA__ ?? []);
  const [highlightedIndex, setHighlightedIndex] = useState<number | undefined>();
  const [frameRef, size] = useElementSize<HTMLDivElement>();
  const series = useMemo(() => issueHistoryToSeries(history), [history]);

  useMcpHostBridge(setHistory);

  useEffect(() => {
    notifySizeChanged();
  }, [history, highlightedIndex, size.height, size.width]);

  return (
    <main>
      <h1>Issue History</h1>
      <section aria-label="Issue history by bucket" className="chart-widget">
        <div className="chart-frame" ref={frameRef}>
          {series.length === 0 ? (
            <p className="empty">No issue history returned.</p>
          ) : (
            <>
              <SimpleTimeline
                height={Math.max(size.height, 300)}
                highlightedIndex={highlightedIndex}
                onHighlightedIndexChange={setHighlightedIndex}
                series={series}
                width={Math.max(size.width, 520)}
              />
              <Tooltip highlightedIndex={highlightedIndex} series={series} />
            </>
          )}
        </div>
        {series.length > 0 && (
          <div className="legend">
            {series.map((serie) => (
              <span className="legend-item" key={serie.name}>
                <span className="legend-swatch" style={{ background: serie.color }} />
                {serie.name}
              </span>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}

createRoot(document.getElementById('issue-history-root') as HTMLElement).render(<IssueHistoryApp />);
