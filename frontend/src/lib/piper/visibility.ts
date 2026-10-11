/**
 * Whether the Piper chat rail is shown. The state lives on <html data-piper="closed"> and in
 * localStorage, and globals.css hides the rail and releases the page margin from it. Doing it
 * with an attribute (set by PIPER_INIT_SCRIPT before the first paint) instead of React state
 * means a visitor who closed Piper never sees it flash open on the next page load.
 */
const STORAGE_KEY = "ironoak.piper.closed";

export function setPiperOpen(open: boolean): void {
  document.documentElement.dataset.piper = open ? "open" : "closed";
  try {
    window.localStorage.setItem(STORAGE_KEY, open ? "0" : "1");
  } catch {
    // storage blocked: the choice still holds until the page reloads
  }
}

/** Inline script for <head>: restores the saved choice before the first paint. */
export const PIPER_INIT_SCRIPT = `try{if(localStorage.getItem("${STORAGE_KEY}")==="1"){document.documentElement.dataset.piper="closed"}}catch(e){}`;
