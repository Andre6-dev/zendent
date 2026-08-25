/**
 * Ends the session and leaves the application by a full document load.
 *
 * A POST and not a link: a GET that ends a session is one prefetch, one
 * crawler, or one over-eager link preview away from signing out someone who
 * only pointed at it.
 *
 * The reload afterwards is the point, not a detail. It drops every piece of
 * Clinic data React is still holding, and the address it lands on is asked of
 * the server afresh — which, with the shell served `no-store`, is what stops
 * the back button putting someone back inside an application they have just
 * left. `replace` rather than `assign` so the screen they signed out of is not
 * the first thing behind them.
 */
export async function signOut(): Promise<void> {
  await fetch('/logout', { method: 'POST' })
  window.location.replace('/login')
}
