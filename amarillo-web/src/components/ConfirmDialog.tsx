/**
 * The one way this app asks "are you sure?".
 *
 * <h2>Why it exists</h2>
 * Three actions used the browser's own `window.confirm` — emergency deceleration, disengaging
 * into a destruction zone, and activating fire control with a weasel out. That dialog is not
 * styled by the page, cannot be themed, and announces itself as "localhost:5173 says", which is
 * how the owner found it: a ship's most committing manoeuvre asked for confirmation in a box
 * that looked like a browser error.
 *
 * <p>A fourth confirmation, for leaving or conceding, was already written by hand in the board.
 * Rather than leave the app with two ways to ask the same question — the shape behind three of
 * today's bugs — that one moves here too.
 *
 * <h2>Blocking, deliberately</h2>
 * A full-screen scrim, unlike the floating pads. These are irreversible: a ship cannot un-stop,
 * and a conceded battle cannot be unconceded. The point is to interrupt.
 */
export interface ConfirmRequest {
  title: string;
  /** What will happen, in the player's terms. Rule citations belong here, not in the title. */
  body: string;
  /** The affirmative button's label — a verb, never "OK". */
  confirmLabel: string;
  /** Red, for anything that destroys or cannot be undone. */
  danger?: boolean;
  /** Run on confirm. The dialog closes first, so this may open another. */
  run: () => void;
}

export function ConfirmDialog({ request, onClose }: {
  request: ConfirmRequest;
  onClose: () => void;
}) {
  const accent = request.danger ? '#f85149' : '#58a6ff';
  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 1200,
        background: 'rgba(0,0,0,0.72)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}
      // Clicking the scrim cancels, which is the safe answer for every one of these.
      onClick={onClose}
    >
      <div
        style={{
          width: 420, maxWidth: '90vw',
          background: '#161b22',
          border: `1px solid ${accent}`,
          borderRadius: 8,
          padding: '1rem 1.1rem',
          boxShadow: '0 8px 32px rgba(0,0,0,0.7)',
        }}
        onClick={e => e.stopPropagation()}
      >
        <div style={{ color: accent, fontWeight: 700, fontSize: '1.1rem', marginBottom: 6 }}>
          {request.title}
        </div>
        <div style={{ fontSize: '0.9rem', color: '#c9d1d9', marginBottom: 16, whiteSpace: 'pre-line' }}>
          {request.body}
        </div>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <button className="secondary" onClick={onClose}>Cancel</button>
          <button
            className="secondary"
            style={request.danger ? { borderColor: '#f85149', color: '#f85149' } : undefined}
            onClick={() => { onClose(); request.run(); }}
          >
            {request.confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
