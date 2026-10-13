/**
 * How many storefront pages this tab has shown since the site was loaded. Lives in module
 * memory: client-side navigations keep it, a full page load (a shared link, a new tab) resets
 * it, which is exactly when "back" would leave the shop.
 */
let pagesSeen = 0;

export function recordPageView(): void {
  pagesSeen += 1;
}

/** True when the previous page in the browser history is a page of this site. */
export function hasInAppHistory(): boolean {
  return pagesSeen > 1;
}
