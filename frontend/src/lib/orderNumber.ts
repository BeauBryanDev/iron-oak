/** Order numbers look like IO-20261010-ABCD1234; anything else from a URL is ignored. */
const ORDER_NUMBER = /^IO-[A-Za-z0-9-]+$/i;

export function parseOrderNumber(value: string | string[] | undefined): string | null {
  const candidate = Array.isArray(value) ? value[0] : value;
  return candidate && ORDER_NUMBER.test(candidate) ? candidate.toUpperCase() : null;
}
