import type React from 'react';
import { useStickyCollapse } from '../hooks/useStickyCollapse';

/**
 * One collapsible section of a panel, remembered open or closed, saying what is behind it
 * when shut.
 *
 * The summary is the reason this is not just a disclosure triangle. A section that hides its
 * own headline when collapsed makes collapsing a choice between seeing a thing and knowing
 * about it at all, so nobody collapses anything and the panel stays as tall as its tallest
 * ship. A header that still reads "17/19 working" costs one line and loses nothing, which is
 * what makes the control usable on a hull with nineteen weapons.
 *
 * Lifted out of HangarDrawer when the SSD panel needed the same thing. The CSS class names are
 * deliberately panel-neutral; both callers share the five rules in App.css.
 */
export default function Section({
  title, colour, summary, storageKey, startOpen = false, children,
}: {
  title: string;
  colour: string;
  /** What the section still says when it is shut. Keep it to one short line. */
  summary?: string;
  /** Where the open/closed choice is remembered. Unique per section, stable across releases. */
  storageKey: string;
  startOpen?: boolean;
  children: React.ReactNode;
}) {
  const [collapsed, setCollapsed] = useStickyCollapse(storageKey, !startOpen);
  return (
    <div className="panel-section">
      <button className="panel-section-head" style={{ color: colour }}
        aria-expanded={!collapsed}
        onClick={() => setCollapsed(!collapsed)}>
        <span>{collapsed ? '▶' : '▼'} {title}</span>
        {summary && <span className="panel-section-summary">{summary}</span>}
      </button>
      {!collapsed && <div className="panel-section-body">{children}</div>}
    </div>
  );
}
